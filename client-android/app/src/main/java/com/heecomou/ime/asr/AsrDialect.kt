package com.heecomou.ime.asr

enum class AsrDialect(val code: String, val label: String, val supportedByLocal: Boolean) {
    MANDARIN("zh", "普通话", true),
    CANTONESE("yue", "粤语", false),
    SHANGHAINESE("wuu", "上海话", false),
    ENGLISH("en", "英语", true);

    companion object {
        fun fromCode(code: String): AsrDialect {
            return entries.find { it.code == code } ?: MANDARIN
        }
    }
}
