package com.heecomou.ime.ui.voice

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

enum class VoiceInputState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    RESULT,
    ERROR
}

class VoiceInputPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val waveView: VoiceInputWaveView
    private val statusText: TextView
    private val resultText: TextView

    var onVoiceStart: (() -> Unit)? = null
    var onVoiceStop: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(24, 16, 24, 16)

        waveView = VoiceInputWaveView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                96
            )
        }
        addView(waveView)

        statusText = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            text = "点击开始语音输入"
        }
        addView(statusText)

        resultText = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            textSize = 16f
            setTextColor(Color.BLACK)
            visibility = GONE
            gravity = Gravity.CENTER
        }
        addView(resultText)
    }

    fun setResultText(text: String) {
        post {
            resultText.text = text
            resultText.visibility = VISIBLE
        }
    }

    fun setState(state: VoiceInputState, message: String? = null) {
        post {
            when (state) {
                VoiceInputState.IDLE -> {
                    waveView.stopAnimation()
                    statusText.text = "点击开始语音输入"
                    statusText.setTextColor(Color.parseColor("#757575"))
                    resultText.visibility = GONE
                }
                VoiceInputState.LISTENING -> {
                    waveView.startAnimation()
                    statusText.text = "正在聆听..."
                    statusText.setTextColor(Color.parseColor("#1976D2"))
                    resultText.visibility = GONE
                }
                VoiceInputState.RECOGNIZING -> {
                    waveView.stopAnimation()
                    statusText.text = "识别中..."
                    statusText.setTextColor(Color.parseColor("#FF5722"))
                    resultText.visibility = GONE
                }
                VoiceInputState.RESULT -> {
                    waveView.stopAnimation()
                    statusText.text = "识别完成"
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                    if (!message.isNullOrEmpty()) {
                        resultText.text = message
                        resultText.visibility = VISIBLE
                    }
                }
                VoiceInputState.ERROR -> {
                    waveView.stopAnimation()
                    statusText.text = message ?: "识别失败"
                    statusText.setTextColor(Color.parseColor("#D32F2F"))
                    resultText.visibility = GONE
                }
            }
        }
    }
}
