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
        paint.shader = LinearGradient(0f,0f,0f,h,
            intArrayOf(0xFFAB92FA.toInt(),0xFFE1DAFF.toInt(),0xFFFFFFFF.toInt(),0xFFB4F7E7.toInt()),
            floatArrayOf(0f,.28f,.72f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(bounds,paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Legacy Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
}
