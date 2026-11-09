package ua.any2b.tvplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** Small halftone meter: dot spacing follows the buffered media percentage. */
class HalftoneBufferIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var percentage = 0
    private val dotPaint = Paint().apply {
        color = 0xFF37E6EF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC7D0D8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.35f
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    fun setBufferPercentage(value: Int) {
        val next = value.coerceIn(0, 100)
        if (percentage == next) return
        percentage = next
        contentDescription = "Заповнення буфера: $next відсотків"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val bracketInsetPx = (density * 1.5f).roundToInt().coerceAtLeast(1)
        val gridInsetXPx = (density * 4f).roundToInt().coerceAtLeast(1)
        val gridInsetYPx = (density * 1.5f).roundToInt().coerceAtLeast(1)
        val dotDiameterPx = density.roundToInt().coerceAtLeast(1)
        val minSpacingPx = (density * 1f).roundToInt().coerceAtLeast(1)
        val maxSpacingPx = (density * 5f).roundToInt().coerceAtLeast(minSpacingPx)
        val currentSpacingPx = (
            maxSpacingPx - (maxSpacingPx - minSpacingPx) * (percentage / 100f)
        ).roundToInt().coerceIn(minSpacingPx, maxSpacingPx)
        val stepPx = dotDiameterPx + currentSpacingPx
        val radiusPx = dotDiameterPx / 2f
        val left = gridInsetXPx + dotDiameterPx / 2
        val top = gridInsetYPx + dotDiameterPx / 2
        val right = width - gridInsetXPx - (dotDiameterPx - 1) / 2
        val bottom = height - gridInsetYPx - (dotDiameterPx - 1) / 2

        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                // Integer pixel coordinates keep the grid sharp on TV panels.
                canvas.drawCircle(x.toFloat(), y.toFloat(), radiusPx, dotPaint)
                x += stepPx
            }
            y += stepPx
        }

        val bracketLeft = bracketInsetPx.toFloat()
        val bracketRight = width - bracketInsetPx.toFloat()
        val bracketTop = density.toInt().coerceAtLeast(1).toFloat()
        val bracketBottom = height - bracketTop
        val hook = density * 2.1f
        val brackets = Path().apply {
            moveTo(bracketLeft + hook, bracketTop)
            lineTo(bracketLeft, bracketTop)
            lineTo(bracketLeft, bracketBottom)
            lineTo(bracketLeft + hook, bracketBottom)
            moveTo(bracketRight - hook, bracketTop)
            lineTo(bracketRight, bracketTop)
            lineTo(bracketRight, bracketBottom)
            lineTo(bracketRight - hook, bracketBottom)
        }
        canvas.drawPath(brackets, bracketPaint)
    }
}
