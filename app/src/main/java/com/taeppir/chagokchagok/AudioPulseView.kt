package com.taeppir.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** The bars follow RMS values from the PCM that AudioTrack is actually playing. */
internal class AudioPulseView(context: Context, private val player: BackingTrack) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow = 0f
    private val levels = FloatArray(48)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val animate = ValueAnimator.areAnimatorsEnabled()
        val target = player.visualLevels()
        for (i in levels.indices) levels[i] = if (!player.active) 0f else if (animate) levels[i] * 0.35f + target[i] * 0.65f else target[i]
        glow = if (!player.active) 0f else if (animate) glow * 0.6f + target[24] * 0.4f else 0f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 36f, 36f, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xFF456CDE.toInt(), 0xFF8D5AC9.toInt(), 0xFFF5A8CE.toInt(), 0xFF9DE5DC.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        val cx = w * 0.5f
        val cy = h * 0.39f
        val radius = min(w * 0.32f, h * 0.31f) * (1f + glow * 0.055f)
        paint.shader = RadialGradient(cx, cy, radius * 1.5f, 0x80FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.5f, paint)
        paint.shader = SweepGradient(cx, cy, intArrayOf(0xE6D8FCF4.toInt(), 0xD9B9BDFF.toInt(),
            0xF2FFE0F0.toInt(), 0xC7B4DEFF.toInt(), 0xE6D8FCF4.toInt()), null)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.color = 0x42FFFFFF
        canvas.drawCircle(cx, cy, radius * 0.76f, paint)
        paint.color = 0x553E387C
        canvas.drawCircle(cx, cy, radius * 0.31f, paint)
        paint.color = 0xEFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, radius * 0.11f, paint)
        val lane = Path().apply { moveTo(-w * 0.1f, h * 0.63f); cubicTo(w * 0.28f, h * 0.18f, w * 0.72f, h * 0.8f, w * 1.1f, h * 0.3f) }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.015f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xA6FFFFFF.toInt()
        canvas.drawPath(lane, paint)
        paint.style = Paint.Style.FILL
        val step = w * 0.84f / levels.size
        val bottom = h * 0.86f
        for (i in levels.indices) {
            val x = w * 0.08f + i * step
            val bar = h * (0.012f + levels[i] * 0.16f)
            paint.color = if (i in 22..25) 0xFF273967.toInt() else 0xEFFFFFFF.toInt()
            canvas.drawRoundRect(x, bottom - bar / 2, x + step * 0.55f, bottom + bar / 2, step, step, paint)
        }
        canvas.restoreToCount(save)
        if (player.active && isShown && animate) postInvalidateDelayed(40)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
