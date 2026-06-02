package com.heecomou.desktop.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heecomou.desktop.auth.AuthApiService
import com.heecomou.desktop.auth.StoredToken
import com.heecomou.desktop.auth.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BrandBlue = Color(0xFF2563EB)
private val SurfaceWhite = Color(0xFFF7F8FA)
private val TextGray = Color(0xFF6B7280)

@Composable
fun LoginWindow(
    tokenManager: TokenManager,
    onLoginSuccess: (username: String) -> Unit,
    onSkip: () -> Unit
) {
    val authApiService = remember { AuthApiService() }
    val coroutineScope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(SurfaceWhite)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandBlue)
                        .padding(top = 48.dp, bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\uD83C\uDF99\uFE0F", fontSize = 28.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "HeecoMou",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "智能语音输入法",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!isRegisterMode) {
                                        Modifier
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .padding(vertical = 10.dp)
                                    } else {
                                        Modifier.padding(vertical = 10.dp)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = {
                                    isRegisterMode = false
                                    errorMessage = ""
                                }
                            ) {
                                Text(
                                    "登录",
                                    color = if (!isRegisterMode) BrandBlue else TextGray,
                                    fontWeight = if (!isRegisterMode) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isRegisterMode) {
                                        Modifier
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .padding(vertical = 10.dp)
                                    } else {
                                        Modifier.padding(vertical = 10.dp)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = {
                                    isRegisterMode = true
                                    errorMessage = ""
                                }
                            ) {
                                Text(
                                    "注册",
                                    color = if (isRegisterMode) BrandBlue else TextGray,
                                    fontWeight = if (isRegisterMode) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (isRegisterMode) "创建账号以同步词库与纠错记录" else "登录后可同步词库与纠错记录",
                        fontSize = 13.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("用户名") },
                        singleLine = true,
                        leadingIcon = { Text("\uD83D\uDC64", fontSize = 16.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            focusedLabelColor = BrandBlue
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        leadingIcon = { Text("\uD83D\uDD12", fontSize = 16.sp) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "隐藏" else "显示", fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            focusedLabelColor = BrandBlue
                        )
                    )

                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorMessage = "" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("邮箱") },
                                singleLine = true,
                                leadingIcon = { Text("\u2709\uFE0F", fontSize = 16.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandBlue,
                                    focusedLabelColor = BrandBlue
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = Color(0xFFDC2626),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "用户名和密码不能为空"
                                return@Button
                            }
                            if (isRegisterMode && email.isBlank()) {
                                errorMessage = "邮箱不能为空"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = ""
                            coroutineScope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        if (isRegisterMode) {
                                            authApiService.register(username, password, email)
                                        } else {
                                            authApiService.login(username, password)
                                        }
                                    }
                                    if (result == null) {
                                        errorMessage = "网络连接失败，无法访问服务器 (117.72.201.26:8081)"
                                    } else if (result.code == 200 && result.data != null) {
                                        try {
                                            tokenManager.save(
                                                StoredToken(
                                                    accessToken = result.data.accessToken,
                                                    refreshToken = result.data.refreshToken,
                                                    expiresAt = System.currentTimeMillis() + result.data.expiresIn,
                                                    userId = result.data.userId,
                                                    username = result.data.username
                                                )
                                            )
                                        } catch (ioe: Exception) {
                                            errorMessage = "登录信息保存失败: ${ioe.message}"
                                            println("[DEBUG] Token save error: ${ioe.message}")
                                            return@launch
                                        }
                                        onLoginSuccess(result.data.username)
                                    } else {
                                        errorMessage = result.message.ifBlank { "操作失败，请重试" }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "网络请求失败: ${e.message}"
                                    println("[DEBUG] Network error: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("处理中...", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Text(
                                text = if (isRegisterMode) "注册" else "登录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onSkip) {
                        Text(
                            text = "跳过登录（离线模式）",
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
