package com.mafaz.dialer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

private const val CALL_D = "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"

object Svg {
    private val tok = Regex("[a-zA-Z]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")

    fun parse(d: String): Path {
        val p = Path()
        val t = tok.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = ' '
        var x = 0f
        var y = 0f
        var sx = 0f
        var sy = 0f
        fun num(): Float = t[i++].toFloat()
        while (i < t.size) {
            if (t[i][0].isLetter()) {
                cmd = t[i][0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    p.close()
                    x = sx
                    y = sy
                    continue
                }
            }
            when (cmd) {
                'M' -> { x = num(); y = num(); p.moveTo(x, y); sx = x; sy = y; cmd = 'L' }
                'm' -> { x += num(); y += num(); p.moveTo(x, y); sx = x; sy = y; cmd = 'l' }
                'L' -> { x = num(); y = num(); p.lineTo(x, y) }
                'l' -> { x += num(); y += num(); p.lineTo(x, y) }
                'H' -> { x = num(); p.lineTo(x, y) }
                'h' -> { x += num(); p.lineTo(x, y) }
                'V' -> { y = num(); p.lineTo(x, y) }
                'v' -> { y += num(); p.lineTo(x, y) }
                'C' -> {
                    val x1 = num(); val y1 = num(); val x2 = num(); val y2 = num()
                    val x3 = num(); val y3 = num()
                    p.cubicTo(x1, y1, x2, y2, x3, y3)
                    x = x3; y = y3
                }
                'c' -> {
                    val x1 = x + num(); val y1 = y + num(); val x2 = x + num(); val y2 = y + num()
                    val x3 = x + num(); val y3 = y + num()
                    p.cubicTo(x1, y1, x2, y2, x3, y3)
                    x = x3; y = y3
                }
                else -> return p
            }
        }
        return p
    }
}

class IconView(ctx: Context, var kind: String) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val white = 0xFFFFFFFF.toInt()
    private val callPath: Path by lazy { Svg.parse(CALL_D) }

    var hot = false
        set(v) {
            field = v
            invalidate()
        }
    var on = false
        set(v) {
            field = v
            invalidate()
        }
    var bgOverride: Int? = null
        set(v) {
            field = v
            invalidate()
        }
    var glyphColor: Int? = null
        set(v) {
            field = v
            invalidate()
        }
    var forceCircle = false
        set(v) {
            field = v
            invalidate()
        }

    private val circleKinds = setOf("wa", "tg", "block", "signal", "viber")
    private val toggleKinds = setOf("torch", "bt", "wifi", "sound", "vib", "data", "dnd")
    private val headerKinds = setOf("keypad", "sort")

    private fun ink(): Int = glyphColor ?: white

    private fun brand(): Int = when (kind) {
        "call" -> 0xFF2EA84F.toInt()
        "sms" -> 0xFF1E88E5.toInt()
        "wa" -> 0xFF25D366.toInt()
        "tg" -> 0xFF2AABEE.toInt()
        "mail" -> 0xFFEA4335.toInt()
        "note" -> 0xFFFBC02D.toInt()
        "block" -> 0xFFD32F2F.toInt()
        "signal" -> 0xFF3A76F0.toInt()
        "viber" -> 0xFF7360F2.toInt()
        "history" -> 0xFF546E7A.toInt()
        "remind" -> 0xFFEF6C00.toInt()
        "addcontact" -> 0xFF00897B.toInt()
        "fav" -> 0xFFFFB300.toInt()
        else -> 0xFF607D8B.toInt()
    }

    private fun bgColor(): Int {
        val o = bgOverride
        if (o != null) return o
        return when {
            kind in toggleKinds -> if (on) Ui.accent(context) else 0xFF3A4650.toInt()
            kind in headerKinds -> Ui.card(context)
            else -> brand()
        }
    }

    private fun stroke(w: Float, color: Int = ink()) {
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = w
        fg.strokeCap = Paint.Cap.ROUND
        fg.strokeJoin = Paint.Join.ROUND
        fg.color = color
    }

    private fun fill(color: Int = ink()) {
        fg.style = Paint.Style.FILL
        fg.color = color
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        canvas.scale(s / 100f, s / 100f)
        drawBg(canvas)
        drawGlyph(canvas)
        canvas.restore()
    }

    private fun isCircle(): Boolean = if (bgOverride != null) forceCircle else kind in circleKinds

    private fun drawBg(c: Canvas) {
        bg.style = Paint.Style.FILL
        bg.color = bgColor()
        rect.set(0f, 0f, 100f, 100f)
        if (isCircle()) c.drawCircle(50f, 50f, 50f, bg) else c.drawRoundRect(rect, 22f, 22f, bg)
        if (hot) {
            bg.style = Paint.Style.STROKE
            bg.strokeWidth = 6f
            bg.color = white
            rect.set(3f, 3f, 97f, 97f)
            if (isCircle()) c.drawCircle(50f, 50f, 47f, bg) else c.drawRoundRect(rect, 20f, 20f, bg)
        }
    }

    private fun drawCall(c: Canvas, rot: Float) {
        c.save()
        if (rot != 0f) c.rotate(rot, 50f, 50f)
        c.translate(20f, 20f)
        c.scale(60f / 24f, 60f / 24f)
        fill()
        c.drawPath(callPath, fg)
        c.restore()
    }

    private fun drawGlyph(c: Canvas) {
        when (kind) {
            "addcontact" -> {
                fill()
                c.drawCircle(42f, 36f, 13f, fg)
                rect.set(18f, 56f, 66f, 80f)
                c.drawRoundRect(rect, 12f, 12f, fg)
                stroke(6f)
                c.drawLine(76f, 28f, 76f, 52f, fg)
                c.drawLine(64f, 40f, 88f, 40f, fg)
            }
            "fav" -> {
                fill()
                path.reset()
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 31.0 else 13.0
                    val a = Math.toRadians(-90.0 + k * 36.0)
                    val x = (50.0 + r * Math.cos(a)).toFloat()
                    val y = (53.0 + r * Math.sin(a)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, fg)
            }
            "call" -> drawCall(c, 0f)
            "hang" -> drawCall(c, 135f)
            "mic", "micoff" -> {
                fill()
                rect.set(40f, 20f, 60f, 56f)
                c.drawRoundRect(rect, 10f, 10f, fg)
                stroke(6f)
                rect.set(31f, 34f, 69f, 70f)
                c.drawArc(rect, 0f, 180f, false, fg)
                c.drawLine(50f, 70f, 50f, 80f, fg)
                c.drawLine(40f, 80f, 60f, 80f, fg)
                if (kind == "micoff") c.drawLine(28f, 24f, 72f, 78f, fg)
            }
            "pause" -> {
                fill()
                rect.set(33f, 26f, 46f, 74f)
                c.drawRoundRect(rect, 4f, 4f, fg)
                rect.set(54f, 26f, 67f, 74f)
                c.drawRoundRect(rect, 4f, 4f, fg)
            }
            "plus" -> {
                stroke(8f)
                c.drawLine(50f, 27f, 50f, 73f, fg)
                c.drawLine(27f, 50f, 73f, 50f, fg)
            }
            "cnote" -> {
                stroke(6f)
                rect.set(31f, 22f, 69f, 78f)
                c.drawRoundRect(rect, 7f, 7f, fg)
                for (y in intArrayOf(38, 50, 62)) c.drawLine(40f, y.toFloat(), 60f, y.toFloat(), fg)
            }
            "phone" -> {
                stroke(6f)
                rect.set(36f, 18f, 64f, 82f)
                c.drawRoundRect(rect, 8f, 8f, fg)
                c.drawLine(45f, 70f, 55f, 70f, fg)
            }
            "head" -> {
                stroke(7f)
                rect.set(28f, 24f, 72f, 70f)
                c.drawArc(rect, 180f, 180f, false, fg)
                fill()
                rect.set(25f, 46f, 39f, 72f)
                c.drawRoundRect(rect, 5f, 5f, fg)
                rect.set(61f, 46f, 75f, 72f)
                c.drawRoundRect(rect, 5f, 5f, fg)
            }
            "gear" -> {
                stroke(7f)
                c.drawCircle(50f, 50f, 22f, fg)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val cs = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    c.drawLine(50f + 26f * cs, 50f + 26f * sn, 50f + 36f * cs, 50f + 36f * sn, fg)
                }
                fill()
                c.drawCircle(50f, 50f, 8f, fg)
            }
            "signal" -> {
                stroke(6f)
                c.drawCircle(50f, 48f, 26f, fg)
                fill()
                path.reset()
                path.moveTo(24f, 78f)
                path.lineTo(30f, 62f)
                path.lineTo(42f, 72f)
                path.close()
                c.drawPath(path, fg)
                for (x in intArrayOf(40, 50, 60)) c.drawCircle(x.toFloat(), 48f, 3.5f, fg)
            }
            "viber" -> {
                fill()
                rect.set(24f, 24f, 76f, 62f)
                c.drawRoundRect(rect, 14f, 14f, fg)
                path.reset()
                path.moveTo(36f, 60f)
                path.lineTo(36f, 78f)
                path.lineTo(52f, 62f)
                path.close()
                c.drawPath(path, fg)
                stroke(5f, brand())
                path.reset()
                path.moveTo(40f, 34f)
                path.quadTo(40f, 52f, 60f, 52f)
                c.drawPath(path, fg)
            }
            "history" -> {
                stroke(7f)
                c.drawCircle(50f, 50f, 26f, fg)
                c.drawLine(50f, 50f, 50f, 33f, fg)
                c.drawLine(50f, 50f, 63f, 58f, fg)
            }
            "remind" -> {
                fill()
                c.drawCircle(50f, 44f, 17f, fg)
                rect.set(30f, 50f, 70f, 66f)
                c.drawRoundRect(rect, 4f, 4f, fg)
                c.drawCircle(50f, 74f, 5f, fg)
                stroke(5f)
                c.drawLine(50f, 20f, 50f, 27f, fg)
            }
            "data" -> {
                fill()
                rect.set(28f, 62f, 40f, 76f)
                c.drawRoundRect(rect, 3f, 3f, fg)
                rect.set(44f, 48f, 56f, 76f)
                c.drawRoundRect(rect, 3f, 3f, fg)
                rect.set(60f, 32f, 72f, 76f)
                c.drawRoundRect(rect, 3f, 3f, fg)
            }
            "dnd" -> {
                fill()
                c.drawCircle(46f, 50f, 24f, fg)
                fill(bgColor())
                c.drawCircle(58f, 42f, 20f, fg)
            }
            "sms" -> {
                fill()
                rect.set(20f, 24f, 80f, 62f)
                c.drawRoundRect(rect, 10f, 10f, fg)
                path.reset()
                path.moveTo(32f, 60f)
                path.lineTo(32f, 78f)
                path.lineTo(50f, 60f)
                path.close()
                c.drawPath(path, fg)
                fill(brand())
                for (x in intArrayOf(36, 50, 64)) c.drawCircle(x.toFloat(), 43f, 4.5f, fg)
            }
            "wa" -> {
                stroke(6f)
                c.drawCircle(50f, 47f, 28f, fg)
                fill()
                path.reset()
                path.moveTo(22f, 80f)
                path.lineTo(28f, 60f)
                path.lineTo(42f, 72f)
                path.close()
                c.drawPath(path, fg)
                stroke(5f, brand())
                path.reset()
                path.moveTo(40f, 37f)
                path.quadTo(40f, 58f, 61f, 58f)
                c.drawPath(path, fg)
                fill(brand())
                c.drawCircle(40f, 36f, 4f, fg)
                c.drawCircle(62f, 58f, 4f, fg)
            }
            "tg" -> {
                fill()
                path.reset()
                path.moveTo(22f, 47f)
                path.lineTo(78f, 26f)
                path.lineTo(64f, 76f)
                path.lineTo(47f, 62f)
                path.lineTo(38f, 71f)
                path.lineTo(36f, 56f)
                path.close()
                c.drawPath(path, fg)
                stroke(3f, brand())
                path.reset()
                path.moveTo(36f, 56f)
                path.lineTo(68f, 36f)
                c.drawPath(path, fg)
            }
            "mail" -> {
                fill()
                rect.set(20f, 30f, 80f, 70f)
                c.drawRoundRect(rect, 6f, 6f, fg)
                stroke(5f, brand())
                path.reset()
                path.moveTo(22f, 33f)
                path.lineTo(50f, 54f)
                path.lineTo(78f, 33f)
                c.drawPath(path, fg)
            }
            "note" -> {
                fill()
                rect.set(28f, 20f, 72f, 80f)
                c.drawRoundRect(rect, 6f, 6f, fg)
                stroke(4f, 0xFF8D6E63.toInt())
                for (y in intArrayOf(36, 48, 60)) c.drawLine(36f, y.toFloat(), 64f, y.toFloat(), fg)
            }
            "block" -> {
                stroke(8f)
                c.drawCircle(50f, 50f, 25f, fg)
                c.drawLine(33f, 33f, 67f, 67f, fg)
            }
            "torch" -> {
                fill()
                path.reset()
                path.moveTo(36f, 20f)
                path.lineTo(64f, 20f)
                path.lineTo(58f, 40f)
                path.lineTo(42f, 40f)
                path.close()
                c.drawPath(path, fg)
                rect.set(42f, 42f, 58f, 80f)
                c.drawRoundRect(rect, 5f, 5f, fg)
            }
            "bt" -> {
                stroke(7f)
                path.reset()
                path.moveTo(34f, 34f)
                path.lineTo(66f, 66f)
                path.lineTo(50f, 80f)
                path.lineTo(50f, 20f)
                path.lineTo(66f, 34f)
                path.lineTo(34f, 66f)
                c.drawPath(path, fg)
            }
            "wifi" -> {
                stroke(7f)
                for (r in floatArrayOf(14f, 28f, 42f)) {
                    rect.set(50f - r, 72f - r, 50f + r, 72f + r)
                    c.drawArc(rect, 225f, 90f, false, fg)
                }
                fill()
                c.drawCircle(50f, 72f, 5f, fg)
            }
            "sound", "vib" -> {
                fill()
                path.reset()
                path.moveTo(24f, 40f)
                path.lineTo(38f, 40f)
                path.lineTo(54f, 26f)
                path.lineTo(54f, 74f)
                path.lineTo(38f, 60f)
                path.lineTo(24f, 60f)
                path.close()
                c.drawPath(path, fg)
                stroke(6f)
                if (kind == "sound") {
                    rect.set(40f, 36f, 68f, 64f)
                    c.drawArc(rect, -50f, 100f, false, fg)
                    rect.set(32f, 28f, 76f, 72f)
                    c.drawArc(rect, -50f, 100f, false, fg)
                } else {
                    c.drawLine(64f, 40f, 80f, 60f, fg)
                    c.drawLine(80f, 40f, 64f, 60f, fg)
                }
            }
            "keypad" -> {
                fill(glyphColor ?: Ui.text(context))
                for (x in intArrayOf(30, 50, 70)) {
                    for (y in intArrayOf(30, 50, 70)) c.drawCircle(x.toFloat(), y.toFloat(), 6f, fg)
                }
            }
            "sort" -> {
                stroke(7f, Ui.text(context))
                c.drawLine(34f, 74f, 34f, 26f, fg)
                path.reset()
                path.moveTo(22f, 38f)
                path.lineTo(34f, 26f)
                path.lineTo(46f, 38f)
                c.drawPath(path, fg)
                c.drawLine(66f, 26f, 66f, 74f, fg)
                path.reset()
                path.moveTo(54f, 62f)
                path.lineTo(66f, 74f)
                path.lineTo(78f, 62f)
                c.drawPath(path, fg)
            }
        }
    }
}
