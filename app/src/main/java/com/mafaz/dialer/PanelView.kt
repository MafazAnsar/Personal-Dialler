package com.mafaz.dialer

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class PanelView(
    ctx: Context,
    private val leftSide: Boolean,
    private val onClose: () -> Unit
) : FrameLayout(ctx), DragHost {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val actions = ArrayList<Triple<TextView, Int, (Person) -> Unit>>()
    private val actionsCol = LinearLayout(ctx)
    private val pane = ContactsPane(ctx, this) { onClose() }

    private var dragPerson: Person? = null
    private var chip: TextView? = null
    private var hover: TextView? = null
    private var lastX = 0f
    private var lastY = 0f
    private var closing = false

    override val dragging: Boolean get() = dragPerson != null

    init {
        setBackgroundColor(Ui.scrim(ctx))
        isFocusableInTouchMode = true
        setOnClickListener { onClose() }

        actionsCol.orientation = LinearLayout.VERTICAL
        actionsCol.gravity = Gravity.CENTER_VERTICAL
        actionsCol.isClickable = true
        actionsCol.setPadding(dp(8), 0, dp(8), 0)
        pill("\uD83D\uDCDE  Call", 0xFF2E7D32.toInt()) { p -> Actions.call(context, p) }
        pill("\uD83D\uDCAC  SMS", 0xFF1976D2.toInt()) { p -> Actions.sms(context, p) }
        pill("WhatsApp", 0xFF1FA855.toInt()) { p -> Actions.whatsapp(context, p) }
        pill("\u2708  Telegram", 0xFF229ED9.toInt()) { p -> Actions.telegram(context, p) }
        pill("\u2709  Email", 0xFFEF6C00.toInt()) { p -> Actions.email(context, p) }
        pill("\uD83D\uDCDD  Note", 0xFF8E24AA.toInt()) { p -> Actions.note(context, p) }
        pill("\uD83D\uDEAB  Block", 0xFFC62828.toInt()) { p -> Actions.block(context, p) }

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), dp(40), dp(8), dp(24))
        val cw = 1.2f + 0.1f * Ui.cfg(ctx).getInt("panelw", 3)
        val cLp = LinearLayout.LayoutParams(0, MP, cw)
        val gLp = LinearLayout.LayoutParams(0, MP, 0.2f)
        val aLp = LinearLayout.LayoutParams(0, MP, 1.0f)
        val gap = View(ctx)
        if (leftSide) {
            row.addView(pane, cLp); row.addView(gap, gLp); row.addView(actionsCol, aLp)
        } else {
            row.addView(actionsCol, aLp); row.addView(gap, gLp); row.addView(pane, cLp)
        }
        addView(row, FrameLayout.LayoutParams(MP, MP))
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun pill(label: String, color: Int, fn: (Person) -> Unit) {
        val tv = TextView(context)
        tv.text = label
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.setTypeface(null, Typeface.BOLD)
        tv.background = Ui.round(color, dp(28))
        tv.alpha = 0.88f
        val lp = LinearLayout.LayoutParams(MP, dp(52))
        lp.setMargins(0, dp(5), 0, dp(5))
        actionsCol.addView(tv, lp)
        actions.add(Triple(tv, color, fn))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
        val w = dp(320).toFloat()
        pane.translationX = if (leftSide) -w else w
        pane.animate().translationX(0f).setDuration(240).start()
        actionsCol.translationX = if (leftSide) w else -w
        actionsCol.animate().translationX(0f).setDuration(240).setStartDelay(40).start()
    }

    fun dismiss(done: () -> Unit) {
        if (closing) return
        closing = true
        animate().alpha(0f).setDuration(150).withEndAction { done() }.start()
    }

    override fun beginDrag(p: Person, x: Float, y: Float) {
        dragPerson = p
        lastX = x
        lastY = y
        val c = TextView(context)
        c.text = p.name
        c.setTextColor(Color.BLACK)
        c.textSize = 16f
        c.setPadding(dp(16), dp(10), dp(16), dp(10))
        c.background = Ui.round(Ui.accent(context), dp(24))
        c.elevation = dp(8).toFloat()
        c.scaleX = 0.6f
        c.scaleY = 0.6f
        addView(c, FrameLayout.LayoutParams(WC, WC))
        chip = c
        c.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        c.post { moveChip() }
    }

    override fun dragTo(x: Float, y: Float) {
        lastX = x
        lastY = y
        moveChip()
    }

    private fun moveChip() {
        val c = chip ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        c.x = lastX - loc[0] - c.width / 2f
        c.y = lastY - loc[1] - c.height - dp(24)
        var over: TextView? = null
        val r = Rect()
        val l = IntArray(2)
        for (a in actions) {
            val tv = a.first
            tv.getLocationOnScreen(l)
            r.set(l[0], l[1], l[0] + tv.width, l[1] + tv.height)
            if (r.contains(lastX.toInt(), lastY.toInt())) over = tv
        }
        if (over !== hover) {
            hover?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(0.88f)?.setDuration(100)?.start()
            over?.animate()?.scaleX(1.1f)?.scaleY(1.1f)?.alpha(1f)?.setDuration(100)?.start()
            hover = over
        }
    }

    override fun endDrag(drop: Boolean) {
        val p = dragPerson
        val target = hover
        val c = chip
        dragPerson = null
        chip = null
        hover = null
        if (c != null) {
            c.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(140)
                .withEndAction { removeView(c) }.start()
        }
        for (a in actions) {
            a.first.animate().scaleX(1f).scaleY(1f).alpha(0.88f).setDuration(100).start()
        }
        if (drop && p != null && target != null) {
            val fn = actions.first { it.first === target }.third
            fn(p)
            onClose()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
