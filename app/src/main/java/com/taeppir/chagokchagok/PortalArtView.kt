package com.taeppir.chagokchagok

import android.content.Context
import android.graphics.*
import android.view.View

/** Illustrated desktop shortcuts: the approved chrome car and a chrome microphone. */
internal class PortalArtView(context:Context,private val kind:Int):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val brand=if(kind==0) BitmapFactory.decodeResource(resources,R.drawable.chagok_brand) else null
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c:Canvas) {
        val save=c.save();val scale=minOf(width/320f,height/230f)
        c.translate((width-320*scale)/2,(height-230*scale)/2);c.scale(scale,scale)
        if(kind==0) {
            p.color=0xFF7962BC.toInt()
            c.drawPath(Path().apply { moveTo(100f,142f);lineTo(220f,142f);lineTo(282f,218f);lineTo(38f,218f);close() },p)
            p.color=0xFF65F0CB.toInt()
            c.drawPath(Path().apply {moveTo(156f,151f);lineTo(164f,151f);lineTo(167f,174f);lineTo(153f,174f);close()},p)
            c.drawPath(Path().apply {moveTo(151f,186f);lineTo(169f,186f);lineTo(172f,209f);lineTo(148f,209f);close()},p)
            brand?.let { c.drawBitmap(it,null,RectF(10f,6f,310f,183f),p) }
        } else {
            c.rotate(-16f,160f,112f)
            p.shader=LinearGradient(110f,20f,198f,200f,intArrayOf(0xFFFFFFFF.toInt(),0xFFA9AAC5.toInt(),0xFFFFFFFF.toInt(),0xFFB3A5D1.toInt()),null,Shader.TileMode.CLAMP)
            c.drawRoundRect(125f,18f,195f,137f,35f,35f,p)
            p.shader=null;p.color=CockpitTheme.PRIMARY
            for(y in 36..104 step 12) c.drawRoundRect(138f,y.toFloat(),182f,y+4f,2f,2f,p)
            p.style=Paint.Style.STROKE;p.strokeWidth=9f;p.strokeCap=Paint.Cap.ROUND
            c.drawArc(RectF(110f,60f,210f,160f),0f,180f,false,p)
            c.drawLine(160f,160f,160f,194f,p);c.drawLine(130f,196f,190f,196f,p)
            p.style=Paint.Style.FILL
            p.color=CockpitTheme.MINT;c.drawCircle(160f,124f,7f,p)
        }
        c.restoreToCount(save)
    }
}
