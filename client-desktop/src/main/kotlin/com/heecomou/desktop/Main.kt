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

fun main() = application {
    var isVisible by remember { mutableStateOf(true) }

    if (isVisible) {
        Window(
            onCloseRequest = { isVisible = false },
            title = "HeecoMou Desktop",
            state = rememberWindowState(width = 400.dp, height = 300.dp)
        ) {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "HeecoMou Desktop",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("语音输入服务已就绪")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "按 Ctrl+Shift+V 唤起语音输入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Tray(
        icon = TrayIcon,
        tooltip = "HeecoMou",
        menu = {
            Item("语音输入", onClick = { })
            Item("设置", onClick = { })
            Separator()
            Item("退出", onClick = { isVisible = false; exitApplication() })
        }
    )
}

object TrayIcon : Painter() {
    override val intrinsicSize = Size(16f, 16f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF4A90D9), radius = 7f)
    }
}