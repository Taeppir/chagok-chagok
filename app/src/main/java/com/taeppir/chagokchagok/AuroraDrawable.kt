package com.taeppir.chagokchagok

import android.graphics.*
import android.graphics.drawable.Drawable

/** Quiet, cool daylight; colour is concentrated in the album and active controls. */
internal class AuroraDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        canvas.drawColor(CockpitTheme.BACKGROUND)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return
        paint.shader = RadialGradient(w * 0.8f, h * 0.35f, maxOf(w, h) * 0.65f,
            intArrayOf(0x99DAE8FA.toInt(), 0x00EAF1FF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Legacy Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
}
