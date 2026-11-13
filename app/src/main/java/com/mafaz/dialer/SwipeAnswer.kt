package com.mafaz.dialer

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeAnswer(
    ctx: Context,
    private val onAnswer: () -> Unit,
    private val onDecline: () -> Unit
) : FrameLayout(ctx) {

    private val green = 0xFF2ECC71.toInt()
    private val red = 0xFFE53935.toInt()
    private val ink = 0xFF101418.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val left = IconView(ctx, "hang")
    private val right = IconView(ctx, "call")
    val handle = IconView(ctx, "call")
    private var startX = 0f
    private var dragging = false
    private var fired = false
    private var ticked = false

    init {
        val sq = Ui.square(ctx)
        background = Ui.round(0x26FFFFFF, dp(if (sq) 14 else 44))
        val icon = dp(44)
        left.bgOverride = 0x66E53935
        left.glyphColor = Color.WHITE
        left.forceCircle = !sq
        right.bgOverride = 0x662ECC71
        right.glyphColor = Color.WHITE
        right.forceCircle = !sq
        val llp = FrameLayout.LayoutParams(icon, icon, Gravity.CENTER_VERTICAL or Gravity.START)
        llp.leftMargin = dp(14)
        addView(left, llp)
        val rlp = FrameLayout.LayoutParams(icon, icon, Gravity.CENTER_VERTICAL or Gravity.END)
        rlp.rightMargin = dp(14)
        addView(right, rlp)
        handle.bgOverride = white
        handle.glyphColor = ink
        handle.forceCircle = !sq
        addView(handle, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun travel(): Float = (width - handle.width) / 2f - dp(10)

    fun reset() {
        fired = false
        dragging = false
        handle.animate().cancel()
        handle.translationX = 0f
        paint(0f)
    }

    private fun paint(p: Float) {
        val a = abs(p)
        if (a < 0.03f) {
            handle.bgOverride = white
            handle.glyphColor = ink
            handle.kind = "call"
            return
        }
        handle.bgOverride = Ui.mix(white, if (p > 0f) green else red, a)
        handle.glyphColor = if (a > 0.5f) Color.WHITE else ink
        handle.kind = if (p < 0f) "hang" else "call"
    }

    private fun nudge() {
        val d = dp(26).toFloat()
        handle.animate().translationX(d).setDuration(120).withEndAction {
            handle.animate().translationX(-d).setDuration(240).withEndAction {
                handle.animate().translationX(0f).setDuration(120).start()
            }.start()
        }.start()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hx = handle.left + handle.translationX
                val onHandle = e.x >= hx - dp(14) && e.x <= hx + handle.width + dp(14)
                if (!onHandle) {
                    nudge()
                    return true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                handle.animate().cancel()
                startX = e.x - handle.translationX
                dragging = true
                fired = false
                ticked = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging || fired) return true
                val t = travel()
                if (t <= 0f) return true
                val dx = (e.x - startX).coerceIn(-t, t)
                handle.translationX = dx
                val p = dx / t
                paint(p)
                if (abs(p) >= 0.85f) {
                    fired = true
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (p > 0f) onAnswer() else onDecline()
                } else if (abs(p) >= 0.5f && !ticked) {
                    ticked = true
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && !fired) {
                    handle.animate().translationX(0f).setDuration(180).start()
                    paint(0f)
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
