package com.heecomou.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "HeecoMou",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "登录后可同步词库与纠错记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isRegisterMode = false
                            errorMessage = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (!isRegisterMode) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        border = if (isRegisterMode) ButtonDefaults.outlinedButtonBorder else null
                    ) {
                        Text("登录")
                    }

                    OutlinedButton(
                        onClick = {
                            isRegisterMode = true
                            errorMessage = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("注册")
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户名") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示", fontSize = 12.sp)
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isLoading) "处理中..." else if (isRegisterMode) "注册" else "登录")
                }

                TextButton(onClick = onSkip) {
                    Text(
                        text = "跳过登录（离线模式）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
