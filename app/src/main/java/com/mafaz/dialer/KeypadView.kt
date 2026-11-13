package com.mafaz.dialer

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class KeypadView(
    ctx: Context,
    private val showCall: Boolean = true,
    private val onCall: (String) -> Unit = {},
    private val onDigit: ((Char) -> Unit)? = null,
    private val onChanged: ((String) -> Unit)? = null
) : LinearLayout(ctx) {

    var contacts: List<Person> = emptyList()
    private val display = TextView(ctx)
    private val match = TextView(ctx)
    private val sb = StringBuilder()
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private var ready = false

    val number: String get() = sb.toString()

    init {
        orientation = LinearLayout.VERTICAL
        display.textSize = 26f
        display.gravity = Gravity.CENTER
        display.setSingleLine()
        display.setTextColor(Ui.text(ctx))
        match.textSize = 13f
        match.gravity = Gravity.CENTER
        match.setSingleLine()
        match.setTextColor(Ui.accent(ctx))
        addView(display, LinearLayout.LayoutParams(MP, WC))
        addView(match, LinearLayout.LayoutParams(MP, WC))

        val rows = arrayOf(
            arrayOf("1" to "", "2" to "ABC", "3" to "DEF"),
            arrayOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            arrayOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            arrayOf("*" to "", "0" to "+", "#" to "")
        )
        for (r in rows) {
            val row = LinearLayout(ctx)
            for ((d, l) in r) row.addView(key(d, l), cell())
            addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }

        val last = LinearLayout(ctx)
        last.addView(View(ctx), cell())
        if (showCall) {
            val call = TextView(ctx)
            call.text = "\uD83D\uDCDE"
            call.textSize = 24f
            call.gravity = Gravity.CENTER
            call.background = Ui.key(ctx, 0xFF2E9E4F.toInt())
            call.setOnClickListener { onCall(sb.toString()) }
            last.addView(call, cell())
        } else {
            last.addView(View(ctx), cell())
        }
        val back = TextView(ctx)
        back.text = "\u232B"
        back.textSize = 24f
        back.gravity = Gravity.CENTER
        back.setTextColor(Ui.sub(ctx))
        back.setOnClickListener {
            if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            render()
        }
        back.setOnLongClickListener { sb.setLength(0); render(); true }
        last.addView(back, cell())
        addView(last, LinearLayout.LayoutParams(MP, 0, 1f))
        render()
        ready = true
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun cell(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, MP, 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        return lp
    }

    private fun key(d: String, l: String): TextView {
        val t = TextView(context)
        t.text = if (l.isEmpty()) d else d + "\n" + l
        t.textSize = 19f
        t.gravity = Gravity.CENTER
        t.setTextColor(Ui.text(context))
        t.background = Ui.key(context, Ui.card(context))
        t.setOnClickListener { press(d[0]) }
        if (d == "0" && showCall) t.setOnLongClickListener { press('+'); true }
        return t
    }

    private fun press(ch: Char) {
        onDigit?.invoke(ch)
        sb.append(ch)
        render()
    }

    fun setNumber(s: String) {
        sb.setLength(0)
        sb.append(s)
        render()
    }

    private fun render() {
        display.text = sb.toString()
        if (ready) onChanged?.invoke(sb.toString())
        val qd = sb.toString().filter { it.isDigit() }
        if (qd.length < 2) {
            match.text = ""
            return
        }
        val hit = contacts.firstOrNull {
            it.number.filter { c -> c.isDigit() }.contains(qd) || T9.digits(it.name).contains(qd)
        }
        match.text = hit?.name ?: ""
    }
}
