package tk.horiuchi.smarttuner.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class NeedleView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // cents: -50 .. +50
    var cents: Float = 0f
        set(v) { field = v.coerceIn(-50f, 50f); invalidate() }

    var inTune: Boolean = false
        set(v) { field = v; invalidate() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w/2f; val cy = h*0.85f; val radius = h*0.8f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景アーチ
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 10f
        paint.color = Color.RED
        c.drawArc(RectF(cx-radius, cy-radius, cx+radius, cy+radius), 200f, 140f, false, paint)
        paint.color = Color.GREEN
        c.drawArc(RectF(cx-radius, cy-radius, cx+radius, cy+radius), 260f, 20f, false, paint)

        // 目盛り
        paint.color = Color.GRAY; paint.strokeWidth = 3f
        for (i in -50..50 step 10) {
            val a = mapCentsToAngle(i.toFloat())
            val sx = cx + (radius-20f)*cos(a); val sy = cy + (radius-20f)*sin(a)
            val ex = cx + radius*cos(a);      val ey = cy + radius*sin(a)
            c.drawLine(sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat(), paint)
        }

        // 針
        val ang = mapCentsToAngle(cents)
        paint.color = Color.BLACK; paint.strokeWidth = 12f
        val ex = cx + (radius-30f)*cos(ang); val ey = cy + (radius-30f)*sin(ang)
        c.drawLine(cx, cy, ex.toFloat(), ey.toFloat(), paint)
        paint.style = Paint.Style.FILL
        paint.color = if (inTune) Color.argb(60, 0, 255, 0) else Color.TRANSPARENT
        c.drawCircle(cx, cy, 28f, paint)
    }

    private fun mapCentsToAngle(cents: Float): Double {
        // -50c -> 200deg, +50c -> 340deg（半円アーチ）
        val deg = 200.0 + (cents + 50f) * (140.0 / 100.0)
        return Math.toRadians(deg)
    }
}
