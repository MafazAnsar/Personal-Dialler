package com.mafaz.dialer

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private val onDone: () -> Unit,
    private val onKeypad: () -> Unit
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
    private var reversed = Ui.cfg(ctx).getBoolean("revlist", false)
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapKey = ""
    private var lastTapTime = 0L

    private val search = EditText(ctx)
    private val strip = LinearLayout(ctx)
    private val stripScroll = HorizontalScrollView(ctx)
    private val tabs = arrayOf(TextView(ctx), TextView(ctx))
    private val list = ListView(ctx)
    private val empty = TextView(ctx)
    private val listFrame = FrameLayout(ctx)

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

        search.hint = "Search contacts and recents"
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
        val head = LinearLayout(ctx)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(search, LinearLayout.LayoutParams(0, WC, 1f))
        val kb = IconView(ctx, "keypad")
        kb.setOnClickListener { onKeypad() }
        val kbLp = LinearLayout.LayoutParams(dp(40), dp(40))
        kbLp.leftMargin = dp(6)
        head.addView(kb, kbLp)
        val sb = IconView(ctx, "sort")
        sb.setOnClickListener { toggleOrder() }
        val sbLp = LinearLayout.LayoutParams(dp(40), dp(40))
        sbLp.leftMargin = dp(6)
        head.addView(sb, sbLp)
        addView(head, LinearLayout.LayoutParams(MP, WC))

        stripScroll.isHorizontalScrollBarEnabled = false
        strip.orientation = LinearLayout.VERTICAL
        stripScroll.addView(strip)
        addView(stripScroll, LinearLayout.LayoutParams(MP, WC))

        val tabRow = LinearLayout(ctx)
        val labels = arrayOf("Recent", "Contacts")
        for (i in 0..1) {
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
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) tapped(shown[pos], "l" + pos)
        }
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
        refresh()
        load()
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    fun setQuery(q: String) {
        if (search.text.toString() != q) search.setText(q)
    }

    private fun toggleOrder() {
        reversed = !reversed
        Ui.cfg(context).edit().putBoolean("revlist", reversed).apply()
        refresh()
    }

    private fun tapped(p: Person, tag: String) {
        val now = SystemClock.uptimeMillis()
        if (tag == lastTapKey && now - lastTapTime < 350) {
            lastTapKey = ""
            doubleTap(p)
        } else {
            lastTapKey = tag
            lastTapTime = now
        }
    }

    private fun doubleTap(p: Person) {
        val id = Store.lastAction(context, p.number) ?: "call"
        Actions.run(context, id, p)
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
        if (p.date > 0) {
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

    private fun favItem(p: Person): View {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.setPadding(dp(3), dp(3), dp(3), dp(3))
        val av = AvatarView(context)
        av.bind(p.name, p.contactId)
        item.addView(av, LinearLayout.LayoutParams(dp(44), dp(44)))
        val nm = TextView(context)
        nm.text = p.name.substringBefore(' ')
        nm.textSize = 10f
        nm.setSingleLine()
        nm.gravity = Gravity.CENTER
        nm.maxWidth = dp(54)
        nm.setTextColor(Ui.sub(context))
        item.addView(nm)
        item.setOnClickListener { tapped(p, "f" + p.number) }
        item.setOnLongClickListener {
            stripScroll.requestDisallowInterceptTouchEvent(true)
            host.beginDrag(p, lastX, lastY)
            true
        }
        item.setOnTouchListener { _, e -> track(e) }
        return item
    }

    private fun buildStrip() {
        strip.removeAllViews()
        val r1 = LinearLayout(context)
        val r2 = LinearLayout(context)
        for ((i, p) in favs.withIndex()) {
            (if (i % 2 == 0) r1 else r2).addView(favItem(p))
        }
        strip.addView(r1)
        strip.addView(r2)
    }

    private fun matches(p: Person, q: String): Boolean {
        val ql = q.trim().lowercase()
        if (ql.isEmpty()) return true
        if (p.name.lowercase().contains(ql)) return true
        if (p.number.replace(" ", "").contains(ql.replace(" ", ""))) return true
        val note = Store.note(context, p.number)
        if (note != null && note.lowercase().contains(ql)) return true
        val qd = ql.filter { it.isDigit() }
        if (qd.length == ql.length && T9.digits(p.name).contains(qd)) return true
        return false
    }

    private fun searchAll(q: String): List<Person> {
        val out = ArrayList<Person>()
        val seen = HashSet<String>()
        for (p in recents) {
            if (matches(p, q) && seen.add(Store.key(p.number))) out.add(p)
        }
        for (p in all) {
            if (matches(p, q) && seen.add(Store.key(p.number))) out.add(p)
        }
        return out
    }

    private fun refresh() {
        val accent = Ui.accent(context)
        for (i in 0..1) {
            tabs[i].setTextColor(if (i == tab) accent else Ui.sub(context))
            tabs[i].setTypeface(null, if (i == tab) Typeface.BOLD else Typeface.NORMAL)
        }
        stripScroll.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE
        val q = query.trim()
        var data = if (q.isEmpty()) (if (tab == 0) recents else all) else searchAll(q)
        if (reversed) data = data.reversed()
        shown = data
        adapter.notifyDataSetChanged()
        list.isStackFromBottom = reversed
        if (reversed && shown.isNotEmpty()) list.setSelection(shown.size - 1)
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
                if (r.isEmpty() && a.isNotEmpty()) tab = 1
                buildStrip()
                refresh()
            }
        }
    }
}
