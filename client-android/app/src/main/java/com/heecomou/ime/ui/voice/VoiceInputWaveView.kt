package com.heecomou.ime.ui.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class VoiceInputWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var barCount: Int = 5
    var barColor: Int = Color.parseColor("#1976D2")
    var barMinHeight: Float = 4f
    var barMaxHeight: Float = 48f
    var barWidth: Float = 6f
    var barGap: Float = 8f
    var barCornerRadius: Float = 3f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val animator: ValueAnimator
    private val amplitudes = FloatArray(barCount) { Random.nextFloat() }

    private var phase = 0f

    init {
        animator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }
    }

    fun startAnimation() {
        if (!animator.isStarted) {
            animator.start()
        }
    }

    fun stopAnimation() {
        animator.cancel()
        for (i in amplitudes.indices) {
            amplitudes[i] = 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = barColor

        val totalWidth = barCount * barWidth + (barCount - 1) * barGap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f

        for (i in 0 until barCount) {
            val amp = if (amplitudes[i] > 0f) amplitudes[i] else 0.3f
            val heightRatio = 0.5f + 0.5f * sin(phase + i * 0.8f)
            val barHeight = barMinHeight + (barMaxHeight - barMinHeight) * heightRatio * amp

            val left = startX + i * (barWidth + barGap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f

            canvas.drawRoundRect(left, top, right, bottom, barCornerRadius, barCornerRadius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
