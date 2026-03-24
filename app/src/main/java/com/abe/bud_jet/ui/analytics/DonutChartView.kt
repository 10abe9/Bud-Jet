package com.abe.bud_jet.ui.analytics

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.abe.bud_jet.R
import com.abe.bud_jet.database.models.CategoryStat

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<CategoryStat> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val colors = listOf(
        Color.parseColor("#FF9800"),
        Color.parseColor("#2196F3"),
        Color.parseColor("#E91E63"),
        Color.parseColor("#4CAF50"),
        Color.parseColor("#9C27B0"),
        Color.parseColor("#FFC107")
    )

    fun setData(stats: List<CategoryStat>) {
        data = stats
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeWidth = width * 0.18f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.BUTT

        val padding = strokeWidth
        rect.set(
            padding,
            padding,
            width - padding,
            height - padding
        )

        // Subtle base ring keeps the chart informative in empty states.
        paint.color = ContextCompat.getColor(context, R.color.border)
        canvas.drawArc(rect, -90f, 360f, false, paint)

        if (data.isEmpty()) return

        val total = data.sumOf { it.total.toDouble() }.toFloat()
        if (total <= 0f) return

        var startAngle = -90f
        val gap = 2.5f

        data.forEachIndexed { index, item ->
            val sweep = (item.total / total) * 360f
            val drawSweep = (sweep - gap).coerceAtLeast(0f)

            paint.color = colors[index % colors.size]
            paint.strokeCap = Paint.Cap.ROUND

            canvas.drawArc(rect, startAngle + (gap / 2f), drawSweep, false, paint)

            startAngle += sweep
        }
    }
}