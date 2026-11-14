package com.taeppir.chagokchagok

import android.graphics.*
import android.graphics.drawable.Drawable

/** Frosted colour and broad light reflections, without borders or blurred text. */
internal class GlassDrawable(private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)
        paint.shader = LinearGradient(0f, 0f, rect.width(), rect.height(),
            intArrayOf(0xE6FFFFFF.toInt(), 0xBFF7FAFF.toInt(), 0xB3FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.shader = RadialGradient(rect.width() * 0.12f, 0f, rect.width() * 0.65f,
            0xB3FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Legacy Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
