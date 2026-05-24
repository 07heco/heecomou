package com.heecomou.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.heecomou.desktop.audio.AudioCaptureManager
import com.heecomou.desktop.asr.*
import com.heecomou.desktop.hotkey.GlobalHotkeyManager
import com.heecomou.desktop.network.VocabApiService
import com.heecomou.desktop.ui.FloatingVoiceWindow
import com.heecomou.desktop.ui.TextOutputManager
import com.heecomou.desktop.ui.VoiceInputState
import com.heecomou.desktop.vocab.LocalVocabStore
import com.heecomou.desktop.vocab.VocabSyncManager
import kotlinx.coroutines.*

fun main() = application {
    val hotkeyManager = remember { GlobalHotkeyManager() }
    val textOutput = remember { TextOutputManager() }
    val vocabApiService = remember { VocabApiService() }
    val localVocabStore = remember { LocalVocabStore() }
    val vocabSyncManager = remember { VocabSyncManager(vocabApiService, localVocabStore) }
    val asrRouter = remember { AsrRouter() }
    val coroutineScope = rememberCoroutineScope()

    var isMainWindowVisible by remember { mutableStateOf(true) }
    var isVoiceWindowVisible by remember { mutableStateOf(false) }
    var voiceInputState by remember { mutableStateOf(VoiceInputState.IDLE) }
    var recognizedText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var audioLevel by remember { mutableStateOf(0f) }
    var asrPreferences by remember { mutableStateOf(AsrPreferences()) }
    var statusMessage by remember { mutableStateOf("语音输入服务已就绪") }
    var vocabCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        hotkeyManager.onHotkeyTriggered = {
            isVoiceWindowVisible = true
            voiceInputState = VoiceInputState.LISTENING
            statusMessage = "正在聆听..."
        }
        hotkeyManager.onCancelTriggered = {
            isVoiceWindowVisible = false
            voiceInputState = VoiceInputState.IDLE
            statusMessage = "已取消"
        }
        hotkeyManager.register()

        coroutineScope.launch {
            try {
                vocabSyncManager.syncIfNeeded()
                vocabCount = vocabSyncManager.getLocalWordCount()
                statusMessage = "词库同步完成 (${vocabCount} 词)"
            } catch (_: Exception) {
                // Sync is optional
            }
        }
    }

    if (isVoiceWindowVisible) {
        Window(
            onCloseRequest = {
                isVoiceWindowVisible = false
                voiceInputState = VoiceInputState.IDLE
            },
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
                onDismiss = {
                    isVoiceWindowVisible = false
                    voiceInputState = VoiceInputState.IDLE
                }
            )
        }
    }

    if (isMainWindowVisible) {
        Window(
            onCloseRequest = { isMainWindowVisible = false },
            title = "HeecoMou Desktop",
            state = rememberWindowState(width = 420.dp, height = 380.dp)
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
                        "按 Ctrl+Shift+V 唤起语音输入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status indicators
                    StatusRow("热键", if (hotkeyManager.isRegistered()) "\u2705 已注册" else "\u274C 未注册")
                    StatusRow("音频", if (AudioCaptureManager().isSupported()) "\u2705 可用" else "\u26A0\uFE0F 不可用")
                    StatusRow("词库", "${vocabCount} 词")
                    StatusRow("模式", asrPreferences.engineMode.label)
                    StatusRow("方言", asrRouter.getSupportedDialects()[asrPreferences.dialect] ?: asrPreferences.dialect)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isVoiceWindowVisible = true
                                voiceInputState = VoiceInputState.LISTENING
                            },
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
                }
            }
        }
    }

    Tray(
        icon = TrayIcon,
        tooltip = "HeecoMou",
        menu = {
            Item("语音输入", onClick = {
                isVoiceWindowVisible = true
                voiceInputState = VoiceInputState.LISTENING
            })
            Item("设置", onClick = {
                isMainWindowVisible = true
            })
            Separator()
            Item("退出", onClick = {
                hotkeyManager.unregister()
                vocabSyncManager.getLocalWordCount()
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
