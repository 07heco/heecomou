package com.heecomou.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HeecoMouIME : InputMethodService() {

    companion object {
        private const val TAG = "HeecoMouIME"
    }

    private lateinit var statusText: TextView
    private lateinit var voiceButton: Button

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        statusText = TextView(this).apply {
            text = "HeecoMou 已就绪"
            textSize = 14f
        }
        layout.addView(statusText)

        voiceButton = Button(this).apply {
            text = "语音输入"
            setOnClickListener {
                Log.d(TAG, "Voice button clicked")
                onVoiceInputStart()
            }
        }
        layout.addView(voiceButton)

        return layout
    }

    private fun onVoiceInputStart() {
        statusText.text = "正在聆听..."
        Log.d(TAG, "Voice input started")
    }
}