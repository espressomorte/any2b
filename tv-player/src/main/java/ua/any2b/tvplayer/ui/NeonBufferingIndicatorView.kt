package ua.any2b.tvplayer.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.ui.graphics.toArgb

/** Compact cyan-cyberpunk spinner for buffering over video. */
class NeonBufferingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val ringBounds = RectF()
    private val ringColor = AppColors.NeonAqua.toArgb()
    private val glowColor = AppColors.NeonAqua.copy(alpha = 0.75f).toArgb()
    private val coreColor = AppColors.NeonCyan.toArgb()
    private var angle = 0f
    private var rotation: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
        color = ringColor
        alpha = 42
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5f * density
        color = glowColor
        setShadowLayer(12f * density, 0f, 0f, glowColor)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f * density
        color = coreColor
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) * 0.34f
        ringBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        canvas.drawArc(ringBounds, 0f, 360f, false, trackPaint)
        canvas.drawArc(ringBounds, angle, 245f, false, glowPaint)
        canvas.drawArc(ringBounds, angle, 245f, false, corePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        rotation?.cancel()
        rotation = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        if (isShown) {
            if (rotation?.isRunning == true) return
            rotation = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    angle = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            rotation?.cancel()
            rotation = null
        }
    }
}
