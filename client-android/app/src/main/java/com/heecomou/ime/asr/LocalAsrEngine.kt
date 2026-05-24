package com.heecomou.ime.asr

import android.content.Context
import android.content.res.AssetManager
import ai.onnxruntime.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.*
import kotlin.collections.HashMap

class LocalAsrEngine(private val context: Context) {

    companion object {
        private const val ENCODER_MODEL = "models/encoder_int8.onnx"
        private const val DECODER_MODEL = "models/decoder_int8.onnx"
        private const val TOKENIZER_FILE = "models/tokenizer.json"
        private const val SAMPLE_RATE = 16000
        private const val N_MELS = 80
        private const val N_FFT = 512
        private const val HOP_LENGTH = 160
        private const val MAX_SOURCE_POSITIONS = 3000
    }

    enum class State {
        UNINITIALIZED,
        LOADING,
        READY,
        ERROR
    }

    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var tokenizerVocab: Map<String, Int> = emptyMap()
    private var idToToken: Map<Int, String> = emptyMap()
    private var state: State = State.UNINITIALIZED
    private var stateListener: ((State) -> Unit)? = null

    val currentState: State get() = state

    fun setStateListener(listener: (State) -> Unit) {
        stateListener = listener
    }

    private fun updateState(newState: State) {
        state = newState
        stateListener?.invoke(newState)
    }

    fun init() {
        if (state == State.READY || state == State.LOADING) return

        updateState(State.LOADING)

        try {
            ortEnv = OrtEnvironment.getEnvironment()

            encoderSession = loadSession(ENCODER_MODEL)
            decoderSession = loadSession(DECODER_MODEL)

            loadTokenizer()

            updateState(State.READY)
        } catch (e: Exception) {
            updateState(State.ERROR)
            throw RuntimeException("Failed to initialize LocalAsrEngine: ${e.message}", e)
        }
    }

    private fun loadSession(modelPath: String): OrtSession {
        val env = ortEnv ?: throw IllegalStateException("ORT environment not initialized")
        val assetManager = context.assets

        return try {
            val modelBytes = readAssetBytes(assetManager, modelPath)
            env.createSession(modelBytes)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load ONNX model $modelPath: ${e.message}", e)
        }
    }

    private fun readAssetBytes(assetManager: AssetManager, path: String): ByteArray {
        assetManager.open(path).use { input ->
            return input.readBytes()
        }
    }

    private fun assetExists(assetManager: AssetManager, path: String): Boolean {
        return try {
            assetManager.open(path).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadTokenizer() {
        val assetManager = context.assets
        val vocab = LinkedHashMap<String, Int>()
        val idMap = LinkedHashMap<Int, String>()

        try {
            val jsonStr = assetManager.open(TOKENIZER_FILE).use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }

            val root = JSONObject(JSONTokener(jsonStr))

            if (root.has("model") && root.getJSONObject("model").has("vocab")) {
                val vocabObj = root.getJSONObject("model").getJSONObject("vocab")
                val keys = vocabObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = vocabObj.getInt(key)
                    vocab[key] = value
                    idMap[value] = key
                }
            } else if (root.has("added_tokens") && root.has("model")) {
                loadHuggingFaceTokenizer(root, vocab, idMap)
            }
        } catch (e: Exception) {
            if (assetExists(assetManager, TOKENIZER_FILE.replace(".json", "_vocab.txt"))) {
                loadSimpleVocab(assetManager, vocab, idMap)
            } else {
                throw RuntimeException("Failed to load tokenizer: ${e.message}", e)
            }
        }

        tokenizerVocab = vocab
        idToToken = idMap
    }

    private fun loadHuggingFaceTokenizer(
        root: JSONObject,
        vocab: MutableMap<String, Int>,
        idMap: MutableMap<Int, String>
    ) {
        val model = root.getJSONObject("model")
        val vocabObj = model.getJSONObject("vocab")
        val keys = vocabObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = vocabObj.getInt(key)
            vocab[key] = value
            idMap[value] = key
        }
    }

    private fun loadSimpleVocab(
        assetManager: AssetManager,
        vocab: MutableMap<String, Int>,
        idMap: MutableMap<Int, String>
    ) {
        val vocabPath = TOKENIZER_FILE.replace(".json", "_vocab.txt")
        assetManager.open(vocabPath).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                var line: String?
                var index = 0
                while (reader.readLine().also { line = it } != null) {
                    val token = line!!.trim()
                    if (token.isNotEmpty()) {
                        vocab[token] = index
                        idMap[index] = token
                        index++
                    }
                }
            }
        }
    }

    fun recognize(pcmSamples: FloatArray, language: String = "zh"): LocalAsrResult {
        if (state != State.READY) {
            throw IllegalStateException("Engine not ready. Current state: $state")
        }

        val ortEnv = ortEnv ?: throw IllegalStateException("ORT environment not initialized")
        val encoderSession = encoderSession ?: throw IllegalStateException("Encoder not loaded")
        val decoderSession = decoderSession ?: throw IllegalStateException("Decoder not loaded")

        val melFeatures = extractLogMel(pcmSamples)

        val encoderOutput = runEncoder(ortEnv, encoderSession, melFeatures)

        val text = runDecoder(ortEnv, decoderSession, encoderOutput, language)

        return LocalAsrResult(
            text = text,
            durationMs = pcmSamples.size.toFloat() / SAMPLE_RATE * 1000f
        )
    }

    fun extractLogMel(pcmSamples: FloatArray): FloatArray {
        val fftSize = N_FFT
        val hopLength = HOP_LENGTH
        val nMels = N_MELS
        val sampleRate = SAMPLE_RATE

        val numFrames = 1 + (pcmSamples.size - fftSize) / hopLength
        if (numFrames <= 0) {
            throw IllegalArgumentException("Audio too short: ${pcmSamples.size} samples")
        }

        val hannWindow = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
        }

        val fftReal = FloatArray(fftSize)
        val fftImag = FloatArray(fftSize)
        val powerSpec = FloatArray(fftSize / 2 + 1)
        val melEnergies = FloatArray(nMels)
        val allMelFrames = FloatArray(numFrames * nMels)

        val melFilterbank = createMelFilterbank(nMels, fftSize, sampleRate)

        for (frameIdx in 0 until numFrames) {
            val start = frameIdx * hopLength

            for (i in 0 until fftSize) {
                val sample = if (start + i < pcmSamples.size) pcmSamples[start + i] else 0f
                fftReal[i] = sample * hannWindow[i]
                fftImag[i] = 0f
            }

            computeFFT(fftReal, fftImag, fftSize)

            for (k in 0 until fftSize / 2 + 1) {
                powerSpec[k] = (fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k]) / fftSize.toFloat()
            }

            for (m in 0 until nMels) {
                var energy = 0f
                val filter = melFilterbank[m]
                for (k in filter.indices) {
                    energy += filter[k] * powerSpec[k]
                }
                melEnergies[m] = Math.log10(Math.max(energy, 1e-10f).toDouble()).toFloat()
            }

            val offset = frameIdx * nMels
            System.arraycopy(melEnergies, 0, allMelFrames, offset, nMels)
        }

        return allMelFrames
    }

    internal fun createMelFilterbank(
        nMels: Int, fftSize: Int, sampleRate: Int
    ): Array<FloatArray> {
        val nFreqs = fftSize / 2 + 1
        val lowMel = hzToMel(0f)
        val highMel = hzToMel(sampleRate / 2f)
        val melPoints = FloatArray(nMels + 2)

        for (i in melPoints.indices) {
            melPoints[i] = lowMel + (highMel - lowMel) * i / (nMels + 1)
        }

        val freqPoints = FloatArray(nMels + 2) { melToHz(melPoints[it]) }
        val binPoints = IntArray(nMels + 2) {
            Math.round(freqPoints[it] * (fftSize - 1) / sampleRate)
        }

        val filters = Array(nMels) { FloatArray(nFreqs) }

        for (m in 1..nMels) {
            val start = binPoints[m - 1]
            val center = binPoints[m]
            val end = binPoints[m + 1]

            val leftRange = center - start
            if (leftRange > 0) {
                for (k in start until center) {
                    if (k < nFreqs) {
                        filters[m - 1][k] = (k - start).toFloat() / leftRange.toFloat()
                    }
                }
            }
            val rightRange = end - center
            if (rightRange > 0) {
                for (k in center until end) {
                    if (k < nFreqs) {
                        filters[m - 1][k] = (end - k).toFloat() / rightRange.toFloat()
                    }
                }
            }
        }

        return filters
    }

    internal fun hzToMel(hz: Float): Float {
        return (2595.0 * Math.log10(1.0 + hz / 700.0)).toFloat()
    }

    internal fun melToHz(mel: Float): Float {
        return (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)).toFloat()
    }

    internal fun computeFFT(real: FloatArray, imag: FloatArray, n: Int) {
        var i = 1
        var j = 0
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            i++
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wReal = Math.cos(angle).toFloat()
            val wImag = Math.sin(angle).toFloat()
            var k = 0
            while (k < n) {
                var wkr = 1f
                var wki = 0f
                for (h in 0 until len / 2) {
                    val idx1 = k + h
                    val idx2 = k + h + len / 2
                    val tr = wkr * real[idx2] - wki * imag[idx2]
                    val ti = wkr * imag[idx2] + wki * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] = real[idx1] + tr
                    imag[idx1] = imag[idx1] + ti
                    val newWr = wkr * wReal - wki * wImag
                    val newWi = wkr * wImag + wki * wReal
                    wkr = newWr
                    wki = newWi
                }
                k += len
            }
            len = len shl 1
        }
    }

    private fun runEncoder(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        melFeatures: FloatArray
    ): FloatArray {
        val numFrames = melFeatures.size / N_MELS
        val paddedFrames = minOf(numFrames, MAX_SOURCE_POSITIONS)

        val inputShape = longArrayOf(1, N_MELS.toLong(), paddedFrames.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(melFeatures, 0, (N_MELS * paddedFrames).toInt()),
            inputShape
        )

        val inputs = mapOf("input_features" to inputTensor)

        val results = session.run(inputs)

        val outputObj = results.first().value
        val rawArray = outputObj as Array<*>
        val hiddenStates: Array<FloatArray>
        val firstElem = rawArray[0]
        if (firstElem is FloatArray) {
            @Suppress("UNCHECKED_CAST")
            hiddenStates = rawArray as Array<FloatArray>
        } else if (firstElem is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            hiddenStates = rawArray as Array<FloatArray>
        } else {
            throw RuntimeException("Unexpected ONNX output type: ${firstElem?.javaClass}")
        }
        val seqLen = hiddenStates.size

        inputTensor.close()
        results.close()

        val hiddenDim = hiddenStates[0].size
        val flat = FloatArray(seqLen * hiddenDim)
        for (i in 0 until seqLen) {
            System.arraycopy(hiddenStates[i], 0, flat, i * hiddenDim, hiddenDim)
        }

        return flat
    }

    private fun runDecoder(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        encoderOutput: FloatArray,
        language: String
    ): String {
        val langTokenId = getLanguageTokenId(language)
        val eotTokenId = getEotTokenId()
        val maxTokens = 256

        val encoderShape = longArrayOf(1, encoderOutput.size.toLong())
        val encoderTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(encoderOutput),
            encoderShape
        )

        var currentTokenIds = longArrayOf(langTokenId.toLong())
        val outputTokens = mutableListOf<Long>()

        var iteration = 0
        while (iteration < maxTokens) {
            val inputIdsShape = longArrayOf(1, currentTokenIds.size.toLong())
            val inputIdsTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(currentTokenIds),
                inputIdsShape
            )

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "encoder_hidden_states" to encoderTensor
            )

            val results = session.run(inputs)
            val logitsOutput = results.first().value
            val rawLogits = logitsOutput as Array<*>
            @Suppress("UNCHECKED_CAST")
            val logits = rawLogits as Array<FloatArray>
            val lastLogits = logits[logits.size - 1]

            inputIdsTensor.close()
            results.close()

            var maxIdx = 0
            var maxVal = lastLogits[0]
            for (i in 1 until lastLogits.size) {
                if (lastLogits[i] > maxVal) {
                    maxVal = lastLogits[i]
                    maxIdx = i
                }
            }

            if (maxIdx == eotTokenId) break

            outputTokens.add(maxIdx.toLong())
            currentTokenIds = longArrayOf(langTokenId.toLong()) + outputTokens.toLongArray()
            iteration++
        }

        encoderTensor.close()

        return decodeTokens(outputTokens)
    }

    private fun getLanguageTokenId(language: String): Int {
        val langToken = "<|$language|>"
        return tokenizerVocab[langToken]
            ?: tokenizerVocab["<|zh|>"]
            ?: tokenizerVocab.values.firstOrNull()
            ?: 0
    }

    private fun getEotTokenId(): Int {
        return tokenizerVocab["<|endoftext|>"]
            ?: tokenizerVocab["<|eot_id|>"]
            ?: tokenizerVocab.values.maxOrNull()
            ?: 50256
    }

    fun decodeTokens(tokenIds: List<Long>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            val token = idToToken[id.toInt()] ?: continue
            if (token.startsWith("<|") || token.startsWith("<s>") || token.startsWith("</s>")) {
                continue
            }
            val cleaned = token.replace("Ġ", " ")
                .replace("▁", " ")
                .replace("##", "")
            sb.append(cleaned)
        }
        return sb.toString().trim()
    }

    fun isModelAvailable(): Boolean {
        return try {
            val am = context.assets
            assetExists(am, ENCODER_MODEL) && assetExists(am, DECODER_MODEL)
        } catch (e: Exception) {
            false
        }
    }

    fun warmup() {
        if (state != State.READY) return
        val dummyAudio = FloatArray(SAMPLE_RATE) { 0.001f }
        try {
            extractLogMel(dummyAudio)
        } catch (e: Exception) {
            // warmup failure is non-fatal
        }
    }

    fun getMemoryUsageEstimate(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory() - runtime.freeMemory()
        } catch (e: Exception) {
            0L
        }
    }

    fun release() {
        try {
            encoderSession?.close()
            decoderSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            // ignore cleanup errors
        }
        encoderSession = null
        decoderSession = null
        ortEnv = null
        updateState(State.UNINITIALIZED)
    }

    data class LocalAsrResult(
        val text: String,
        val durationMs: Float
    )
}
