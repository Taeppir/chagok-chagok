package com.taeppir.chagokchagok

import android.graphics.*
import android.graphics.drawable.Drawable

/** Restrained frosted surface. Text remains sharp and no decorative border is drawn. */
internal class GlassDrawable(private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        paint.shader = LinearGradient(0f, 0f, rect.width(), rect.height(),
            intArrayOf(0xF5FFFFFF.toInt(), 0xD9FCF8FF.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Legacy Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
