package com.mhk.filemanager.ui.japcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarEntry(val label: String, val value: Long, val dateStr: String)

    private var entries = listOf<BarEntry>()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    var barColor: Int = 0xFF8DCDFF.toInt()
        set(value) {
            field = value
            barPaint.color = value
            invalidate()
        }

    var labelColor: Int = 0xFF888888.toInt()
        set(value) {
            field = value
            labelPaint.color = value
            gridPaint.color = value
            invalidate()
        }

    var valueColor: Int = 0xFF333333.toInt()
        set(value) {
            field = value
            valuePaint.color = value
            invalidate()
        }

    init {
        barPaint.color = barColor
        labelPaint.color = labelColor
        valuePaint.color = valueColor
        gridPaint.color = labelColor
    }

    fun setData(data: List<BarEntry>) {
        entries = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (entries.isEmpty()) return

        val paddingLeft = 8f
        val paddingRight = 8f
        val paddingTop = 24f
        val paddingBottom = 40f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val maxValue = entries.maxOfOrNull { it.value } ?: 0L
        val safeMax = if (maxValue > 0) maxValue.toFloat() else 1f

        val barCount = entries.size
        val totalGap = chartWidth * 0.2f
        val gap = if (barCount > 1) totalGap / (barCount - 1) else 0f
        val barWidth = (chartWidth - totalGap) / barCount

        for ((index, entry) in entries.withIndex()) {
            val barHeight = (entry.value.toFloat() / safeMax) * chartHeight
            val left = paddingLeft + index * (barWidth + gap)
            val top = paddingTop + chartHeight - barHeight
            val right = left + barWidth
            val bottom = paddingTop + chartHeight

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 6f, 6f, barPaint)

            // Value on top of bar
            if (entry.value > 0) {
                canvas.drawText(entry.value.toString(), (left + right) / 2, top - 6f, valuePaint)
            }

            // Label below bar
            canvas.drawText(entry.label, (left + right) / 2, height - 12f, labelPaint)
        }

        // Baseline
        canvas.drawLine(paddingLeft, paddingTop + chartHeight, width - paddingRight, paddingTop + chartHeight, gridPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = 220
        setMeasuredDimension(width, height)
    }
}
