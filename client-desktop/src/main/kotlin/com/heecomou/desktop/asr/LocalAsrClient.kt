package com.heecomou.desktop.asr

import com.heecomou.desktop.network.LocalAsrApiService
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

enum class LocalAsrState {
    IDLE,
    INITIALIZING,
    READY,
    RECOGNIZING,
    ERROR
}

class LocalAsrClient {

    companion object {
        private val LOGGER = Logger.getLogger(LocalAsrClient::class.java.name)
        private const val SAMPLE_RATE = 16000
        private const val LOCAL_SERVER_URL = "http://127.0.0.1:8085"
        private const val SERVER_STARTUP_TIMEOUT_MS = 30_000L
    }

    private var state = LocalAsrState.IDLE
    private val isRunning = AtomicBoolean(false)
    private var audioBuffer = mutableListOf<ByteArray>()
    private var accumulatedSamples = 0
    private val stateLock = Any()

    @Volatile var isModelLoaded: Boolean = false
        private set

    private var serverProcess: Process? = null
    private val apiService = LocalAsrApiService(LOCAL_SERVER_URL)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onStateChanged: ((LocalAsrState) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onSpeechDetected: (() -> Unit)? = null
    var onSilenceDetected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun initialize(): Boolean {
        return try {
            setState(LocalAsrState.INITIALIZING)

            val pythonExe = findPython()
            if (pythonExe == null) {
                LOGGER.warning("Python not found, local ASR unavailable")
                setState(LocalAsrState.ERROR)
                onError?.invoke("未找到 Python 运行环境，端侧识别不可用")
                isModelLoaded = false
                return false
            }

            val serverScript = findServerScript()
            if (serverScript == null) {
                LOGGER.warning("local_onnx_server.py not found")
                setState(LocalAsrState.ERROR)
                onError?.invoke("未找到端侧推理脚本")
                isModelLoaded = false
                return false
            }

            val modelDir = findModelDir()
            if (modelDir == null) {
                LOGGER.warning("ONNX model directory not found")
                setState(LocalAsrState.ERROR)
                onError?.invoke("未找到 ONNX 模型文件，请先运行 export_onnx.py 导出")
                isModelLoaded = false
                return false
            }

            LOGGER.info("Starting local ONNX server: $pythonExe $serverScript (models=$modelDir)")
            val processBuilder = ProcessBuilder(
                pythonExe, serverScript
            )
            processBuilder.environment()["ONNX_MODEL_DIR"] = modelDir
            processBuilder.environment()["ONNX_PORT"] = "8085"
            processBuilder.environment()["ONNX_HOST"] = "127.0.0.1"
            processBuilder.redirectErrorStream(true)

            serverProcess = processBuilder.start()

            val ready = waitForServerReady(SERVER_STARTUP_TIMEOUT_MS)
            if (!ready) {
                LOGGER.severe("Local ONNX server failed to start within timeout")
                serverProcess?.destroyForcibly()
                serverProcess = null
                setState(LocalAsrState.ERROR)
                onError?.invoke("端侧推理服务启动超时")
                isModelLoaded = false
                return false
            }

            isModelLoaded = true
            setState(LocalAsrState.READY)
            LOGGER.info("Local ONNX ASR ready")
            true
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Failed to initialize local ASR: ${e.message}", e)
            setState(LocalAsrState.ERROR)
            onError?.invoke("端侧初始化失败: ${e.message}")
            false
        }
    }

    fun startListening(): Boolean {
        synchronized(stateLock) {
            if (state != LocalAsrState.READY && state != LocalAsrState.IDLE) {
                onError?.invoke("Cannot start listening in state: $state")
                return false
            }

            isRunning.set(true)
            audioBuffer.clear()
            accumulatedSamples = 0
            setState(LocalAsrState.RECOGNIZING)
            return true
        }
    }

    fun feedPcmData(pcmData: ByteArray, isSpeech: Boolean, timestampMs: Long) {
        if (!isRunning.get()) return

        accumulatedSamples += pcmData.size / 2
        audioBuffer.add(pcmData)

        val totalSeconds = accumulatedSamples.toFloat() / SAMPLE_RATE
        val partialText = "端侧识别中... (${"%.1f".format(totalSeconds)}s)"
        onPartialResult?.invoke(partialText)
    }

    fun stopListening() {
        isRunning.set(false)

        if (audioBuffer.isNotEmpty()) {
            produceFinalResult()
        }

        synchronized(stateLock) {
            if (state == LocalAsrState.RECOGNIZING) {
                setState(LocalAsrState.READY)
            }
        }

        audioBuffer.clear()
        accumulatedSamples = 0
    }

    fun release() {
        isRunning.set(false)
        audioBuffer.clear()
        scope.cancel()

        serverProcess?.let { proc ->
            LOGGER.info("Shutting down local ONNX server...")
            proc.destroy()
            try {
                proc.waitFor()
            } catch (_: InterruptedException) {
                proc.destroyForcibly()
            }
            serverProcess = null
        }

        synchronized(stateLock) {
            setState(LocalAsrState.IDLE)
        }
        isModelLoaded = false
        onStateChanged = null
        onPartialResult = null
        onFinalResult = null
        onSpeechDetected = null
        onSilenceDetected = null
        onError = null
    }

    val currentState: LocalAsrState get() = state

    private fun produceFinalResult() {
        scope.launch {
            try {
                val wavBytes = pcmToWav(audioBuffer)
                val audioB64 = Base64.getEncoder().encodeToString(wavBytes)

                LOGGER.info("Sending ${wavBytes.size} bytes WAV to local ONNX server...")
                val result = apiService.recognize(audioB64, "zh")

                if (result != null && result.text.isNotBlank()) {
                    onFinalResult?.invoke(result.text)
                    LOGGER.info("Local ONNX result: ${result.text.take(50)}")
                } else {
                    onFinalResult?.invoke("端侧识别无结果")
                }
            } catch (e: Exception) {
                LOGGER.log(Level.WARNING, "Local ONNX recognition failed: ${e.message}", e)
                onError?.invoke("端侧识别失败: ${e.message}")
            }
        }
    }

    private fun pcmToWav(chunks: List<ByteArray>): ByteArray {
        val pcmBytes = ByteArray(chunks.sumOf { it.size })
        var pos = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, pcmBytes, pos, chunk.size)
            pos += chunk.size
        }

        val totalDataLen = pcmBytes.size
        val totalFileLen = totalDataLen + 44
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2 // 16-bit = 2 bytes

        val out = ByteArrayOutputStream(totalFileLen)
        out.write("RIFF".toByteArray())
        out.write(intToBytesLittleEndian(totalFileLen - 8))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytesLittleEndian(16))
        out.write(shortToBytesLittleEndian(1))
        out.write(shortToBytesLittleEndian(channels))
        out.write(intToBytesLittleEndian(SAMPLE_RATE))
        out.write(intToBytesLittleEndian(byteRate))
        out.write(shortToBytesLittleEndian(channels * 2))
        out.write(shortToBytesLittleEndian(16))
        out.write("data".toByteArray())
        out.write(intToBytesLittleEndian(totalDataLen))
        out.write(pcmBytes)

        return out.toByteArray()
    }

    private fun intToBytesLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToBytesLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte()
        )
    }

    private fun waitForServerReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            try {
                attempt++
                val healthy = apiService.healthCheck()
                if (healthy) {
                    LOGGER.info("Local ONNX server ready after ${attempt} attempt(s)")
                    return true
                }
            } catch (_: Exception) {
                // server not ready yet
            }
            Thread.sleep(1000)
        }
        return false
    }

    private fun findPython(): String? {
        for (candidate in listOf("python3", "python")) {
            try {
                val proc = ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (proc.exitValue() == 0 && output.contains("Python")) {
                    LOGGER.info("Found Python: $candidate ($output.trim())")
                    return candidate
                }
            } catch (_: Exception) {
                // not found, try next
            }
        }
        return null
    }

    private fun findServerScript(): String? {
        val candidates = listOf(
            "asr-python/src/local_onnx_server.py",
            "../asr-python/src/local_onnx_server.py",
        )
        for (c in candidates) {
            val f = java.io.File(c)
            if (f.exists()) {
                LOGGER.info("Found server script: ${f.absolutePath}")
                return f.absolutePath
            }
        }
        return null
    }

    private fun findModelDir(): String? {
        val candidates = listOf(
            "asr-python/onnx_models",
            "../asr-python/onnx_models",
        )
        for (c in candidates) {
            val d = java.io.File(c)
            val encoderExists = java.io.File(d, "encoder.onnx").exists()
            val encoderInt8Exists = java.io.File(d, "encoder_int8.onnx").exists()
            if (d.isDirectory && (encoderExists || encoderInt8Exists)) {
                LOGGER.info("Found ONNX model dir: ${d.absolutePath}")
                return d.absolutePath
            }
        }
        return null
    }

    private fun setState(newState: LocalAsrState) {
        synchronized(stateLock) {
            if (state != newState) {
                state = newState
                onStateChanged?.invoke(newState)
            }
        }
    }
}
