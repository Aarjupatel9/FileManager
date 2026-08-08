package com.mhk.filemanager.ui.japcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MalaProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val totalDots = 108
    private var completedDots = 0

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dotRadius = 6f
    private val dotSpacing = 4f

    var progress: Int = 0
        set(value) {
            completedDots = value.coerceIn(0, totalDots)
            invalidate()
        }

    var activeColor: Int = 0xFF8DCDFF.toInt()
        set(value) {
            field = value
            activePaint.color = value
            invalidate()
        }

    var inactiveColor: Int = 0x44FFFFFF
        set(value) {
            field = value
            inactivePaint.color = value
            invalidate()
        }

    init {
        activePaint.color = activeColor
        inactivePaint.color = inactiveColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - dotRadius - 4f

        for (i in 0 until totalDots) {
            val angle = (i.toFloat() / totalDots) * 2 * Math.PI - Math.PI / 2
            val x = cx + (radius * Math.cos(angle)).toFloat()
            val y = cy + (radius * Math.sin(angle)).toFloat()

            if (i < completedDots) {
                canvas.drawCircle(x, y, dotRadius, activePaint)
            } else {
                canvas.drawCircle(x, y, dotRadius, inactivePaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val resolvedSize = if (size > 0) size else 200
        setMeasuredDimension(resolvedSize, resolvedSize)
    }
}
