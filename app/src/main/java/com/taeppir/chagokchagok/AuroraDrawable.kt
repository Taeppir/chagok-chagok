package com.taeppir.chagokchagok

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Static colour fields keep the parked player readable without continuous motion. */
internal class AuroraDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fields = emptyList<Shader>()

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val radius = maxOf(width, height) * 0.72f
        fields = listOf(
            RadialGradient(width * 0.08f, height * 0.08f, radius, 0xC080ABFF.toInt(), 0x0080ABFF, Shader.TileMode.CLAMP),
            RadialGradient(width * 0.8f, height * 0.25f, radius, 0xB0C39AFF.toInt(), 0x00C39AFF, Shader.TileMode.CLAMP),
            RadialGradient(width * 0.6f, height, radius, 0xB0F5AFD5.toInt(), 0x00F5AFD5, Shader.TileMode.CLAMP),
            RadialGradient(0f, height, radius * 0.8f, 0xB080E9DD.toInt(), 0x0080E9DD, Shader.TileMode.CLAMP),
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFFF2F5FF.toInt())
        fields.forEach { paint.shader = it; canvas.drawRect(bounds, paint) }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Legacy Drawable API")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
