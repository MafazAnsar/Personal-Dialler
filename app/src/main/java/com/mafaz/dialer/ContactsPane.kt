package com.mafaz.dialer

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

interface DragHost {
    val dragging: Boolean
    fun beginDrag(p: Person, x: Float, y: Float)
    fun dragTo(x: Float, y: Float)
    fun endDrag(drop: Boolean)
}

class ContactsPane(
    ctx: Context,
    private val host: DragHost,
    private val onDone: () -> Unit
) : LinearLayout(ctx) {

    private class Holder(val av: AvatarView, val name: TextView, val sub: TextView)

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ui = Handler(Looper.getMainLooper())
    private var recents: List<Person> = emptyList()
    private var all: List<Person> = emptyList()
    private var favs: List<Person> = emptyList()
    private var shown: List<Person> = emptyList()
    private var tab = 0
    private var query = ""
    private var lastX = 0f
    private var lastY = 0f

    private val search = EditText(ctx)
    private val strip = LinearLayout(ctx)
    private val stripScroll = HorizontalScrollView(ctx)
    private val tabs = arrayOf(TextView(ctx), TextView(ctx), TextView(ctx))
    private val list = ListView(ctx)
    private val empty = TextView(ctx)
    private val listFrame = FrameLayout(ctx)
    private val keypad = KeypadView(ctx, showCall = true, onCall = { n -> callNumber(n) })

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: newRow()
            bind(row.tag as Holder, shown[position])
            return row
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        background = Ui.round(Ui.bg(ctx), dp(18))
        setPadding(dp(8), dp(8), dp(8), dp(8))

        search.hint = "Search name or number"
        search.setSingleLine()
        search.textSize = 15f
        search.setTextColor(Ui.text(ctx))
        search.setHintTextColor(Ui.sub(ctx))
        search.background = Ui.round(Ui.card(ctx), dp(12))
        search.setPadding(dp(12), dp(8), dp(12), dp(8))
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                refresh()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        addView(search, LinearLayout.LayoutParams(MP, WC))

        stripScroll.isHorizontalScrollBarEnabled = false
        strip.orientation = LinearLayout.HORIZONTAL
        stripScroll.addView(strip)
        addView(stripScroll, LinearLayout.LayoutParams(MP, WC))

        val tabRow = LinearLayout(ctx)
        val labels = arrayOf("Recent", "Contacts", "Keypad")
        for (i in 0..2) {
            tabs[i].text = labels[i]
            tabs[i].gravity = Gravity.CENTER
            tabs[i].textSize = 14f
            tabs[i].setPadding(dp(4), dp(10), dp(4), dp(10))
            tabs[i].setOnClickListener { tab = i; refresh() }
            tabRow.addView(tabs[i], LinearLayout.LayoutParams(0, WC, 1f))
        }
        addView(tabRow, LinearLayout.LayoutParams(MP, WC))

        empty.setTextColor(Ui.sub(ctx))
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(8), dp(24), dp(8), dp(8))
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(0x33FFFFFF))
        list.adapter = adapter
        list.emptyView = empty
        list.setOnItemClickListener { _, _, _, _ -> hint() }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices) {
                list.parent.requestDisallowInterceptTouchEvent(true)
                host.beginDrag(shown[pos], lastX, lastY)
                true
            } else false
        }
        list.setOnTouchListener { _, e -> track(e) }
        listFrame.addView(list, FrameLayout.LayoutParams(MP, MP))
        listFrame.addView(empty, FrameLayout.LayoutParams(MP, WC))
        addView(listFrame, LinearLayout.LayoutParams(MP, 0, 1f))
        addView(keypad, LinearLayout.LayoutParams(MP, 0, 1f))
        refresh()
        load()
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun hint() {
        Toast.makeText(context, "Hold and drag onto an action", Toast.LENGTH_SHORT).show()
    }

    private fun callNumber(n: String) {
        if (n.isBlank()) return
        Actions.call(context, Person(n, n))
        onDone()
    }

    private fun track(e: MotionEvent): Boolean {
        lastX = e.rawX
        lastY = e.rawY
        if (!host.dragging) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> host.dragTo(e.rawX, e.rawY)
            MotionEvent.ACTION_UP -> host.endDrag(true)
            MotionEvent.ACTION_CANCEL -> host.endDrag(false)
        }
        return true
    }

    private fun newRow(): View {
        val av = AvatarView(context)
        val name = TextView(context)
        name.textSize = 16f
        name.setTypeface(null, Typeface.BOLD)
        name.setSingleLine()
        val sub = TextView(context)
        sub.textSize = 12f
        sub.setSingleLine()
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.addView(name)
        col.addView(sub)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        row.addView(av, LinearLayout.LayoutParams(dp(44), dp(44)))
        val lp = LinearLayout.LayoutParams(0, WC, 1f)
        lp.leftMargin = dp(12)
        row.addView(col, lp)
        row.tag = Holder(av, name, sub)
        return row
    }

    private fun bind(h: Holder, p: Person) {
        val c = context
        h.av.bind(p.name, p.contactId)
        h.name.text = p.name
        h.name.setTextColor(if (p.callType == CallLog.Calls.MISSED_TYPE) 0xFFE53935.toInt() else Ui.text(c))
        val sb = StringBuilder()
        if (tab == 0 && p.date > 0) {
            sb.append(typeIcon(p.callType)).append(' ').append(relTime(p.date))
            if (p.duration > 0) sb.append("  ").append(fmtDur(p.duration))
            if (p.name != p.number) sb.append("  ").append(p.number)
        } else {
            sb.append(p.number)
        }
        val note = Store.note(c, p.number)
        if (note != null) sb.append("  \uD83D\uDCDD ").append(note)
        h.sub.text = sb.toString()
        h.sub.setTextColor(Ui.sub(c))
    }

    private fun typeIcon(t: Int): String = when (t) {
        1 -> "\u2199"
        2 -> "\u2197"
        3 -> "\u2716"
        5 -> "\u2298"
        else -> "\u2022"
    }

    private fun relTime(d: Long): String = DateUtils.getRelativeTimeSpanString(
        d, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    private fun fmtDur(s: Long): String {
        val m = s / 60
        val r = s % 60
        return m.toString() + ":" + (if (r < 10) "0" else "") + r
    }

    private fun buildStrip() {
        strip.removeAllViews()
        for (p in favs) {
            val item = LinearLayout(context)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(dp(4), dp(4), dp(4), dp(4))
            val av = AvatarView(context)
            av.bind(p.name, p.contactId)
            item.addView(av, LinearLayout.LayoutParams(dp(50), dp(50)))
            val nm = TextView(context)
            nm.text = p.name.substringBefore(' ')
            nm.textSize = 11f
            nm.setSingleLine()
            nm.gravity = Gravity.CENTER
            nm.maxWidth = dp(58)
            nm.setTextColor(Ui.sub(context))
            item.addView(nm)
            item.setOnClickListener { hint() }
            item.setOnLongClickListener {
                stripScroll.requestDisallowInterceptTouchEvent(true)
                host.beginDrag(p, lastX, lastY)
                true
            }
            item.setOnTouchListener { _, e -> track(e) }
            strip.addView(item)
        }
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
        val accent = Ui.accent(context)
        for (i in 0..2) {
            tabs[i].setTextColor(if (i == tab) accent else Ui.sub(context))
            tabs[i].setTypeface(null, if (i == tab) Typeface.BOLD else Typeface.NORMAL)
        }
        val kp = tab == 2
        keypad.visibility = if (kp) View.VISIBLE else View.GONE
        listFrame.visibility = if (kp) View.GONE else View.VISIBLE
        search.visibility = if (kp) View.GONE else View.VISIBLE
        stripScroll.visibility = if (kp || favs.isEmpty()) View.GONE else View.VISIBLE
        if (kp) return
        shown = (if (tab == 0) recents else all).filter { matches(it, query) }
        adapter.notifyDataSetChanged()
        empty.text = if (all.isEmpty() && recents.isEmpty())
            "Nothing to show. Allow contacts and call log permission in the app."
        else "No matches"
    }

    private fun load() {
        thread {
            val r = Repo.recents(context)
            val a = Repo.contacts(context)
            val f = Repo.favourites(context)
            ui.post {
                recents = r
                all = a
                favs = f
                keypad.contacts = a
                if (r.isEmpty() && a.isNotEmpty()) tab = 1
                buildStrip()
                refresh()
            }
        }
    }
}
