package com.heecomou.ime.asr

enum class AsrEngineMode(val label: String, val description: String) {
    AUTO("自动", "根据网络和噪声自动选择"),
    CLOUD("云端", "高精度云端识别"),
    LOCAL("端侧", "离线隐私识别")
}
