package ua.any2b.tvplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** Twenty crisp ticks show buffer fill between square brackets. */
class BufferTickIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var percentage = 0
    private val filledPaint = Paint().apply {
        color = 0xFF1CDDE2.toInt()
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    private val emptyPaint = Paint().apply {
        color = 0xFF0D5B70.toInt()
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
        val tickCount = 20
        val tickWidthPx = (density * 2f).roundToInt().coerceAtLeast(2)
        val gapPx = density.roundToInt().coerceAtLeast(1)
        val tickHeightPx = (density * 8f).roundToInt().coerceAtLeast(2)
        val leftPx = (density * 4f).roundToInt().coerceAtLeast(1)
        val topPx = ((height - tickHeightPx) / 2).coerceAtLeast(0)
        val filledCount = (tickCount * percentage / 100f).roundToInt().coerceIn(0, tickCount)

        for (index in 0 until tickCount) {
            val left = leftPx + index * (tickWidthPx + gapPx)
            val top = topPx
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                (left + tickWidthPx).toFloat(),
                (top + tickHeightPx).toFloat(),
                if (index < filledCount) filledPaint else emptyPaint,
            )
        }

        val inset = (density * 1.5f).roundToInt().coerceAtLeast(1).toFloat()
        val hook = (density * 2.1f).roundToInt().coerceAtLeast(1).toFloat()
        val bracketTop = density.roundToInt().coerceAtLeast(1).toFloat()
        val bracketBottom = height - bracketTop
        val right = width - inset
        val brackets = Path().apply {
            moveTo(inset + hook, bracketTop)
            lineTo(inset, bracketTop)
            lineTo(inset, bracketBottom)
            lineTo(inset + hook, bracketBottom)
            moveTo(right - hook, bracketTop)
            lineTo(right, bracketTop)
            lineTo(right, bracketBottom)
            lineTo(right - hook, bracketBottom)
        }
        canvas.drawPath(brackets, bracketPaint)
    }
}
