package com.heecomou.ime.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.heecomou.ime.network.ApiClient
import com.heecomou.ime.network.TokenManager
import com.heecomou.ime.ui.login.LoginActivity
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenManager.init(applicationContext)

        if (!TokenManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.userApiService.getMe()
                if (response.code == 200 && response.data != null) {
                    finish()
                    return@launch
                }
            } catch (_: Exception) {
            }
            TokenManager.clear()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
