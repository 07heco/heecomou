package com.heecomou.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.heecomou.desktop.audio.AudioCaptureManager
import com.heecomou.desktop.asr.*
import com.heecomou.desktop.hotkey.GlobalHotkeyManager
import com.heecomou.desktop.network.VocabApiService
import com.heecomou.desktop.network.CorrectionRequest
import com.heecomou.desktop.ui.FloatingVoiceWindow
import com.heecomou.desktop.ui.TextOutputManager
import com.heecomou.desktop.asr.LocalAsrState
import com.heecomou.desktop.ui.VoiceInputState
import com.heecomou.desktop.auth.TokenManager
import com.heecomou.desktop.ui.LoginWindow
import com.heecomou.desktop.vocab.LocalVocabStore
import com.heecomou.desktop.vocab.VocabSyncManager
import kotlinx.coroutines.*
import kotlin.math.abs

private const val MAX_RECORDING_SECONDS = 60

fun main() = application {
    val hotkeyManager = remember { GlobalHotkeyManager() }
    val textOutput = remember { TextOutputManager() }
    val tokenManager = remember { TokenManager() }
    val vocabApiService = remember { VocabApiService(tokenProvider = { tokenManager.getAccessToken() }) }
    val localVocabStore = remember { LocalVocabStore() }
    val vocabSyncManager = remember { VocabSyncManager(vocabApiService, localVocabStore) }
    val asrRouter = remember { AsrRouter() }
    val audioCaptureManager = remember { AudioCaptureManager() }
    val cloudAsrClient = remember { CloudAsrClient() }
    val localAsrClient = remember { LocalAsrClient() }
    val coroutineScope = rememberCoroutineScope()

    var isMainWindowVisible by remember { mutableStateOf(false) }
    var isVoiceWindowVisible by remember { mutableStateOf(false) }
    var voiceInputState by remember { mutableStateOf(VoiceInputState.IDLE) }
    var recognizedText by remember { mutableStateOf("") }
    var originalAsrText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var audioLevel by remember { mutableStateOf(0f) }
    var asrPreferences by remember { mutableStateOf(AsrPreferences()) }
    var statusMessage by remember { mutableStateOf("语音输入服务已就绪") }
    var vocabCount by remember { mutableStateOf(0) }
    var recordingSeconds by remember { mutableStateOf(0L) }
    var maxSeconds by remember { mutableStateOf(MAX_RECORDING_SECONDS) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loggedInUsername by remember { mutableStateOf("") }
    var showLoginWindow by remember { mutableStateOf(false) }

    fun bumpUsedWords(text: String) {
        if (text.isBlank()) return
        val words = text.replace(Regex("[^\\u4e00-\\u9fff\\w]"), " ").split(" ")
            .filter { it.length >= 2 }
        for (w in words) {
            vocabSyncManager.bumpWordFrequency(w)
        }
    }

    fun finishRecording() {
        if (voiceInputState != VoiceInputState.LISTENING) return
        timerJob?.cancel()
        println("[DEBUG] manual stop: stopping audio capture, waiting for ASR result...")
        audioCaptureManager.stopRecording()
        if (localAsrClient.currentState == LocalAsrState.RECOGNIZING) {
            localAsrClient.stopListening()
        }
        voiceInputState = VoiceInputState.RECOGNIZING
        statusMessage = "等待识别结果..."
    }

    fun startRecordingTimer() {
        timerJob = coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                recordingSeconds = elapsed
                if (elapsed >= MAX_RECORDING_SECONDS.toLong()) {
                    println("[DEBUG] max timeout reached, stopping...")
                    finishRecording()
                    break
                }
            }
        }
    }

    fun startCloudRecognition() {
        println("[DEBUG] startCloudRecognition: connecting to cloud ASR...")

        audioCaptureManager.onAudioData = { pcmData ->
            cloudAsrClient.sendAudio(pcmData)
            val level = pcmData.map { abs(it.toInt()) }.average().toFloat() / 128f
            audioLevel = level.coerceIn(0f, 1f)
        }
        audioCaptureManager.onError = { err ->
            statusMessage = "音频: $err"
            println("[DEBUG] audio error: $err")
        }

        cloudAsrClient.onPartialResult = { text ->
            partialText = text
            voiceInputState = VoiceInputState.RECOGNIZING
            println("[DEBUG] partial: $text")
        }
        cloudAsrClient.onFinalResult = { text, confidence ->
            recognizedText = text
            originalAsrText = text
            voiceInputState = VoiceInputState.RESULT
            statusMessage = "云端识别完成"
            println("[DEBUG] final: $text (confidence=$confidence)")
            timerJob?.cancel()
            audioCaptureManager.stopRecording()
            cloudAsrClient.disconnect()
            coroutineScope.launch {
                textOutput.output(text)
                bumpUsedWords(text)
            }
        }
        cloudAsrClient.onConnectionFailed = { err ->
            statusMessage = "连接失败: $err"
            voiceInputState = VoiceInputState.IDLE
            isVoiceWindowVisible = false
            timerJob?.cancel()
            audioCaptureManager.stopRecording()
            println("[DEBUG] connection failed: $err")
        }
        cloudAsrClient.onError = { err ->
            statusMessage = "识别错误: $err"
            println("[DEBUG] asr error: $err")
            timerJob?.cancel()
            audioCaptureManager.stopRecording()
            cloudAsrClient.disconnect()
            recognizedText = "识别失败: $err"
            voiceInputState = VoiceInputState.RESULT
        }
        cloudAsrClient.onDisconnected = {
            println("[DEBUG] ws disconnected, currentState=$voiceInputState")
            timerJob?.cancel()
            if (voiceInputState == VoiceInputState.LISTENING || voiceInputState == VoiceInputState.RECOGNIZING) {
                audioCaptureManager.stopRecording()
                voiceInputState = VoiceInputState.IDLE
                statusMessage = "识别超时，请重试"
                isVoiceWindowVisible = false
            }
        }

        cloudAsrClient.connect()
        println("[DEBUG] starting audio recording...")
        val recordingStarted = audioCaptureManager.startRecording()
        println("[DEBUG] recording started=$recordingStarted")
        if (!recordingStarted) {
            statusMessage = "麦克风启动失败"
            voiceInputState = VoiceInputState.IDLE
            isVoiceWindowVisible = false
            return
        }

        startRecordingTimer()
    }

    fun startLocalRecognition() {
        println("[DEBUG] startLocalRecognition: initializing local ASR...")

        if (!localAsrClient.initialize()) {
            println("[DEBUG] local ASR init failed, falling back to cloud")
            statusMessage = "端侧初始化失败，切换到云端"
            voiceInputState = VoiceInputState.IDLE
            startCloudRecognition()
            return
        }

        localAsrClient.vocabWords = localVocabStore.search("", 500).map { it.first }

        audioCaptureManager.onAudioData = { pcmData ->
            localAsrClient.feedPcmData(
                pcmData,
                isSpeech = true,
                timestampMs = System.currentTimeMillis()
            )
            val level = pcmData.map { abs(it.toInt()) }.average().toFloat() / 128f
            audioLevel = level.coerceIn(0f, 1f)
        }
        audioCaptureManager.onError = { err ->
            statusMessage = "音频: $err"
            println("[DEBUG] audio error: $err")
        }

        localAsrClient.onPartialResult = { text ->
            partialText = text
            println("[DEBUG] local partial: $text")
        }
        localAsrClient.onFinalResult = { text ->
            recognizedText = text
            originalAsrText = text
            voiceInputState = VoiceInputState.RESULT
            statusMessage = "端侧识别完成"
            println("[DEBUG] local final: $text")
            timerJob?.cancel()
            audioCaptureManager.stopRecording()
            coroutineScope.launch {
                textOutput.output(text)
                bumpUsedWords(text)
            }
        }
        localAsrClient.onError = { err ->
            statusMessage = "端侧错误: $err"
            println("[DEBUG] local asr error: $err")
            timerJob?.cancel()
            audioCaptureManager.stopRecording()
            recognizedText = "识别失败: $err"
            voiceInputState = VoiceInputState.RESULT
        }

        localAsrClient.startListening()
        println("[DEBUG] starting audio recording for local...")
        val recordingStarted = audioCaptureManager.startRecording()
        println("[DEBUG] local recording started=$recordingStarted")
        if (!recordingStarted) {
            statusMessage = "麦克风启动失败"
            voiceInputState = VoiceInputState.IDLE
            isVoiceWindowVisible = false
            return
        }

        startRecordingTimer()
    }

    fun startAsrPipeline() {
        when (voiceInputState) {
            VoiceInputState.LISTENING -> {
                finishRecording()
                return
            }
            VoiceInputState.RECOGNIZING -> return
            VoiceInputState.RESULT -> return
            VoiceInputState.IDLE -> { /* proceed */ }
        }

        try {
            val decision = asrRouter.decide(
                preferences = asrPreferences,
                isNetworkAvailable = true,
                networkRttMs = 0,
                noiseLevel = 0
            )
            println("[DEBUG] AsrRouter: ${decision.mode.label} — ${decision.reason}")

            isVoiceWindowVisible = true
            voiceInputState = VoiceInputState.LISTENING
            recordingSeconds = 0L
            statusMessage = "${decision.mode.label}: ${decision.reason}"

            when (decision.mode) {
                AsrEngineMode.CLOUD -> startCloudRecognition()
                AsrEngineMode.LOCAL -> startLocalRecognition()
                AsrEngineMode.AUTO -> startCloudRecognition()
            }
        } catch (e: Exception) {
            println("[DEBUG] startAsrPipeline exception: ${e.message}")
            e.printStackTrace()
            statusMessage = "启动失败: ${e.message}"
            voiceInputState = VoiceInputState.IDLE
            isVoiceWindowVisible = false
            timerJob?.cancel()
        }
    }

    fun stopAsrPipeline() {
        timerJob?.cancel()
        audioCaptureManager.stopRecording()
        cloudAsrClient.disconnect()
        localAsrClient.stopListening()
        isVoiceWindowVisible = false
        voiceInputState = VoiceInputState.IDLE
        recordingSeconds = 0L
        statusMessage = "已取消"
        println("[DEBUG] stopAsrPipeline: cancelled")
    }

    LaunchedEffect(Unit) {
        hotkeyManager.onHotkeyTriggered = { startAsrPipeline() }
        hotkeyManager.onCancelTriggered = { stopAsrPipeline() }
        hotkeyManager.register()

        val savedToken = tokenManager.load()
        if (savedToken != null && tokenManager.getAccessToken() != null) {
            isLoggedIn = true
            loggedInUsername = savedToken.username
            isMainWindowVisible = true
        } else {
            showLoginWindow = true
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val result = vocabSyncManager.syncIfNeeded()
            vocabCount = result.totalCount
            if (result.error != null) {
                statusMessage = "⚠ ${result.error}"
                println("[DEBUG] SYNC ERROR: ${result.error}")
            } else if (result.syncedCount > 0) {
                statusMessage = "词库同步完成 (+${result.syncedCount}新, 共${result.totalCount}词)"
            } else {
                statusMessage = "词库已是最新 (共${result.totalCount}词)"
            }
        }

        while (isActive) {
            delay(120_000L)
            if (!isLoggedIn) continue
            val result = vocabSyncManager.syncIfNeeded()
            vocabCount = result.totalCount
            if (result.error != null) {
                statusMessage = "⚠ ${result.error}"
                println("[DEBUG] SYNC ERROR: ${result.error}")
            } else if (result.syncedCount > 0) {
                statusMessage = "词库同步: +${result.syncedCount}词"
            }
        }
    }

    if (isVoiceWindowVisible) {
        Window(
            onCloseRequest = { stopAsrPipeline() },
            title = "HeecoMou Voice",
            state = rememberWindowState(
                width = 360.dp,
                height = 240.dp,
                position = WindowPosition(Alignment.Center)
            ),
            alwaysOnTop = true,
            undecorated = true,
            transparent = true,
            focusable = false
        ) {
            FloatingVoiceWindow(
                state = voiceInputState,
                recognizedText = recognizedText,
                partialText = partialText,
                audioLevel = audioLevel,
                recordingSeconds = recordingSeconds,
                maxSeconds = maxSeconds,
                onDismiss = { stopAsrPipeline() },
                onSubmitCorrection = { original, corrected ->
                    coroutineScope.launch {
                        val result = vocabApiService.submitCorrection(
                            CorrectionRequest(originalText = original, correctedText = corrected, source = "desktop")
                        )
                        if (result != null && result.code == 200) {
                            println("[DEBUG] correction submitted: '$original' -> '$corrected'")
                        } else {
                            println("[DEBUG] correction submission failed")
                        }
                    }
                }
            )
        }
    }

    if (showLoginWindow) {
        Window(
            onCloseRequest = { },
            title = "HeecoMou - 登录",
            state = rememberWindowState(
                width = 420.dp,
                height = 480.dp,
                position = WindowPosition(Alignment.Center)
            ),
            resizable = false
        ) {
            LoginWindow(
                tokenManager = tokenManager,
                onLoginSuccess = { username ->
                    isLoggedIn = true
                    loggedInUsername = username
                    showLoginWindow = false
                    isMainWindowVisible = true
                    statusMessage = "已登录: $username"
                },
                onSkip = {
                    showLoginWindow = false
                    isMainWindowVisible = true
                    statusMessage = "离线模式，词库同步不可用"
                }
            )
        }
    }

    if (isMainWindowVisible) {
        Window(
            onCloseRequest = { isMainWindowVisible = false },
            title = "HeecoMou Desktop",
            state = rememberWindowState(width = 520.dp, height = 500.dp)
        ) {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HeecoMou Desktop",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(statusMessage)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "按 Ctrl+Shift+V 开始/停止语音输入 | Esc 取消",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow("热键", if (hotkeyManager.isRegistered()) "\u2705 已注册" else "\u274C 未注册")
                    StatusRow("音频", if (audioCaptureManager.isSupported()) "\u2705 可用" else "\u26A0\uFE0F 不可用")
                    StatusRow("词库", "${vocabCount} 词")
                    StatusRow("账号", if (isLoggedIn) "\u2705 ${loggedInUsername}" else "\u26A0\uFE0F 未登录")
                    StatusRow("模式", asrPreferences.engineMode.label)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { startAsrPipeline() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("语音输入")
                        }

                        OutlinedButton(
                            onClick = {
                                asrPreferences = when (asrPreferences.engineMode) {
                                    AsrEngineMode.AUTO -> AsrPreferences(engineMode = AsrEngineMode.CLOUD)
                                    AsrEngineMode.CLOUD -> AsrPreferences(engineMode = AsrEngineMode.LOCAL)
                                    AsrEngineMode.LOCAL -> AsrPreferences(engineMode = AsrEngineMode.AUTO)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(asrPreferences.engineMode.label)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (isLoggedIn) {
                                    tokenManager.clear()
                                    isLoggedIn = false
                                    loggedInUsername = ""
                                    vocabCount = 0
                                    statusMessage = "已登出"
                                } else {
                                    showLoginWindow = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isLoggedIn) "登出" else "登录")
                        }
                    }
                }
            }
        }
    }

    Tray(
        icon = TrayIcon,
        tooltip = "HeecoMou",
        menu = {
            Item("语音输入", onClick = { startAsrPipeline() })
            Item("设置", onClick = {
                isMainWindowVisible = true
            })
            Separator()
            Item("退出", onClick = {
                stopAsrPipeline()
                hotkeyManager.unregister()
                cloudAsrClient.release()
                localAsrClient.release()
                audioCaptureManager.release()
                localVocabStore.close()
                textOutput.release()
                isMainWindowVisible = false
                exitApplication()
            })
        }
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

object TrayIcon : Painter() {
    override val intrinsicSize = Size(16f, 16f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF4A90D9), radius = 7f)
    }
}
