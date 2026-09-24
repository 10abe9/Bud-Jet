package com.abe.bud_jet.ui.analytics

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.abe.bud_jet.R
import com.abe.bud_jet.database.models.CategoryStat
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<CategoryStat> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var animatedProgress: Float = 1f
    private var progressAnimator: ValueAnimator? = null

    fun setData(stats: List<CategoryStat>) {
        data = stats
        startFillAnimation()
    }

    private fun startFillAnimation() {
        progressAnimator?.cancel()
        progressAnimator = null

        if (data.isEmpty()) {
            animatedProgress = 1f
            invalidate()
            return
        }

        animatedProgress = 0f
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                animatedProgress = valueAnimator.animatedValue as Float
                invalidate()
            }
        }
        progressAnimator = animator
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
        progressAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Slightly thinner stroke makes ROUND caps look premium (less "pill" feel).
        val strokeWidth = width * 0.17f
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

        val filledAngle = 360f * animatedProgress
        val filledAngleAbsolute = -90f + filledAngle
        var startAngle = -90f
        // Small gap + straight caps (BUTT) keeps segments clean.
        val gap = 1.6f

        data.forEach { item ->
            val sweep = (item.total / total) * 360f
            val drawSweep = (sweep - gap).coerceAtLeast(0f)

            paint.color = runCatching { Color.parseColor(item.colorHex) }.getOrDefault(Color.GRAY)
            paint.strokeCap = Paint.Cap.BUTT

            val segmentStart = startAngle + (gap / 2f)
            val segmentEnd = segmentStart + drawSweep

            if (filledAngleAbsolute <= segmentStart) {
                // No fill reached this segment yet.
            } else {
                val clampedEnd = minOf(segmentEnd, filledAngleAbsolute)
                val partialSweep = (clampedEnd - segmentStart).coerceAtLeast(0f)
                if (partialSweep > 0f) {
                    canvas.drawArc(rect, segmentStart, partialSweep, false, paint)
                }
            }

            startAngle += sweep
        }
    }
}