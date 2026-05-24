package com.heecomou.ime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.heecomou.ime.audio.AudioCaptureManager
import com.heecomou.ime.audio.ClientVAD
import com.heecomou.ime.asr.AsrEngineMode
import com.heecomou.ime.asr.LocalAsrClient
import com.heecomou.ime.network.asr.CloudAsrClient
import com.heecomou.ime.ui.voice.VoiceInputPanel
import com.heecomou.ime.ui.voice.VoiceInputState
import java.util.concurrent.Executors

class HeecoMouIME : InputMethodService() {

    companion object {
        private const val TAG = "HeecoMouIME"
        private const val MAX_RECORD_MS = 10_000L
    }

    private lateinit var voiceButton: Button
    private lateinit var voicePanel: VoiceInputPanel
    private lateinit var audioCapture: AudioCaptureManager
    private var asrClient: CloudAsrClient? = null
    private var localAsrClient: LocalAsrClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var isRecording = false
    private var stopRecordingTask: Runnable? = null
    private var vad: ClientVAD? = null
    private var engineMode: AsrEngineMode = AsrEngineMode.AUTO

    fun setEngineMode(mode: AsrEngineMode) {
        engineMode = mode
        Log.d(TAG, "Engine mode changed to: ${mode.label}")
    }

    fun getEngineMode(): AsrEngineMode = engineMode

    override fun onCreate() {
        super.onCreate()
        audioCapture = AudioCaptureManager(
            sampleRate = 16000,
            channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO,
            audioEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        )
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        voicePanel = VoiceInputPanel(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(voicePanel)

        voiceButton = Button(this).apply {
            text = "语音输入"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            setOnClickListener {
                Log.d(TAG, "Voice button clicked")
                toggleVoiceInput()
            }
        }
        layout.addView(voiceButton)

        return layout
    }

    private fun toggleVoiceInput() {
        if (isRecording) {
            stopVoiceInput()
        } else {
            startVoiceInput()
        }
    }

    private fun startVoiceInput() {
        if (!hasMicPermission()) {
            voicePanel.setState(VoiceInputState.ERROR, "需要麦克风权限")
            return
        }

        voicePanel.setState(VoiceInputState.LISTENING)
        voiceButton.text = "停止"
        isRecording = true

        vad = ClientVAD().apply { reset() }

        if (engineMode == AsrEngineMode.LOCAL) {
            startLocalRecognition()
        } else {
            startCloudRecognition()
        }

        audioCapture.onAudioData = { pcmData ->
            val shortSamples = ShortArray(pcmData.size / 2)
            for (i in shortSamples.indices) {
                val low = pcmData[i * 2].toInt() and 0xFF
                val high = pcmData[i * 2 + 1].toInt() and 0xFF
                shortSamples[i] = ((high shl 8) or low).toShort()
            }
            val hasSpeech = vad?.detect(shortSamples) ?: true
            if (hasSpeech) {
                if (engineMode == AsrEngineMode.LOCAL) {
                    localAsrClient?.feedPcmData(pcmData, true, System.currentTimeMillis())
                } else {
                    asrClient?.sendAudio(pcmData)
                }
            }
        }
        audioCapture.onError = { error ->
            mainHandler.post {
                voicePanel.setState(VoiceInputState.ERROR, error)
                resetAfterDelay()
            }
        }

        audioCapture.startRecording()

        stopRecordingTask = Runnable {
            if (isRecording) {
                stopVoiceInput()
            }
        }
        mainHandler.postDelayed(stopRecordingTask!!, MAX_RECORD_MS)
    }

    private fun startCloudRecognition() {
        asrClient = CloudAsrClient()
        asrClient?.onSessionStarted = { session ->
            Log.d(TAG, "ASR session started: ${session.sessionId}")
        }
        asrClient?.onError = { error ->
            mainHandler.post {
                voicePanel.setState(VoiceInputState.ERROR, error)
            }
        }
        asrClient?.connect()
    }

    private fun startLocalRecognition() {
        localAsrClient = LocalAsrClient(this)
        localAsrClient?.onStateChanged = { state ->
            Log.d(TAG, "Local ASR state: $state")
        }
        localAsrClient?.onFinalResult = { text ->
            mainHandler.post {
                voicePanel.setState(VoiceInputState.RESULT)
                voicePanel.setResultText(text)
            }
        }
        localAsrClient?.onError = { error ->
            mainHandler.post {
                voicePanel.setState(VoiceInputState.ERROR, error)
            }
        }
        localAsrClient?.initialize()
        localAsrClient?.startListening()
    }

    private fun stopVoiceInput() {
        isRecording = false
        stopRecordingTask?.let { mainHandler.removeCallbacks(it) }
        stopRecordingTask = null

        audioCapture.stopRecording()
        ioExecutor.execute {
            if (engineMode == AsrEngineMode.LOCAL) {
                localAsrClient?.stopListening()
            } else {
                asrClient?.disconnect()
                asrClient = null
            }
        }

        voicePanel.setState(VoiceInputState.RECOGNIZING)
        mainHandler.postDelayed({
            voicePanel.setState(VoiceInputState.IDLE)
            voiceButton.text = "语音输入"
        }, 1500)
    }

    private fun resetAfterDelay() {
        mainHandler.postDelayed({
            voicePanel.setState(VoiceInputState.IDLE)
            voiceButton.text = "语音输入"
            isRecording = false
        }, 2000)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        audioCapture.release()
        asrClient?.disconnect()
        asrClient = null
        localAsrClient?.release()
        localAsrClient = null
        ioExecutor.shutdown()
    }
}
