package com.heecomou.ime.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.heecomou.ime.R
import com.heecomou.ime.model.LoginRequest
import com.heecomou.ime.network.ApiClient
import com.heecomou.ime.network.TokenManager
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        TokenManager.init(applicationContext)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        val btnLogin = findViewById<View>(R.id.btnLogin)
        val progressBar = findViewById<View>(R.id.progressBar)
        val tvError = findViewById<View>(R.id.tvError)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()) {
                etUsername.error = "请输入用户名"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "请输入密码"
                return@setOnClickListener
            }

            tvError.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.login(
                        LoginRequest(username, password)
                    )
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    if (response.code == 200 && response.data != null) {
                        val data = response.data!!
                        TokenManager.saveTokens(data.accessToken, data.refreshToken)
                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        tvError.visibility = View.VISIBLE
                        (tvError as android.widget.TextView).text = response.message
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvError.visibility = View.VISIBLE
                    (tvError as android.widget.TextView).text = "网络请求失败: ${e.message}"
                }
            }
        }
    }
}
