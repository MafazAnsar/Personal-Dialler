package com.mafaz.dialer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.min

class AvatarView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bmp: Bitmap? = null
    private var letter = "?"
    private var color = 0xFF607D8B.toInt()
    private val palette = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(),
        0xFF3949AB.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF546E7A.toInt()
    )

    fun bind(name: String, contactId: Long?) {
        val first = name.trim().firstOrNull { it.isLetterOrDigit() }
        letter = first?.uppercaseChar()?.toString() ?: "#"
        color = palette[(name.hashCode() and 0x7fffffff) % palette.size]
        bmp = if (contactId != null) Repo.photo(context, contactId) else null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f
        val b = bmp
        if (b != null) {
            val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix()
            val sc = max(w / b.width, h / b.height)
            m.setScale(sc, sc)
            m.postTranslate((w - b.width * sc) / 2f, (h - b.height * sc) / 2f)
            shader.setLocalMatrix(m)
            paint.shader = shader
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
        } else {
            paint.color = color
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = r * 0.95f
            val y = cy - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letter, cx, y, paint)
        }
    }
}
