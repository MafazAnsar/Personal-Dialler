package com.mafaz.dialer

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class PanelView(
    ctx: Context,
    private val leftSide: Boolean,
    private val cc: String,
    private val onClose: () -> Unit
) : FrameLayout(ctx) {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ACTION_BG = 0xFF37474F.toInt()
    private val ACTION_HOT = 0xFF00897B.toInt()

    private val ui = Handler(Looper.getMainLooper())
    private var recents: List<Person> = emptyList()
    private var all: List<Person> = emptyList()
    private var shown: List<Person> = emptyList()
    private var tab = 0
    private var query = ""

    private val list = ListView(ctx)
    private val empty = TextView(ctx)
    private val tabs = arrayOf(TextView(ctx), TextView(ctx))
    private val actions = ArrayList<Pair<TextView, (Person) -> Unit>>()

    private var dragPerson: Person? = null
    private var chip: TextView? = null
    private var hover: TextView? = null
    private var lastX = 0f
    private var lastY = 0f

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                addView(TextView(context).apply {
                    setTextColor(Color.WHITE); textSize = 16f
                    setTypeface(null, Typeface.BOLD); setSingleLine()
                })
                addView(TextView(context).apply {
                    setTextColor(0xAAFFFFFF.toInt()); textSize = 12f; setSingleLine()
                })
            }
            val item = shown[position]
            (row.getChildAt(0) as TextView).text = item.name
            (row.getChildAt(1) as TextView).text = item.number
            return row
        }
    }

    init {
        setBackgroundColor(0xB3000000.toInt())
        isFocusableInTouchMode = true
        setOnClickListener { onClose() }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(40), dp(8), dp(24))
        }
        val contactsCol = buildContactsColumn()
        val actionsCol = buildActionsColumn()
        val gap = View(ctx)
        val cLp = LinearLayout.LayoutParams(0, MP, 1.5f)
        val gLp = LinearLayout.LayoutParams(0, MP, 0.25f)
        val aLp = LinearLayout.LayoutParams(0, MP, 1.0f)
        if (leftSide) {
            row.addView(contactsCol, cLp); row.addView(gap, gLp); row.addView(actionsCol, aLp)
        } else {
            row.addView(actionsCol, aLp); row.addView(gap, gLp); row.addView(contactsCol, cLp)
        }
        addView(row, FrameLayout.LayoutParams(MP, MP))
        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun buildContactsColumn(): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = round(0xF2212121.toInt(), 16)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val search = EditText(context).apply {
            hint = "Search name or number"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
            textSize = 15f
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    refresh()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        col.addView(search, LinearLayout.LayoutParams(MP, WC))

        val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = arrayOf("Recent", "Contacts")
        for (i in 0..1) {
            tabs[i].apply {
                text = labels[i]
                gravity = Gravity.CENTER
                textSize = 15f
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { tab = i; refresh() }
            }
            tabRow.addView(tabs[i], LinearLayout.LayoutParams(0, WC, 1f))
        }
        col.addView(tabRow, LinearLayout.LayoutParams(MP, WC))

        empty.apply {
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(24), dp(8), dp(8))
        }
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(0x33FFFFFF))
        list.adapter = adapter
        list.emptyView = empty
        list.setOnItemClickListener { _, _, _, _ ->
            Toast.makeText(context, "Hold a contact, then drag it onto an action", Toast.LENGTH_SHORT).show()
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices) {
                list.parent.requestDisallowInterceptTouchEvent(true)
                startDrag(shown[pos])
                true
            } else false
        }
        list.setOnTouchListener { _, e ->
            lastX = e.rawX
            lastY = e.rawY
            if (dragPerson == null) {
                false
            } else {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> moveDrag()
                    MotionEvent.ACTION_UP -> endDrag(true)
                    MotionEvent.ACTION_CANCEL -> endDrag(false)
                }
                true
            }
        }
        val frame = FrameLayout(context)
        frame.addView(list, FrameLayout.LayoutParams(MP, MP))
        frame.addView(empty, FrameLayout.LayoutParams(MP, WC))
        col.addView(frame, LinearLayout.LayoutParams(MP, 0, 1f))
        return col
    }

    private fun buildActionsColumn(): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(8), 0, dp(8), 0)
        }
        val defs = listOf<Pair<String, (Person) -> Unit>>(
            "\uD83D\uDCDE  Call" to { p: Person -> Actions.call(context, p) },
            "\uD83D\uDCAC  SMS" to { p: Person -> Actions.sms(context, p) },
            "\uD83D\uDFE2  WhatsApp" to { p: Person -> Actions.whatsapp(context, p, cc) },
            "\u2708\uFE0F  Telegram" to { p: Person -> Actions.telegram(context, p, cc) },
            "\u2709\uFE0F  Email" to { p: Person -> Actions.email(context, p) }
        )
        for ((label, fn) in defs) {
            val tv = TextView(context).apply {
                text = label
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = round(ACTION_BG, 20)
            }
            val lp = LinearLayout.LayoutParams(MP, dp(64))
            lp.setMargins(0, dp(7), 0, dp(7))
            col.addView(tv, lp)
            actions.add(Pair(tv, fn))
        }
        return col
    }

    private fun matches(p: Person, q: String): Boolean {
        val ql = q.trim().lowercase()
        if (ql.isEmpty()) return true
        if (p.name.lowercase().contains(ql)) return true
        val qd = ql.filter { it.isDigit() }
        if (qd.length == ql.length) {
            if (p.number.filter { it.isDigit() }.contains(qd)) return true
            if (T9.digits(p.name).contains(qd)) return true
        }
        return false
    }

    private fun refresh() {
        val src = if (tab == 0) recents else all
        shown = src.filter { matches(it, query) }
        adapter.notifyDataSetChanged()
        for (i in 0..1) {
            tabs[i].setTextColor(if (i == tab) 0xFF4FC3F7.toInt() else 0xAAFFFFFF.toInt())
            tabs[i].setTypeface(null, if (i == tab) Typeface.BOLD else Typeface.NORMAL)
        }
        empty.text = if (all.isEmpty() && recents.isEmpty())
            "Nothing to show. Allow contacts and call log permission in the app."
        else "No matches"
    }

    private fun load() {
        thread {
            val r = Repo.recents(context)
            val a = Repo.contacts(context)
            ui.post {
                recents = r
                all = a
                if (r.isEmpty() && a.isNotEmpty()) tab = 1
                refresh()
            }
        }
    }

    private fun startDrag(p: Person) {
        dragPerson = p
        val c = TextView(context).apply {
            text = p.name
            setTextColor(Color.BLACK)
            textSize = 16f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = round(0xFFFFEB3B.toInt(), 24)
            elevation = dp(8).toFloat()
        }
        addView(c, FrameLayout.LayoutParams(WC, WC))
        chip = c
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        c.post { moveDrag() }
    }

    private fun moveDrag() {
        val c = chip ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        c.x = lastX - loc[0] - c.width / 2f
        c.y = lastY - loc[1] - c.height - dp(24)
        var over: TextView? = null
        val r = Rect()
        val l = IntArray(2)
        for ((tv, _) in actions) {
            tv.getLocationOnScreen(l)
            r.set(l[0], l[1], l[0] + tv.width, l[1] + tv.height)
            if (r.contains(lastX.toInt(), lastY.toInt())) over = tv
        }
        if (over !== hover) {
            hover?.background = round(ACTION_BG, 20)
            over?.background = round(ACTION_HOT, 20)
            hover = over
        }
    }

    private fun endDrag(drop: Boolean) {
        val p = dragPerson
        val target = hover
        chip?.let { removeView(it) }
        chip = null
        dragPerson = null
        hover = null
        for ((tv, _) in actions) tv.background = round(ACTION_BG, 20)
        if (drop && p != null && target != null) {
            val fn = actions.first { it.first === target }.second
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