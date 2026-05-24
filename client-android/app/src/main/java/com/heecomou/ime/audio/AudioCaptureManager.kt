package com.heecomou.ime.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureManager(
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    var onAudioData: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    val bufferSize: Int
        get() = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            .coerceAtLeast(sampleRate * 2 / 10)
            .let { it * BUFFER_SIZE_MULTIPLIER }

    val bytesPerSample: Int
        get() = if (audioEncoding == AudioFormat.ENCODING_PCM_16BIT) 2 else 1

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(): Boolean {
        if (isRecording.get()) {
            onError?.invoke("Recording is already in progress")
            return false
        }

        val minBufferSize = bufferSize

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioEncoding,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError?.invoke("AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            onStateChanged?.invoke(true)

            val buffer = ByteArray(minBufferSize / 2)

            recordingThread = Thread {
                try {
                    while (isRecording.get()) {
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        when {
                            bytesRead > 0 -> {
                                val chunk = buffer.copyOf(bytesRead)
                                onAudioData?.invoke(chunk)
                            }
                            bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                                onError?.invoke("AudioRecord invalid operation")
                                break
                            }
                            bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                                onError?.invoke("AudioRecord bad value")
                                break
                            }
                            bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                                onError?.invoke("AudioRecord dead object")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isRecording.get()) {
                        onError?.invoke("Recording error: ${e.message}")
                    }
                }
            }

            recordingThread?.name = "AudioCapture-Thread"
            recordingThread?.start()
            return true
        } catch (e: SecurityException) {
            onError?.invoke("Microphone permission denied")
            return false
        } catch (e: IllegalArgumentException) {
            onError?.invoke("Invalid audio parameters: ${e.message}")
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
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            onError?.invoke("Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null

        onStateChanged?.invoke(false)
    }

    fun release() {
        stopRecording()
        onAudioData = null
        onError = null
        onStateChanged = null
    }
}
