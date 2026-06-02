package com.heecomou.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import kotlin.math.sin

private val BrandBlue = Color(0xFF2563EB)
private val AccentGreen = Color(0xFF10B981)
private val AccentOrange = Color(0xFFF59E0B)
private val AlertRed = Color(0xFFEF4444)

enum class VoiceInputState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    RESULT
}

@Composable
fun FrameWindowScope.FloatingVoiceWindow(
    state: VoiceInputState,
    recognizedText: String,
    partialText: String,
    audioLevel: Float,
    recordingSeconds: Long,
    maxSeconds: Int,
    onDismiss: () -> Unit,
    onSubmitCorrection: (originalText: String, correctedText: String) -> Unit
) {
    val isActive = state == VoiceInputState.LISTENING || state == VoiceInputState.RECOGNIZING
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "audioLevel"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF01A1A2E), RoundedCornerShape(18.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                VoiceInputState.IDLE -> IdleContent()
                VoiceInputState.LISTENING -> ListeningContent(
                    animatedLevel = animatedLevel,
                    recordingSeconds = recordingSeconds,
                    maxSeconds = maxSeconds
                )
                VoiceInputState.RECOGNIZING -> RecognizingContent(partialText)
                VoiceInputState.RESULT -> ResultContent(recognizedText, onDismiss, onSubmitCorrection)
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Spacer(modifier = Modifier.height(24.dp))

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(BrandBlue.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(BrandBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83C\uDF99\uFE0F", fontSize = 24.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "准备就绪",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )

    Text(
        text = "开始说话以输入文字",
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "按 Ctrl+Shift+V 开始语音输入",
        color = Color.White.copy(alpha = 0.3f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ListeningContent(
    animatedLevel: Float,
    recordingSeconds: Long,
    maxSeconds: Int
) {
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .size((52 + animatedLevel * 28).dp)
            .clip(CircleShape)
            .background(
                AlertRed.copy(alpha = 0.6f + animatedLevel * 0.35f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83C\uDF99\uFE0F",
            fontSize = (22 + animatedLevel * 6).sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    val minutes = recordingSeconds / 60
    val seconds = recordingSeconds % 60
    val timeText = "%d:%02d".format(minutes, seconds)
    val remaining = maxSeconds - recordingSeconds.toInt()

    Text(
        text = "\u25CF 正在聆听  $timeText",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    if (remaining <= 10) {
        Text(
            text = "\u26A0\uFE0F 将在 ${remaining} 秒后自动停止",
            color = Color(0xFFFF6B6B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }

    AudioLevelBars(animatedLevel)

    Text(
        text = "再按 Ctrl+Shift+V 停止识别  |  最长 ${maxSeconds} 秒",
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RecognizingContent(partialText: String) {
    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(BrandBlue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = BrandBlue,
            strokeWidth = 3.dp,
            trackColor = BrandBlue.copy(alpha = 0.15f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "识别中...",
        color = BrandBlue,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "正在将语音转换为文字",
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )

    if (partialText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = partialText,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ResultContent(
    originalText: String,
    onDismiss: () -> Unit,
    onSubmitCorrection: (originalText: String, correctedText: String) -> Unit
) {
    var editedText by remember { mutableStateOf(originalText) }
    var submitted by remember { mutableStateOf(false) }
    val hasChanges = editedText != originalText && !submitted

    Spacer(modifier = Modifier.height(4.dp))

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AccentGreen.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "\u2714\uFE0F", fontSize = 22.sp)
    }

    Text(
        text = "识别完成",
        color = AccentGreen,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
    )

    if (originalText.isNotEmpty()) {
        OutlinedTextField(
            value = editedText,
            onValueChange = { if (!submitted) editedText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 96.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                cursorColor = BrandBlue
            ),
            maxLines = 3,
            singleLine = false
        )

        AnimatedVisibility(
            visible = hasChanges,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AccentOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "\u270F\uFE0F 文本已修改，可提交纠错",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = AccentOrange,
                    fontSize = 11.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        if (hasChanges) {
            Button(
                onClick = {
                    onSubmitCorrection(originalText, editedText)
                    submitted = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("提交纠错", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    AnimatedVisibility(
        visible = submitted,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            text = "\u2714\uFE0F 纠错已提交",
            color = AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AudioLevelBars(level: Float) {
    val barCount = 5
    val baseHeight = 10.dp
    val maxHeight = 36.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val phase = i * 0.5f
            val barLevel = (level * (0.5f + 0.5f * sin(phase))).coerceIn(0f, 1f)
            val height = baseHeight + (maxHeight - baseHeight) * barLevel

            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        BrandBlue.copy(alpha = 0.35f + barLevel * 0.65f)
                    )
            )
        }
    }
}
