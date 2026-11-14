package com.taeppir.chagokchagok

import android.graphics.*
import android.graphics.drawable.Drawable

/** Small vector instruments, drawn with one chrome-and-plum material family. */
internal class InstrumentArt(private val kind:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas:Canvas) {
        val save=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        canvas.scale(bounds.width()/120f,bounds.height()/90f)
        paint.shader=LinearGradient(10f,10f,90f,80f,intArrayOf(0xFFFFFFFF.toInt(),0xFFACB7C9.toInt(),0xFFF5F2FF.toInt(),0xFF8F83AA.toInt()),null,Shader.TileMode.CLAMP)
        when(kind) {
            0 -> { // shaker
                canvas.rotate(-24f,60f,45f)
                canvas.drawRoundRect(24f,24f,96f,66f,18f,18f,paint)
                paint.shader=null; paint.color=CockpitTheme.PRIMARY
                canvas.drawRoundRect(47f,24f,73f,66f,6f,6f,paint)
                paint.color=Color.WHITE
                for(x in 54..66 step 6) for(y in 35..55 step 10) canvas.drawCircle(x.toFloat(),y.toFloat(),1.6f,paint)
            }
            1 -> { // bells and synth keys
                canvas.drawRoundRect(12f,18f,108f,76f,12f,12f,paint)
                paint.shader=null; paint.color=CockpitTheme.PRIMARY
                for(i in 0..4) canvas.drawRoundRect(22f+i*16,40f,33f+i*16,68f,3f,3f,paint)
                paint.color=Color.WHITE
                for(i in 0..3) canvas.drawRoundRect(30f+i*16,40f,39f+i*16,54f,2f,2f,paint)
                paint.color=CockpitTheme.PRIMARY; canvas.drawCircle(90f,29f,4f,paint)
            }
            2 -> { // hand-clap pad, paired palms
                canvas.drawRoundRect(14f,16f,106f,78f,18f,18f,paint)
                paint.shader=null; paint.color=CockpitTheme.PRIMARY
                canvas.save(); canvas.rotate(-15f,50f,48f)
                canvas.drawRoundRect(32f,36f,53f,65f,9f,9f,paint)
                for(i in 0..2) canvas.drawRoundRect(32f+i*7,25f,38f+i*7,48f,3f,3f,paint)
                canvas.restore(); canvas.save();canvas.rotate(15f,70f,48f)
                canvas.drawRoundRect(65f,36f,86f,65f,9f,9f,paint)
                for(i in 0..2) canvas.drawRoundRect(65f+i*7,25f,71f+i*7,48f,3f,3f,paint)
                canvas.restore()
            }
            4 -> {
                canvas.drawRoundRect(12f,20f,108f,72f,12f,12f,paint)
                paint.shader=null;paint.color=CockpitTheme.PRIMARY
                canvas.drawRoundRect(24f,28f,96f,56f,5f,5f,paint)
                paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
                val wave=Path().apply {moveTo(28f,43f);cubicTo(38f,12f,44f,72f,56f,43f);cubicTo(68f,12f,75f,72f,92f,43f)}
                canvas.drawPath(wave,paint);paint.style=Paint.Style.FILL;paint.color=CockpitTheme.PRIMARY
                for(x in 28..92 step 16) canvas.drawCircle(x.toFloat(),64f,3f,paint)
            }
            5 -> {
                canvas.drawOval(25f,12f,95f,78f,paint)
                paint.shader=null;paint.color=CockpitTheme.PRIMARY
                canvas.drawOval(35f,22f,85f,68f,paint)
                paint.color=Color.WHITE
                for(i in 0..5) {
                    val angle=i*Math.PI/3
                    canvas.drawOval((60+29*kotlin.math.cos(angle)-6).toFloat(),(45+27*kotlin.math.sin(angle)-4).toFloat(),(60+29*kotlin.math.cos(angle)+6).toFloat(),(45+27*kotlin.math.sin(angle)+4).toFloat(),paint)
                }
            }
            else -> { // snare rim and sticks
                canvas.drawRoundRect(20f,37f,100f,70f,10f,10f,paint)
                canvas.drawOval(20f,22f,100f,53f,paint)
                paint.shader=null; paint.color=CockpitTheme.PRIMARY;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
                canvas.drawOval(23f,25f,97f,50f,paint)
                paint.strokeCap=Paint.Cap.ROUND;canvas.drawLine(24f,12f,80f,45f,paint);canvas.drawLine(99f,11f,55f,47f,paint)
                paint.style=Paint.Style.FILL
            }
        }
        paint.shader=null;canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int) { paint.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter:ColorFilter?) { paint.colorFilter=filter;invalidateSelf() }
    @Deprecated("Legacy Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
