package com.darkred1145.geohud

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.color.MaterialColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Determinate download bar drawn as a moving sine wave. The completed part is
 * solid in the theme accent, the rest a faint track.
 */
class WaveProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val strokeWidth = 3f * density
    private val amplitude = 3.5f * density
    private val wavelength = 26f * density
    private var phase = 0f

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidth
        color = MaterialColors.getColor(this@WaveProgressView, androidx.appcompat.R.attr.colorPrimary)
    }

    private val trackPaint = Paint(wavePaint).apply {
        alpha = 60
    }

    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val centerY = height / 2f
        val waveHeight = amplitude.coerceAtMost((height - strokeWidth) / 2f)

        path.reset()
        path.moveTo(0f, centerY)
        var x = 0f
        while (x <= width) {
            val y = centerY +
                sin(x / wavelength * 2 * PI + phase).toFloat() * waveHeight
            path.lineTo(x, y)
            x += 3f * density
        }

        canvas.drawPath(path, trackPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width * progress, height.toFloat())
        canvas.drawPath(path, wavePaint)
        canvas.restore()
    }
}
