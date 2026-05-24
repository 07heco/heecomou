package com.heecomou.desktop.audio

import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class AudioCaptureManager(
    val sampleRate: Float = 16000f,
    val sampleSizeInBits: Int = 16,
    val channels: Int = 1
) {
    companion object {
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val FRAME_SIZE_BYTES = 2
    }

    private var targetDataLine: TargetDataLine? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    var onAudioData: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    val bufferSize: Int
        get() {
            val minFrames = (sampleRate * 0.04).toInt()
            return minFrames * FRAME_SIZE_BYTES * BUFFER_SIZE_MULTIPLIER
        }

    val audioFormat: AudioFormat
        get() = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            sampleSizeInBits,
            channels,
            FRAME_SIZE_BYTES,
            sampleRate,
            false
        )

    fun isSupported(): Boolean {
        return try {
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            AudioSystem.isLineSupported(info)
        } catch (e: Exception) {
            false
        }
    }

    fun startRecording(): Boolean {
        if (isRecording.get()) {
            onError?.invoke("Recording is already in progress")
            return false
        }

        if (!isSupported()) {
            onError?.invoke("Audio format not supported: $sampleRate Hz, $sampleSizeInBits-bit, $channels channel(s)")
            return false
        }

        try {
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            val line = AudioSystem.getLine(info) as TargetDataLine

            line.open(audioFormat, bufferSize)
            line.start()

            targetDataLine = line
            isRecording.set(true)
            onStateChanged?.invoke(true)

            val buffer = ByteArray(bufferSize / 2)

            recordingThread = Thread {
                try {
                    while (isRecording.get()) {
                        val bytesRead = line.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            val chunk = buffer.copyOf(bytesRead)
                            onAudioData?.invoke(chunk)
                        }
                    }
                } catch (e: Exception) {
                    if (isRecording.get()) {
                        onError?.invoke("Recording error: ${e.message}")
                    }
                }
            }

            recordingThread?.name = "DesktopAudioCapture-Thread"
            recordingThread?.isDaemon = true
            recordingThread?.start()
            return true
        } catch (e: SecurityException) {
            onError?.invoke("Microphone access denied")
            return false
        } catch (e: IllegalArgumentException) {
            onError?.invoke("Invalid audio parameters: ${e.message}")
            return false
        } catch (e: Exception) {
            onError?.invoke("Failed to start recording: ${e.message}")
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) return

        isRecording.set(false)
        recordingThread?.let { thread ->
            try {
                thread.join(500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        recordingThread = null

        try {
            targetDataLine?.let {
                if (it.isRunning) {
                    it.stop()
                }
                it.flush()
                it.close()
            }
        } catch (e: Exception) {
            onError?.invoke("Error releasing audio line: ${e.message}")
        }
        targetDataLine = null

        onStateChanged?.invoke(false)
    }

    fun release() {
        stopRecording()
        onAudioData = null
        onError = null
        onStateChanged = null
    }

    fun isRecording(): Boolean = isRecording.get()
}
