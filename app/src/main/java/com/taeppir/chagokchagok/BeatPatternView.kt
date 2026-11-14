package com.taeppir.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

internal class BeatPatternView(context: Context, color: Int) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private var kicks = DrumPattern.kickSteps(syncopated = false)

    fun setPattern(arrangement: Arrangement) {
        val pattern = DrumPattern.kickSteps(arrangement)
        if (pattern == kicks) return
        kicks = pattern
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width / 2f
        val stepWidth = barWidth / DrumPattern.STEPS_PER_BAR
        val strokeWidth = min(stepWidth * 0.4f, height * 0.2f)
        val baseline = height * 0.9f

        for (bar in 0..1) {
            for (step in 0 until DrumPattern.STEPS_PER_BAR) {
                val center = bar * barWidth + (step + 0.5f) * stepWidth
                val strength = when {
                    step in kicks -> 0.86f
                    DrumPattern.isBackbeat(step) -> 0.56f
                    else -> 0.25f
                }
                ink.alpha = if (step in kicks) 255 else 150
                canvas.drawRoundRect(
                    center - strokeWidth / 2, baseline - height * strength,
                    center + strokeWidth / 2, baseline,
                    strokeWidth / 2, strokeWidth / 2, ink,
                )
            }
        }
        ink.alpha = 115
        canvas.drawLine(barWidth, height * 0.08f, barWidth, height.toFloat(), ink)
    }
}
