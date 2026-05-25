package com.heecomou.desktop.ui

import androidx.compose.animation.core.*
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
import kotlin.math.abs
import kotlin.math.sin

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
            .background(Color(0xCC1A1A2E), RoundedCornerShape(16.dp))
            .padding(20.dp)
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
    Spacer(modifier = Modifier.height(20.dp))

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0xFF4A90D9)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83C\uDF99",
            fontSize = 28.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "准备就绪",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )

    Text(
        text = "开始说话以输入文字",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
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
            .size((48 + animatedLevel * 32).dp)
            .clip(CircleShape)
            .background(
                Color(0xFFE74C3C).copy(alpha = 0.7f + animatedLevel * 0.3f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83C\uDF99",
            fontSize = (22 + animatedLevel * 8).sp
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    val minutes = recordingSeconds / 60
    val seconds = recordingSeconds % 60
    val timeText = "%d:%02d".format(minutes, seconds)
    val remaining = maxSeconds - recordingSeconds.toInt()

    Text(
        text = "正在聆听...  $timeText",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    if (remaining <= 10) {
        Text(
            text = "即将自动停止: ${remaining}秒",
            color = Color(0xFFFF6B6B),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }

    AudioLevelBars(animatedLevel)

    Text(
        text = "再按 Ctrl+Shift+V 停止识别 | 最长${maxSeconds}秒",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RecognizingContent(partialText: String) {
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xFF4A90D9)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u23F3",
            fontSize = 24.sp
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "识别中...",
        color = Color(0xFF4A90D9),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    if (partialText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = partialText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
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

    Text(
        text = "\u2705",
        fontSize = 28.sp
    )

    Text(
        text = "识别完成",
        color = Color(0xFF2ECC71),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
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
                focusedBorderColor = Color(0xFF4A90D9),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                cursorColor = Color(0xFF4A90D9)
            ),
            maxLines = 3,
            singleLine = false
        )

        if (hasChanges) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\u270F\uFE0F 文本已修改，可提交纠错",
                color = Color(0xFFF39C12),
                fontSize = 11.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (hasChanges) {
            Button(
                onClick = {
                    onSubmitCorrection(originalText, editedText)
                    submitted = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE67E22)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("提交纠错", fontSize = 13.sp)
            }
        }

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A90D9)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("关闭", fontSize = 13.sp)
        }
    }

    if (submitted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\u2714\uFE0F 纠错已提交",
            color = Color(0xFF2ECC71),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AudioLevelBars(level: Float) {
    val barCount = 5
    val baseHeight = 8.dp
    val maxHeight = 32.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val phase = i * 0.5f
            val barLevel = (level * (0.5f + 0.5f * sin(phase))).coerceIn(0f, 1f)
            val height = baseHeight + (maxHeight - baseHeight) * barLevel

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Color(0xFF4A90D9).copy(alpha = 0.4f + barLevel * 0.6f)
                    )
            )
        }
    }
}
