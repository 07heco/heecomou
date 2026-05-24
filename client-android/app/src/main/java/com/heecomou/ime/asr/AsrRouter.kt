package com.heecomou.ime.asr

class AsrRouter(
    private val noiseDetector: NoiseDetector = NoiseDetector()
) {
    data class RoutingConfig(
        val batteryLevel: Float = 100f,
        val isOnline: Boolean = true,
        val networkType: String = "unknown",
        val signalStrength: Float = 1.0f,
        val isSensitiveContext: Boolean = false,
        val language: String = "zh"
    )

    data class RoutingDecision(
        val engine: AsrEngineMode,
        val reason: String
    )

    fun decide(config: RoutingConfig): RoutingDecision {
        if (!config.isOnline) {
            return RoutingDecision(AsrEngineMode.LOCAL, "离线状态 - 强制端侧")
        }

        if (config.batteryLevel <= 0.15f) {
            return RoutingDecision(AsrEngineMode.LOCAL, "低电量 (${(config.batteryLevel * 100).toInt()}%) - 优先端侧")
        }

        if (config.networkType == "cellular" && config.signalStrength < 0.5f) {
            return RoutingDecision(AsrEngineMode.LOCAL, "弱蜂窝信号 - 优先端侧")
        }

        if (config.networkType == "wifi" && config.signalStrength < 0.3f) {
            return RoutingDecision(AsrEngineMode.LOCAL, "弱WiFi信号 - 优先端侧")
        }

        if (config.isSensitiveContext) {
            return RoutingDecision(AsrEngineMode.LOCAL, "敏感上下文 - 优先端侧")
        }

        return RoutingDecision(AsrEngineMode.CLOUD, "默认云端")
    }

    fun resolveLocalFallback(clientAvailable: Boolean): AsrEngineMode {
        return if (clientAvailable) AsrEngineMode.LOCAL else AsrEngineMode.CLOUD
    }
}
