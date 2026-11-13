package com.mafaz.dialer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

class IconView(ctx: Context, var kind: String) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val white = 0xFFFFFFFF.toInt()

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

    private val circleKinds = setOf("wa", "tg", "block")
    private val toggleKinds = setOf("torch", "bt", "wifi", "sound", "vib")
    private val headerKinds = setOf("keypad", "sort")

    private fun brand(): Int = when (kind) {
        "call" -> 0xFF2EA84F.toInt()
        "sms" -> 0xFF1E88E5.toInt()
        "wa" -> 0xFF25D366.toInt()
        "tg" -> 0xFF2AABEE.toInt()
        "mail" -> 0xFFEA4335.toInt()
        "note" -> 0xFFFBC02D.toInt()
        "block" -> 0xFFD32F2F.toInt()
        else -> 0xFF607D8B.toInt()
    }

    private fun bgColor(): Int = when {
        kind in toggleKinds -> if (on) Ui.accent(context) else 0xFF3A4650.toInt()
        kind in headerKinds -> Ui.card(context)
        else -> brand()
    }

    private fun stroke(w: Float, color: Int = white) {
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = w
        fg.strokeCap = Paint.Cap.ROUND
        fg.strokeJoin = Paint.Join.ROUND
        fg.color = color
    }

    private fun fill(color: Int = white) {
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

    private fun drawBg(c: Canvas) {
        bg.style = Paint.Style.FILL
        bg.color = bgColor()
        rect.set(0f, 0f, 100f, 100f)
        if (kind in circleKinds) c.drawCircle(50f, 50f, 50f, bg) else c.drawRoundRect(rect, 22f, 22f, bg)
        if (hot) {
            bg.style = Paint.Style.STROKE
            bg.strokeWidth = 6f
            bg.color = white
            rect.set(3f, 3f, 97f, 97f)
            if (kind in circleKinds) c.drawCircle(50f, 50f, 47f, bg) else c.drawRoundRect(rect, 20f, 20f, bg)
        }
    }

    private fun drawGlyph(c: Canvas) {
        when (kind) {
            "call" -> {
                stroke(10f)
                path.reset()
                path.moveTo(34f, 34f)
                path.quadTo(32f, 68f, 66f, 66f)
                c.drawPath(path, fg)
                fill()
                rect.set(24f, 22f, 46f, 38f)
                c.drawRoundRect(rect, 6f, 6f, fg)
                rect.set(62f, 60f, 78f, 78f)
                c.drawRoundRect(rect, 6f, 6f, fg)
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
                fill(Ui.text(context))
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
