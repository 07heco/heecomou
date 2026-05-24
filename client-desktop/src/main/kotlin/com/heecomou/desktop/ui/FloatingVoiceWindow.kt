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
    onDismiss: () -> Unit
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
                VoiceInputState.LISTENING -> ListeningContent(animatedLevel)
                VoiceInputState.RECOGNIZING -> RecognizingContent(partialText)
                VoiceInputState.RESULT -> ResultContent(recognizedText, onDismiss)
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
private fun ListeningContent(animatedLevel: Float) {
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

    Text(
        text = "正在聆听...",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    AudioLevelBars(animatedLevel)

    Text(
        text = "说完后自动识别",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RecognizingContent(partialText: String) {
    Spacer(modifier = Modifier.height(8.dp))

    val infiniteTransition = rememberInfiniteTransition(label = "recognizing")
    infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

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
private fun ResultContent(text: String, onDismiss: () -> Unit) {
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

    if (text.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

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
