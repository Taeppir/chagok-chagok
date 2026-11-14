package com.taeppir.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

internal class AudioPulseView(context: Context, private val player: BackingTrack) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levels = FloatArray(48)
    private var artwork: Bitmap? = null
    private var energy = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        artwork = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { renderArtwork(Canvas(it), w.toFloat(), h.toFloat()) }
    }

    private fun renderArtwork(canvas: Canvas, w: Float, h: Float) {
        val save = canvas.save()
        val scale = min(w / 760f, h / 650f)
        canvas.translate(w / 2f, h * 0.43f)
        canvas.scale(scale, scale)
        paint.shader = RadialGradient(0f, 150f, 330f, 0x245E78A7, 0x005E78A7, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.3f)
        canvas.drawCircle(0f, 500f, 330f, paint)
        canvas.restore()
        val palettes = arrayOf(
            intArrayOf(0xFF6787D4.toInt(), 0xFF526DB5.toInt(), 0xFF3254A0.toInt()),
            intArrayOf(0xFFB5C2EF.toInt(), 0xFF939AD9.toInt(), 0xFF6F83BF.toInt()),
            intArrayOf(0xFFF4FCFF.toInt(), 0xFFBDDCF2.toInt(), 0xFF8CADD5.toInt()),
        )
        for (layer in 0..2) {
            val y = 100f - layer * 83f
            val points = floatArrayOf(-265f,y-60f, 45f,y-160f, 270f,y-18f, -40f,y+88f)
            val matrix = Matrix().apply { setPolyToPoly(floatArrayOf(0f,0f,500f,0f,500f,300f,0f,300f),0,points,0,4) }
            val surface = Path().apply { addRoundRect(RectF(0f,0f,500f,300f),38f,38f,Path.Direction.CW); transform(matrix) }
            val lower = Path(surface).apply { offset(0f, 28f) }
            paint.shader = LinearGradient(-240f,y,270f,y+90f,palettes[layer][1],palettes[layer][2],Shader.TileMode.CLAMP)
            paint.setShadowLayer(22f, 0f, 17f, 0x24536B91)
            canvas.drawPath(lower,paint)
            paint.clearShadowLayer()
            paint.shader = LinearGradient(-200f,y-130f,160f,y+60f,palettes[layer][0],palettes[layer][1],Shader.TileMode.CLAMP)
            canvas.drawPath(surface,paint)
            val shineSave=canvas.save()
            canvas.clipPath(surface)
            paint.shader=RadialGradient(-80f,y-125f,320f,0xB3FFFFFF.toInt(),0x00FFFFFF,Shader.TileMode.CLAMP)
            canvas.drawRect(-300f,y-180f,300f,y+120f,paint)
            canvas.restoreToCount(shineSave)
            if(layer==2) {
                val symbolSave=canvas.save()
                canvas.concat(matrix)
                paint.shader=null
                paint.color=0xE62B5AC2.toInt()
                for(i in 0..2) {
                    val yy=90f+i*44f
                    val ribbon=Path().apply { moveTo(158f,yy); quadTo(245f,yy-34f,342f,yy); lineTo(342f,yy+17f); quadTo(245f,yy-17f,158f,yy+17f); close() }
                    canvas.drawPath(ribbon,paint)
                }
                canvas.restoreToCount(symbolSave)
            }
        }
        paint.shader=null
        canvas.restoreToCount(save)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val animate = ValueAnimator.areAnimatorsEnabled()
        val target = player.visualLevels()
        for (i in levels.indices) levels[i] = if (!player.active) 0f else if (animate) levels[i]*0.35f+target[i]*0.65f else target[i]
        energy=if (!player.active || !animate) 0f else energy*0.7f+target[24]*0.3f
        val save=canvas.save()
        canvas.scale(1f+energy*0.012f,1f+energy*0.012f,width/2f,height*0.42f)
        paint.color=Color.WHITE
        artwork?.let { canvas.drawBitmap(it,0f,0f,paint) }
        canvas.restoreToCount(save)
        val step=width*0.76f/levels.size
        val baseline=height*0.88f
        for(i in levels.indices) {
            val x=width*0.12f+i*step
            val bar=height*(0.006f+levels[i]*0.105f)
            paint.color=if(i in 22..25) CockpitTheme.BLUE else 0xFF8096BC.toInt()
            canvas.drawRoundRect(x,baseline-bar/2,x+step*0.38f,baseline+bar/2,step,step,paint)
        }
        if(player.active && isShown && animate) postInvalidateDelayed(40)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView,visibility)
        if(visibility==VISIBLE) invalidate()
    }
}
