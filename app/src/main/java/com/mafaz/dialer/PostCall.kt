package com.mafaz.dialer

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object PostCall {
    private var view: View? = null
    private val h = Handler(Looper.getMainLooper())

    fun dismiss(ctx: Context) {
        h.removeCallbacksAndMessages(null)
        val v = view ?: return
        view = null
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) { }
    }

    fun show(ctx: Context, number: String) {
        if (number.isBlank() || !Settings.canDrawOverlays(ctx)) return
        if (!Ui.cfg(ctx).getBoolean("postcall", true)) return
        dismiss(ctx)
        val MP = ViewGroup.LayoutParams.MATCH_PARENT
        val WC = ViewGroup.LayoutParams.WRAP_CONTENT
        val found = Repo.lookup(ctx, number)
        val person = found ?: Person(number, number)
        val note = Store.note(ctx, number)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.round(Ui.solid(ctx), Ui.dp(ctx, 20))
        card.elevation = Ui.dp(ctx, 12).toFloat()
        card.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 12))

        val top = LinearLayout(ctx)
        top.gravity = Gravity.CENTER_VERTICAL
        val av = AvatarView(ctx)
        av.bind(person.name, person.contactId)
        top.addView(av, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)))
        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        val nm = TextView(ctx)
        nm.text = person.name
        nm.textSize = 17f
        nm.setTypeface(null, Typeface.BOLD)
        nm.setTextColor(Ui.text(ctx))
        nm.setSingleLine()
        val sub = TextView(ctx)
        sub.text = if (note != null) "Call ended  \uD83D\uDCDD " + note else "Call ended"
        sub.textSize = 12f
        sub.setTextColor(Ui.sub(ctx))
        sub.setSingleLine()
        info.addView(nm)
        info.addView(sub)
        val ilp = LinearLayout.LayoutParams(0, WC, 1f)
        ilp.leftMargin = Ui.dp(ctx, 12)
        top.addView(info, ilp)
        val close = TextView(ctx)
        close.text = "\u2715"
        close.textSize = 18f
        close.setTextColor(Ui.sub(ctx))
        close.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4))
        close.setOnClickListener { dismiss(ctx) }
        top.addView(close)
        card.addView(top, LinearLayout.LayoutParams(MP, WC))

        val row = LinearLayout(ctx)
        fun btn(text: String, color: Int, f: () -> Unit) {
            val b = TextView(ctx)
            b.text = text
            b.textSize = 12f
            b.gravity = Gravity.CENTER
            b.setTextColor(Color.WHITE)
            b.setTypeface(null, Typeface.BOLD)
            b.background = Ui.round(color, Ui.dp(ctx, 18))
            b.setOnClickListener { f(); dismiss(ctx) }
            val lp = LinearLayout.LayoutParams(0, Ui.dp(ctx, 40), 1f)
            lp.setMargins(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0)
            row.addView(b, lp)
        }
        btn("Call back", 0xFF2E7D32.toInt()) { Actions.call(ctx, person) }
        btn("Message", 0xFF1976D2.toInt()) { Actions.sms(ctx, person) }
        btn("Note", 0xFF8E24AA.toInt()) { Actions.note(ctx, person) }
        btn("Block", 0xFFC62828.toInt()) { Actions.block(ctx, person) }
        btn("Later", 0xFF6D4C41.toInt()) { Actions.remind(ctx, person) }
        val rlp = LinearLayout.LayoutParams(MP, WC)
        rlp.topMargin = Ui.dp(ctx, 12)
        card.addView(row, rlp)

        val wrap = FrameLayout(ctx)
        wrap.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
        wrap.addView(card, FrameLayout.LayoutParams(MP, WC))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM
        lp.y = Ui.dp(ctx, 64)
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(wrap, lp)
            view = wrap
            h.postDelayed({ dismiss(ctx) }, 9000)
        } catch (e: Exception) { }
    }
}
