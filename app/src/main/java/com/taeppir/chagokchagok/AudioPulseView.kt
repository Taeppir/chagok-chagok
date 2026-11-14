package com.taeppir.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** Cached CD artwork; its face rotates independently of the fixed perspective and shadow. */
internal class AudioPulseView(
    context: Context,
    private val isPlaying: () -> Boolean,
    private val visualLevels: () -> FloatArray,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val levels = FloatArray(48)
    private var artwork: Bitmap? = null
    private val innerBevel = ring(37f, 29f)
    private val bevelShader = LinearGradient(-90f, -90f, 90f, 90f,
        0xFF94A8C9.toInt(), 0xDFFFFFFF.toInt(), Shader.TileMode.CLAMP)
    private val face = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { drawFace(Canvas(it)) }
    private var angle = 0f
    private var lastFrame = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        artwork = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            drawBase(Canvas(it), w.toFloat(), h.toFloat())
        }
    }

    private fun ring(outer: Float, inner: Float, y: Float = 0f) = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addCircle(0f, y, outer, Path.Direction.CW)
        addCircle(0f, y, inner, Path.Direction.CW)
    }

    private fun drawFace(canvas: Canvas) {
        canvas.translate(256f, 256f)
        val shape = ring(226f, 29f)
        paint.shader = SweepGradient(0f, 0f, intArrayOf(
            0xFFB7C3F3.toInt(), 0xFFEBC2E2.toInt(), 0xFFB3A4ED.toInt(),
            0xFF7FB6EC.toInt(), 0xFF94E3D5.toInt(), 0xFFDDEEAC.toInt(), 0xFFB7C3F3.toInt()), null)
        canvas.drawPath(shape, paint)
        val reflection = canvas.save()
        canvas.clipPath(shape)
        paint.shader = LinearGradient(-180f, -180f, 190f, 190f,
            intArrayOf(0x00FFFFFF, 0xD9FFFFFF.toInt(), 0x10FFFFFF, 0x70FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.3f, 0.43f, 0.68f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-240f, -240f, 240f, 240f, paint)
        paint.shader = RadialGradient(-100f, -140f, 200f, 0xA0FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(-100f, -140f, 200f, paint)
        canvas.restoreToCount(reflection)
        paint.shader = LinearGradient(-64f, -64f, 64f, 64f,
            intArrayOf(0xCCF8FCFF.toInt(), 0xBBACBBD9.toInt(), 0xDDEAF3F6.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(ring(58f, 29f), paint)
        paint.shader = null
    }

    private fun drawBase(canvas: Canvas, w: Float, h: Float) {
        val save = canvas.save()
        val scale = min(w / 760f, h / 650f)
        canvas.translate(w / 2f, h * 0.40f)
        canvas.scale(scale, scale)
        paint.shader = RadialGradient(0f, 0f, 270f, 0x3544578E, 0x0044578E, Shader.TileMode.CLAMP)
        val shadow = canvas.save()
        canvas.translate(0f, 232f)
        canvas.scale(1f, 0.18f)
        canvas.drawCircle(0f, 0f, 270f, paint)
        canvas.restoreToCount(shadow)
        canvas.rotate(-18f)
        canvas.scale(1f, 0.96f)
        paint.shader = LinearGradient(-210f, -160f, 210f, 200f,
            intArrayOf(0xFF5B91D1.toInt(), 0xFF89BCDB.toInt(), 0xFF8B74B9.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(ring(226f, 29f, 5f), paint)
        paint.shader = null
        canvas.restoreToCount(save)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val animate = ValueAnimator.areAnimatorsEnabled()
        val playing = isPlaying()
        val target = visualLevels()
        for (i in levels.indices) levels[i] = if (!playing) 0f else if (animate) levels[i] * 0.35f + target[i] * 0.65f else target[i]
        val now = SystemClock.uptimeMillis()
        if (playing && animate && isShown) {
            if (lastFrame != 0L) angle = (angle + min(now - lastFrame, 100L) * 0.025f) % 360f
            lastFrame = now
        } else lastFrame = 0L
        paint.color = Color.WHITE
        artwork?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        val save = canvas.save()
        val scale = min(width / 760f, height / 650f)
        canvas.translate(width / 2f, height * 0.40f)
        canvas.scale(scale, scale)
        canvas.rotate(-18f)
        canvas.scale(1f, 0.96f)
        val rotation = canvas.save()
        canvas.rotate(angle)
        canvas.drawBitmap(face, -256f, -256f, paint)
        canvas.restoreToCount(rotation)
        // Fixed inner bevel preserves the apparent light direction while the face spins.
        paint.shader = bevelShader
        canvas.drawPath(innerBevel, paint)
        paint.shader = null
        canvas.restoreToCount(save)
        val step = width * 0.76f / levels.size
        val baseline = height * 0.88f
        if (playing) for (i in levels.indices) {
            val x = width * 0.12f + i * step
            val bar = height * (0.006f + levels[i] * 0.105f)
            paint.color = when {
                i in 22..25 -> CockpitTheme.PRIMARY
                i < 16 -> 0xFF4EAFA6.toInt()
                i < 32 -> 0xFF8495D4.toInt()
                else -> 0xFFCA8CB4.toInt()
            }
            canvas.drawRoundRect(x, baseline-bar/2, x+step*0.38f, baseline+bar/2, step, step, paint)
        }
        if (playing && isShown && animate) postInvalidateDelayed(40)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        lastFrame = 0L
        if (visibility == VISIBLE) invalidate()
    }
}
