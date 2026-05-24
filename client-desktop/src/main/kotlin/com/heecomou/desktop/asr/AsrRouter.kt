package com.heecomou.desktop.asr

enum class AsrEngineMode(val label: String, val description: String) {
    AUTO("自动", "根据网络和噪声自动选择"),
    CLOUD("云端", "高精度云端识别"),
    LOCAL("端侧", "离线隐私识别")
}

data class AsrPreferences(
    val engineMode: AsrEngineMode = AsrEngineMode.AUTO,
    val dialect: String = "zh",
    val privacyFirst: Boolean = false
)

class AsrRouter {

    data class RoutingDecision(
        val mode: AsrEngineMode,
        val reason: String
    )

    companion object {
        private val DIALECTS = mapOf(
            "zh" to "普通话",
            "yue" to "粤语",
            "wuu" to "上海话",
            "en" to "英语"
        )
    }

    fun getSupportedDialects(): Map<String, String> = DIALECTS

    fun decide(
        preferences: AsrPreferences,
        isNetworkAvailable: Boolean,
        networkRttMs: Long = 0,
        noiseLevel: Int = 0
    ): RoutingDecision {
        if (!isNetworkAvailable) {
            return RoutingDecision(AsrEngineMode.LOCAL, "网络不可用，使用端侧识别")
        }

        if (preferences.engineMode == AsrEngineMode.LOCAL) {
            return RoutingDecision(AsrEngineMode.LOCAL, "手动选择端侧模式")
        }

        if (preferences.engineMode == AsrEngineMode.CLOUD) {
            return RoutingDecision(AsrEngineMode.CLOUD, "手动选择云端模式")
        }

        if (preferences.privacyFirst) {
            return RoutingDecision(AsrEngineMode.LOCAL, "隐私优先模式")
        }

        if (networkRttMs > 500) {
            return RoutingDecision(AsrEngineMode.LOCAL, "网络延迟过高 (${networkRttMs}ms)，使用端侧")
        }

        if (noiseLevel >= 3) {
            return RoutingDecision(AsrEngineMode.CLOUD, "高噪声环境，使用云端高精度识别")
        }

        return RoutingDecision(AsrEngineMode.CLOUD, "默认云端模式（最佳精度）")
    }
}
