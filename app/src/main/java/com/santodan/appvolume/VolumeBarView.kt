package com.santodan.appvolume

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Small, non-interactive bar used to show a volume fraction inside a list row.
 * The parent row owns focus and key handling so D-pad navigation stays predictable.
 */
class VolumeBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stroke)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            1f,
            0f,
            ContextCompat.getColor(context, R.color.accent),
            ContextCompat.getColor(context, R.color.accent_light),
            Shader.TileMode.CLAMP,
        )
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }

    private var fraction = 0f
    private var active = false

    fun setLevel(fraction: Float, active: Boolean) {
        val safeFraction = fraction.coerceIn(0f, 1f)
        if (this.fraction == safeFraction && this.active == active) return
        this.fraction = safeFraction
        this.active = active
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat().coerceAtLeast(1f),
            0f,
            ContextCompat.getColor(context, R.color.accent),
            ContextCompat.getColor(context, R.color.accent_light),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barHeight = 6f * density
        val radius = barHeight / 2f
        val centerY = height / 2f
        val top = centerY - radius
        val bottom = centerY + radius

        canvas.drawRoundRect(0f, top, width.toFloat(), bottom, radius, radius, trackPaint)

        if (fraction <= 0f) return

        val minimumWidth = barHeight
        val fillRight = max(width * fraction, minimumWidth).coerceAtMost(width.toFloat())
        canvas.drawRoundRect(
            0f,
            top,
            fillRight,
            bottom,
            radius,
            radius,
            if (active) fillPaint else inactivePaint,
        )
    }
}
