package com.mafaz.dialer

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Themed building blocks for popups and dialogs, shared by the panel and the activities.
object Cards {
    private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(c: Context, v: Int) = Ui.dp(c, v)

    fun card(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        val g = Ui.round(Ui.solid(ctx), dp(ctx, if (Ui.square(ctx)) 8 else 16))
        g.setStroke(dp(ctx, 1), Ui.withAlpha(Ui.accent(ctx), 0x55))
        l.background = g
        l.setPadding(dp(ctx, 6), dp(ctx, 8), dp(ctx, 6), dp(ctx, 8))
        l.elevation = dp(ctx, 12).toFloat()
        l.isClickable = true
        return l
    }

    fun title(ctx: Context, text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Ui.accent(ctx))
        t.setSingleLine()
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 6))
        return t
    }

    fun item(ctx: Context, text: String, danger: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 14f
        t.setTextColor(if (danger) 0xFFE57373.toInt() else Ui.text(ctx))
        t.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        val sl = StateListDrawable()
        sl.addState(
            intArrayOf(android.R.attr.state_pressed),
            Ui.round(Ui.withAlpha(Ui.accent(ctx), 0x44), dp(ctx, 8))
        )
        sl.addState(intArrayOf(), ColorDrawable(0))
        t.background = sl
        t.setOnClickListener { onClick() }
        return t
    }

    fun button(ctx: Context, text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 14f
        t.setTextColor(if (primary) Ui.accent(ctx) else Ui.sub(ctx))
        if (primary) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
        t.setOnClickListener { onClick() }
        return t
    }

    fun menu(ctx: Context, title: String?, labels: List<String>, dangerIdx: Int, onPick: (Int) -> Unit): LinearLayout {
        val c = card(ctx)
        if (title != null) c.addView(title(ctx, title))
        for ((i, l) in labels.withIndex()) {
            c.addView(item(ctx, l, i == dangerIdx) { onPick(i) })
        }
        return c
    }

    fun input(
        ctx: Context, title: String, initial: String, hint: String,
        onSave: (String) -> Unit, onCancel: () -> Unit
    ): LinearLayout {
        val c = card(ctx)
        c.addView(title(ctx, title))
        val et = EditText(ctx)
        et.setText(initial)
        et.hint = hint
        et.setTextColor(Ui.text(ctx))
        et.setHintTextColor(Ui.sub(ctx))
        et.textSize = 15f
        et.background = Ui.round(Ui.card(ctx), dp(ctx, 8))
        et.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.setMargins(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 8))
        c.addView(et, lp)
        val row = LinearLayout(ctx)
        row.gravity = Gravity.END
        row.addView(button(ctx, "Cancel", false) { onCancel() })
        row.addView(button(ctx, "Save", true) { onSave(et.text.toString()) })
        c.addView(row, LinearLayout.LayoutParams(MP, WC))
        return c
    }

    fun rows(ctx: Context, title: String, rows: List<String>): LinearLayout {
        val c = card(ctx)
        c.addView(title(ctx, title))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for (r in rows) {
            val t = TextView(ctx)
            t.text = r
            t.textSize = 13f
            t.setTextColor(Ui.text(ctx))
            t.setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
            col.addView(t)
        }
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        val h = if (rows.size > 8) dp(ctx, 260) else WC
        c.addView(sv, LinearLayout.LayoutParams(MP, h))
        return c
    }
}
