package com.heecomou.ime.model

data class CorrectionFeedback(
    val originalText: String,
    val correctedText: String,
    val source: String = "manual"
)
