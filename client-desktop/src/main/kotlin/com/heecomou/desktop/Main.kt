package com.heecomou.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    println("[TokenManager] 存储路径: ${tokenManager.storagePath}, 可写: ${tokenManager.writable}")
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
    var fallbackInProgress by remember { mutableStateOf(false) }

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

    var startLocalRecognition: () -> Unit = {}
    var startCloudRecognition: () -> Unit = {}

    startCloudRecognition = {
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
            println("[DEBUG] cloud connection failed: $err")
            if (!fallbackInProgress) {
                fallbackInProgress = true
                statusMessage = "云端不可达，自动切换到端侧识别..."
                println("[DEBUG] falling back to local ASR")
                audioCaptureManager.stopRecording()
                cloudAsrClient.disconnect()
                startLocalRecognition()
            } else {
                statusMessage = "云端和端侧均不可用，请检查网络或运行环境"
                voiceInputState = VoiceInputState.IDLE
                isVoiceWindowVisible = false
                audioCaptureManager.stopRecording()
                fallbackInProgress = false
                println("[DEBUG] both cloud and local failed: $err")
            }
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
        } else {
            startRecordingTimer()
        }
    }

    startLocalRecognition = {
        println("[DEBUG] startLocalRecognition: initializing local ASR...")

        if (!localAsrClient.initialize()) {
            println("[DEBUG] local ASR init failed")
            if (!fallbackInProgress) {
                fallbackInProgress = true
                statusMessage = "端侧初始化失败，尝试云端识别..."
                println("[DEBUG] falling back to cloud")
                startCloudRecognition()
            } else {
                statusMessage = "端侧和云端均不可用，请检查 Python 环境和网络"
                voiceInputState = VoiceInputState.IDLE
                isVoiceWindowVisible = false
                fallbackInProgress = false
                println("[DEBUG] both local and cloud failed")
            }
        } else {

        fallbackInProgress = false

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
        } else {
            startRecordingTimer()
        }
    }
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

        fallbackInProgress = false

        try {
            // Quick reachability check for cloud server
            val cloudReachable = try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("117.72.201.26", 8080), 2000)
                socket.close()
                true
            } catch (_: Exception) {
                false
            }

            val decision = asrRouter.decide(
                preferences = asrPreferences,
                isNetworkAvailable = cloudReachable,
                networkRttMs = 0,
                noiseLevel = 0
            )
            println("[DEBUG] AsrRouter: ${decision.mode.label} — ${decision.reason} (cloudReachable=$cloudReachable)")

            isVoiceWindowVisible = true
            voiceInputState = VoiceInputState.LISTENING
            recordingSeconds = 0L
            statusMessage = "${decision.mode.label}: ${decision.reason}"

            when (decision.mode) {
                AsrEngineMode.CLOUD -> startCloudRecognition()
                AsrEngineMode.LOCAL -> startLocalRecognition()
                AsrEngineMode.AUTO -> {
                    if (cloudReachable) {
                        startCloudRecognition()
                    } else {
                        statusMessage = "网络不可达，使用端侧识别"
                        startLocalRecognition()
                    }
                }
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
        fallbackInProgress = false
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
                width = 380.dp,
                height = 340.dp,
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
                width = 460.dp,
                height = 600.dp,
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
        val mainBg = Color(0xFFF5F7FA)
        val primaryBlue = Color(0xFF2563EB)

        Window(
            onCloseRequest = { isMainWindowVisible = false },
            title = "HeecoMou Desktop",
            state = rememberWindowState(width = 600.dp, height = 680.dp)
        ) {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().background(mainBg)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(primaryBlue)
                                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isLoggedIn) loggedInUsername.take(1).uppercase() else "\uD83C\uDF99",
                                        fontSize = if (isLoggedIn) 22.sp else 24.sp,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "HeecoMou",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isLoggedIn) "欢迎, $loggedInUsername" else "智能语音输入法",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = statusColor(statusMessage),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = statusMessage,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = "按 Ctrl+Shift+V 开始/停止语音输入  |  Esc 取消",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MiniStatusCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "\u2328\uFE0F",
                                    label = "热键",
                                    value = if (hotkeyManager.isRegistered()) "已注册" else "未注册",
                                    ok = hotkeyManager.isRegistered()
                                )
                                MiniStatusCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "\uD83C\uDF99\uFE0F",
                                    label = "音频",
                                    value = if (audioCaptureManager.isSupported()) "可用" else "不可用",
                                    ok = audioCaptureManager.isSupported()
                                )
                                MiniStatusCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "\uD83D\uDCDA",
                                    label = "词库",
                                    value = "${vocabCount} 词",
                                    ok = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MiniStatusCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "\uD83D\uDC64",
                                    label = "账号",
                                    value = if (isLoggedIn) loggedInUsername else "未登录",
                                    ok = isLoggedIn
                                )
                                MiniStatusCard(
                                    modifier = Modifier.weight(1f),
                                    icon = "\u2699\uFE0F",
                                    label = "模式",
                                    value = asrPreferences.engineMode.label,
                                    ok = true
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { startAsrPipeline() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "\uD83C\uDF99\uFE0F  开始语音输入",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        asrPreferences = when (asrPreferences.engineMode) {
                                            AsrEngineMode.AUTO -> AsrPreferences(engineMode = AsrEngineMode.CLOUD)
                                            AsrEngineMode.CLOUD -> AsrPreferences(engineMode = AsrEngineMode.LOCAL)
                                            AsrEngineMode.LOCAL -> AsrPreferences(engineMode = AsrEngineMode.AUTO)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "切换: ${asrPreferences.engineMode.label}",
                                        fontSize = 13.sp
                                    )
                                }

                                OutlinedButton(
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
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = if (isLoggedIn) {
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFEF4444)
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(
                                        text = if (isLoggedIn) "登出" else "登录",
                                        fontSize = 13.sp
                                    )
                                }
                            }
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
private fun MiniStatusCard(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    value: String,
    ok: Boolean
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF6B7280)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) Color(0xFF2563EB) else Color(0xFFEF4444)
            )
        }
    }
}

private fun statusColor(msg: String): Color {
    return when {
        msg.contains("已登录") || msg.contains("完成") || msg.contains("最新") -> Color(0xFF16A34A)
        msg.contains("已登出") || msg.contains("未登录") || msg.contains("离线") || msg.contains("不可用") -> Color(0xFF6B7280)
        msg.contains("失败") || msg.contains("错误") || msg.contains("\u26A0") -> Color(0xFFEF4444)
        msg.contains("识别中") || msg.contains("同步") || msg.contains("连接") -> Color(0xFF2563EB)
        else -> Color(0xFF2563EB)
    }
}

object TrayIcon : Painter() {
    override val intrinsicSize = Size(16f, 16f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF4A90D9), radius = 7f)
    }
}
