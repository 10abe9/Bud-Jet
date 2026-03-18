package com.abe.bud_jet.ui.analytics

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
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

        if (data.isEmpty()) return

        val total = data.sumOf { it.total.toDouble() }.toFloat()

        val strokeWidth = width * 0.18f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND

        val padding = strokeWidth
        rect.set(
            padding,
            padding,
            width - padding,
            height - padding
        )

        var startAngle = -90f

        data.forEachIndexed { index, item ->
            val sweep = (item.total / total) * 360f

            paint.color = colors[index % colors.size]

            canvas.drawArc(rect, startAngle, sweep, false, paint)

            startAngle += sweep
        }
    }
}