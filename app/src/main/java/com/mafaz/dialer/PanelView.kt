package com.mafaz.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PanelView(
    ctx: Context,
    private val leftSide: Boolean,
    private val onClose: () -> Unit
) : FrameLayout(ctx), DragHost {

    private class Act(val view: IconView, val id: String, val fn: (Person) -> Unit)

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val repeatable = setOf("call", "sms", "wa", "tg", "signal", "viber", "mail")
    private val acts = ArrayList<Act>()
    private val toggles = ArrayList<IconView>()
    private val actionsCol = LinearLayout(ctx)
    private val actionsScroll = ScrollView(ctx)
    private val holder = FrameLayout(ctx)
    private val sheet = FrameLayout(ctx)
    private val trayMode = Ui.cfg(ctx).getString("actionmode", "tray") == "tray"
    private val pane = ContactsPane(
        ctx, this, { onClose() }, { toggleSheet() }, { p -> onSelection(p) }, trayMode,
        { if (!trayMode) buildColumn() }
    )
    private val keypad = KeypadView(
        ctx,
        showCall = true,
        onCall = { n -> dial(n) },
        onChanged = { q -> pane.setQuery(q) }
    )
    private val sheetH = (ctx.resources.displayMetrics.heightPixels * 0.5f).toInt()

    private var dragPerson: Person? = null
    private var chip: TextView? = null
    private var hover: IconView? = null
    private var lastX = 0f
    private var lastY = 0f
    private var closing = false
    private var downX = 0f
    private var downY = 0f
    private var swipeDone = false
    private var kb = 0
    private var topPad = Ui.dp(ctx, 40)
    private var botPad = Ui.dp(ctx, 24)
    private var layer: FrameLayout? = null
    private var layerIsBubble = false
    private var dragBubble: View? = null
    private val bubbleActs = ArrayList<Act>()
    private val inPanel = setOf("note", "history", "remind", "block", "fav")
    private var startX = 0f
    private var startY = 0f
    private var rowRef: LinearLayout? = null

    override val dragging: Boolean get() = dragPerson != null

    init {
        setBackgroundColor(Ui.scrim(ctx))
        isFocusableInTouchMode = true
        setOnClickListener { onClose() }

        if (!trayMode) {
            actionsCol.orientation = LinearLayout.VERTICAL
            actionsCol.isClickable = true
            actionsCol.setPadding(dp(8), 0, dp(8), 0)
            actionsScroll.isFillViewport = true
            actionsScroll.isVerticalScrollBarEnabled = false
            actionsScroll.addView(actionsCol, FrameLayout.LayoutParams(MP, WC))
            buildColumn()
        }

        holder.addView(pane, FrameLayout.LayoutParams(MP, MP))
        buildSheet()
        holder.addView(sheet, FrameLayout.LayoutParams(MP, sheetH, Gravity.BOTTOM))

        val row = LinearLayout(ctx)
        rowRef = row
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), topPad, dp(8), botPad)
        val frac = 0.55f + 0.04f * Ui.cfg(ctx).getInt("panelw", 3)
        val hLp = LinearLayout.LayoutParams(0, MP, frac)
        val gLp = LinearLayout.LayoutParams(0, MP, max(0.05f, 1f - frac))
        val aLp = LinearLayout.LayoutParams(dp(78), MP)
        val gap = View(ctx)
        if (trayMode) {
            // no side column: the panel is wider and the rest is a margin that closes it
            val tf = min(0.97f, frac + 0.15f)
            val tLp = LinearLayout.LayoutParams(0, MP, tf)
            val tgLp = LinearLayout.LayoutParams(0, MP, max(0.03f, 1f - tf))
            if (leftSide) {
                row.addView(holder, tLp); row.addView(gap, tgLp)
            } else {
                row.addView(gap, tgLp); row.addView(holder, tLp)
            }
        } else if (leftSide) {
            row.addView(holder, hLp); row.addView(gap, gLp); row.addView(actionsScroll, aLp)
        } else {
            row.addView(actionsScroll, aLp); row.addView(gap, gLp); row.addView(holder, hLp)
        }
        addView(row, FrameLayout.LayoutParams(MP, MP))

        setOnApplyWindowInsetsListener { _, insets ->
            kb = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                insets.systemWindowInsetBottom
            }
            if (kb < dp(100)) kb = 0
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                if (b.top > 0) topPad = b.top + dp(12)
                if (b.bottom > 0) botPad = b.bottom + dp(12)
            }
            applyKeyboardPadding()
            insets
        }
        viewTreeObserver.addOnGlobalLayoutListener { applyKeyboardPadding() }
    }

    private fun realHeight(): Int {
        val dm = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        return dm.heightPixels
    }

    private fun currentKb(): Int {
        if (kb > 0) return kb
        val r = Rect()
        getWindowVisibleDisplayFrame(r)
        val k = realHeight() - r.bottom
        return if (k > dp(150)) k else 0
    }

    // Lifts the panel above the on-screen keyboard so search results stay visible.
    private fun applyKeyboardPadding() {
        val row = rowRef ?: return
        val k = currentKb()
        var extra = 0
        if (k > 0) {
            val loc = IntArray(2)
            row.getLocationOnScreen(loc)
            val rowBottom = loc[1] + row.height
            extra = max(0, rowBottom - (realHeight() - k))
        }
        val target = botPad + extra
        if (row.paddingBottom != target || row.paddingTop != topPad) {
            row.setPadding(dp(8), topPad, dp(8), target)
        }
    }

    private fun dp(v: Int) = Ui.dp(context, v)

    private fun addGear() {
        val gear = IconView(context, "gear")
        gear.setOnClickListener {
            Actions.openSettings(context)
            onClose()
        }
        val lp = LinearLayout.LayoutParams(dp(40), dp(40))
        lp.setMargins(0, dp(6), 0, dp(6))
        lp.gravity = Gravity.CENTER_HORIZONTAL
        actionsCol.addView(gear, lp)
    }

    // When the list starts from the bottom, settings moves to the top so the action icons
    // sit low, within easy reach of your thumb.
    private fun buildColumn() {
        actionsCol.removeAllViews()
        acts.clear()
        toggles.clear()
        val fromBottom = Ui.cfg(context).getBoolean("revlist", false)
        actionsCol.gravity = if (fromBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        if (fromBottom) {
            addGear()
            actionsCol.addView(View(context), LinearLayout.LayoutParams(MP, 0, 1f))
        }
        for (id in ActionDefs.enabled(context)) {
            icon(id, id) { p -> Actions.run(context, id, p) }
        }
        if (!fromBottom) {
            actionsCol.addView(View(context), LinearLayout.LayoutParams(MP, dp(10)))
            addGear()
        }
        if (Ui.cfg(context).getBoolean("showtoggles", false)) {
            toggle("torch") {
                Toggles.torch(context)
                postDelayed({ refreshToggles() }, 250)
            }
            toggle("bt") {
                Toggles.bluetooth(context)
                onClose()
            }
            toggle("wifi") {
                Toggles.wifi(context)
                onClose()
            }
            toggle("data") {
                Toggles.data(context)
                onClose()
            }
            toggle("dnd") {
                Toggles.toggleDnd(context)
                postDelayed({ refreshToggles() }, 300)
            }
            toggle("sound") {
                Toggles.toggleSound(context)
                refreshToggles()
            }
            layoutToggles()
            refreshToggles()
        }
    }

    // Opens the keypad sheet (used by phone links, the keypad shortcut and Add call).
    fun openKeypad(number: String?) {
        if (sheet.visibility != View.VISIBLE) toggleSheet()
        if (!number.isNullOrEmpty()) keypad.setNumber(number)
    }

    private fun buildSheet() {
        val bgd = GradientDrawable()
        bgd.setColor(Ui.withAlpha(Ui.solid(context), Ui.keyAlpha(context)))
        val r = if (Ui.square(context)) 0f else dp(20).toFloat()
        bgd.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = bgd
        sheet.isClickable = true
        sheet.setPadding(dp(8), dp(2), dp(8), dp(8))
        sheet.visibility = View.GONE
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        val handle = TextView(context)
        handle.text = "\u25BE"
        handle.textSize = 22f
        handle.gravity = Gravity.CENTER
        handle.setTextColor(Ui.sub(context))
        handle.setOnClickListener { toggleSheet() }
        col.addView(handle, LinearLayout.LayoutParams(MP, dp(28)))
        col.addView(keypad, LinearLayout.LayoutParams(MP, 0, 1f))
        sheet.addView(col, FrameLayout.LayoutParams(MP, MP))
    }

    private fun toggleSheet() {
        val lp = pane.layoutParams as FrameLayout.LayoutParams
        if (sheet.visibility == View.VISIBLE) {
            sheet.animate().translationY(sheetH.toFloat()).setDuration(180)
                .withEndAction { sheet.visibility = View.GONE }.start()
            lp.bottomMargin = 0
            keypad.setNumber("")
        } else {
            sheet.visibility = View.VISIBLE
            sheet.translationY = sheetH.toFloat()
            sheet.animate().translationY(0f).setDuration(200).start()
            lp.bottomMargin = sheetH + dp(6)
        }
        pane.layoutParams = lp
    }

    private fun dial(n: String) {
        if (n.isBlank()) return
        Actions.call(context, Person(n, n))
        onClose()
    }

    private fun icon(kind: String, id: String, fn: (Person) -> Unit) {
        val v = IconView(context, kind)
        v.alpha = 0.92f
        val lp = LinearLayout.LayoutParams(dp(54), dp(54))
        lp.setMargins(0, dp(4), 0, dp(4))
        lp.gravity = Gravity.CENTER_HORIZONTAL
        actionsCol.addView(v, lp)
        val act = Act(v, id, fn)
        acts.add(act)
        v.setOnClickListener { onIconTap(act) }
    }

    private fun onSelection(p: Person?) {
        for (a in acts) {
            val v = a.view
            v.alpha = if (p != null) 1f else 0.92f
            if (p != null) {
                v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }.start()
            }
        }
    }

    private fun performAction(a: Act, p: Person, ax: Float, ay: Float) {
        if (a.id in inPanel) {
            runInPanel(a.id, p, ax, ay)
        } else {
            a.fn(p)
            if (a.id in repeatable) Store.setLastAction(context, p.number, a.id)
            onClose()
        }
    }

    private fun onIconTap(a: Act) {
        val p = pane.selectedPerson()
        if (p != null) {
            val loc = IntArray(2)
            a.view.getLocationOnScreen(loc)
            performAction(a, p, loc[0] + a.view.width / 2f, loc[1] + a.view.height / 2f)
        } else if (a.id == "call") {
            openKeypad(null)
        } else {
            if (Actions.openApp(context, a.id)) onClose()
        }
    }

    private fun toggle(kind: String, click: () -> Unit) {
        val v = IconView(context, kind)
        v.setOnClickListener { click() }
        toggles.add(v)
    }

    private fun layoutToggles() {
        var row: LinearLayout? = null
        for ((i, t) in toggles.withIndex()) {
            if (i % 2 == 0) {
                val r = LinearLayout(context)
                r.gravity = Gravity.CENTER
                actionsCol.addView(r, LinearLayout.LayoutParams(MP, WC))
                row = r
            }
            val lp = LinearLayout.LayoutParams(dp(30), dp(30))
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            row?.addView(t, lp)
        }
    }

    private fun refreshToggles() {
        for (t in toggles) {
            when (t.kind) {
                "torch" -> t.on = Toggles.torchOn
                "bt" -> t.on = Toggles.btOn(context)
                "wifi" -> t.on = Toggles.wifiOn(context)
                "data" -> t.on = Toggles.dataOn(context)
                "dnd" -> t.on = Toggles.dndOn(context)
                else -> {
                    val normal = Toggles.soundNormal(context)
                    t.kind = if (normal) "sound" else "vib"
                    t.on = normal
                    t.invalidate()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        val cfg = Ui.cfg(context)
        if (!cfg.getBoolean("tip_select", false)) {
            cfg.edit().putBoolean("tip_select", true).apply()
            Toast.makeText(context, "Tip: tap a contact, then tap an icon. Dragging works too.", Toast.LENGTH_LONG).show()
        }
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
        val w = dp(320).toFloat()
        holder.translationX = if (leftSide) -w else w
        holder.animate().translationX(0f).setDuration(240).start()
        actionsScroll.translationX = if (leftSide) w else -w
        actionsScroll.animate().translationX(0f).setDuration(240).setStartDelay(40).start()
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
        startX = x
        startY = y
        val c = TextView(context)
        c.text = p.name
        c.setTextColor(Color.BLACK)
        c.textSize = 16f
        c.setPadding(dp(16), dp(10), dp(16), dp(10))
        c.background = Ui.round(Ui.accent(context), dp(if (Ui.square(context)) 6 else 24))
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
        if (trayMode && dragBubble == null && layer == null) {
            val mx = lastX - startX
            val my = lastY - startY
            if (mx * mx + my * my > dp(14) * dp(14)) showDragBubble()
        }
        var over: IconView? = null
        val r = Rect()
        val l = IntArray(2)
        for (a in acts + bubbleActs) {
            val v = a.view
            if (!v.isAttachedToWindow) continue
            v.getLocationOnScreen(l)
            r.set(l[0], l[1], l[0] + v.width, l[1] + v.height)
            if (r.contains(lastX.toInt(), lastY.toInt())) over = v
        }
        if (over !== hover) {
            hover?.let {
                it.hot = false
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            over?.let {
                it.hot = true
                it.animate().scaleX(1.18f).scaleY(1.18f).setDuration(100).start()
            }
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
        for (a in acts + bubbleActs) {
            a.view.hot = false
            a.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        val hit = if (target != null) (acts + bubbleActs).firstOrNull { it.view === target } else null
        removeDragBubble()
        if (drop && p != null && target == null) {
            val dx = lastX - startX
            val dy = lastY - startY
            if (dx * dx + dy * dy < dp(14) * dp(14)) {
                openMenu(p, startX, startY)
            }
        }
        if (drop && p != null && hit != null) {
            performAction(hit, p, lastX, lastY)
        }
    }

    private fun inView(v: View, x: Float, y: Float): Boolean {
        val l = IntArray(2)
        v.getLocationOnScreen(l)
        return x >= l[0] && x <= l[0] + v.width && y >= l[1] && y <= l[1] + v.height
    }

    private fun swipeCheck(e: MotionEvent): Boolean {
        if (swipeDone || dragging || layer != null) return false
        if (sheet.visibility == View.VISIBLE && inView(sheet, downX, downY)) return false
        val dx = e.rawX - downX
        val dy = e.rawY - downY
        if (abs(dx) < dp(40) || abs(dx) < abs(dy) * 2f) return false
        if (pane.stripCanScroll(downX, downY, dx)) return false
        swipeDone = true
        pane.switchTab(if (dx < 0) 1 else -1)
        return true
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                swipeDone = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeCheck(e)) return true
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                swipeDone = false
            }
            MotionEvent.ACTION_MOVE -> swipeCheck(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = swipeDone
                swipeDone = false
                if (was) return true
            }
        }
        if (swipeDone) return true
        return super.onTouchEvent(e)
    }

    // Back closes an open popup first; returns false when the panel itself should close.
    fun handleBack(): Boolean {
        if (layer != null) {
            dismissPopup()
            return true
        }
        return false
    }

    private fun dismissPopup() {
        val l = layer ?: return
        removeView(l)
        layer = null
        layerIsBubble = false
        if (dragBubble == null) bubbleActs.clear()
    }

    private fun onLayerClick() {
        if (layerIsBubble && pane.recentTap()) {
            dismissPopup()
            pane.doubleTapSelected()
        } else {
            dismissPopup()
        }
    }

    private fun placeBubble(card: View, cx: Float, topY: Float, bottomY: Float) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val w = card.width.toFloat()
        val h = card.height.toFloat()
        val m = dp(8).toFloat()
        var x = cx - loc[0] - w / 2f
        x = x.coerceIn(m, max(m, width - w - m))
        var y = bottomY - loc[1] + dp(6)
        if (y + h > height - m) y = topY - loc[1] - h - dp(6)
        y = y.coerceIn(m, max(m, height - h - m))
        card.x = x
        card.y = y
    }

    // A compact tray of the action icons, so no long drag across the panel is needed.
    private fun buildBubble(p: Person, inDrag: Boolean): LinearLayout {
        bubbleActs.clear()
        val card = Cards.card(context)
        card.setPadding(dp(5), dp(5), dp(5), dp(5))
        val ids = ActionDefs.enabled(context) + (if (inDrag) emptyList<String>() else listOf("more"))
        var row: LinearLayout? = null
        var n = 0
        for (id in ids) {
            if (n % 5 == 0) {
                val r = LinearLayout(context)
                r.gravity = Gravity.CENTER
                card.addView(r, LinearLayout.LayoutParams(WC, WC))
                row = r
            }
            n++
            val v = IconView(context, id)
            val lp = LinearLayout.LayoutParams(dp(42), dp(42))
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            row?.addView(v, lp)
            if (id == "more") {
                v.setOnClickListener {
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    dismissPopup()
                    openMenu(p, loc[0] + v.width / 2f, loc[1] + v.height / 2f)
                }
            } else {
                val act = Act(v, id) { q -> Actions.run(context, id, q) }
                bubbleActs.add(act)
                if (!inDrag) {
                    v.setOnClickListener {
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        performAction(act, p, loc[0] + v.width / 2f, loc[1] + v.height / 2f)
                    }
                }
            }
        }
        return card
    }

    override fun onRowTapped(p: Person, row: View) {
        if (!trayMode) return
        val card = buildBubble(p, false)
        dismissPopup()
        val l = FrameLayout(context)
        l.setOnClickListener { onLayerClick() }
        l.addView(card, FrameLayout.LayoutParams(WC, WC))
        addView(l, FrameLayout.LayoutParams(MP, MP))
        layer = l
        layerIsBubble = true
        card.alpha = 0f
        card.post {
            val loc = IntArray(2)
            row.getLocationOnScreen(loc)
            placeBubble(card, loc[0] + row.width / 2f, loc[1].toFloat(), (loc[1] + row.height).toFloat())
            card.animate().alpha(1f).setDuration(100).start()
        }
    }

    private fun showDragBubble() {
        val p = dragPerson ?: return
        val card = buildBubble(p, true)
        dragBubble = card
        addView(card, FrameLayout.LayoutParams(WC, WC))
        card.alpha = 0f
        card.post {
            placeBubble(card, startX, startY - dp(60), startY + dp(10))
            card.animate().alpha(1f).setDuration(100).start()
        }
    }

    private fun removeDragBubble() {
        val b = dragBubble ?: return
        removeView(b)
        dragBubble = null
        bubbleActs.clear()
    }

    private fun showPopup(card: View, widthDp: Int, ax: Float, ay: Float) {
        dismissPopup()
        val l = FrameLayout(context)
        l.setOnClickListener { onLayerClick() }
        l.addView(card, FrameLayout.LayoutParams(dp(widthDp), WC))
        addView(l, FrameLayout.LayoutParams(MP, MP))
        layer = l
        card.alpha = 0f
        card.post {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val w = card.width.toFloat()
            val h = card.height.toFloat()
            val m = dp(8).toFloat()
            var x = ax - loc[0] - w / 2f
            x = x.coerceIn(m, max(m, width - w - m))
            var y = ay - loc[1] + dp(16)
            if (y + h > height - m) y = ay - loc[1] - h - dp(16)
            y = y.coerceIn(m, max(m, height - h - m))
            card.x = x
            card.y = y
            card.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun openMenu(p: Person, ax: Float, ay: Float) {
        val known = p.contactId != null || Repo.lookup(context, p.number) != null
        val fav = Favs.isFav(context, p)
        val blocked = Store.isBlocked(context, p.number)
        val labels = ArrayList<String>()
        val runs = ArrayList<() -> Unit>()
        if (!known) {
            labels.add("Save to contacts")
            runs.add { Actions.addContact(context, p); onClose() }
        } else {
            labels.add("Contact details")
            runs.add { Actions.openContact(context, p); onClose() }
        }
        val cid = p.contactId ?: Repo.lookup(context, p.number)?.contactId
        if (cid != null && pane.numberCount(cid) > 1) {
            val d = Store.defaultNumber(context, cid)
            if (d == null || Store.key(d) != Store.key(p.number)) {
                labels.add("Set as default number")
                runs.add {
                    Store.setDefaultNumber(context, cid, p.number)
                    Cache.clear()
                    Toast.makeText(context, "Default number set", Toast.LENGTH_SHORT).show()
                    dismissPopup()
                    pane.reload()
                }
            }
        }
        labels.add(if (fav) "Remove from favourites" else "Add to favourites")
        runs.add { Actions.favourite(context, p); dismissPopup(); pane.reload() }
        labels.add("Call history")
        runs.add { showHistory(p, ax, ay) }
        labels.add("Note")
        runs.add { showNote(p, ax, ay) }
        labels.add("Remind me")
        runs.add { showRemind(p, ax, ay) }
        labels.add(if (blocked) "Unblock" else "Block")
        runs.add { toggleBlock(p) }
        labels.add("Copy number")
        runs.add { copyNumber(p) }
        showPopup(Cards.menu(context, p.name, labels, -1) { i -> runs[i]() }, 210, ax, ay)
    }

    private fun runInPanel(id: String, p: Person, ax: Float, ay: Float) {
        when (id) {
            "note" -> showNote(p, ax, ay)
            "history" -> showHistory(p, ax, ay)
            "remind" -> showRemind(p, ax, ay)
            "block" -> toggleBlock(p)
            "fav" -> {
                Actions.favourite(context, p)
                dismissPopup()
                pane.reload()
            }
        }
    }

    private fun showHistory(p: Person, ax: Float, ay: Float) {
        val h = History.load(context, p.number)
        val title = p.name + "  \u00B7  " + h.count + " calls  \u00B7  " + History.fmt(h.total)
        val rows = if (h.rows.isEmpty()) listOf("No calls found") else h.rows
        showPopup(Cards.rows(context, title, rows), 250, ax, ay)
    }

    private fun showNote(p: Person, ax: Float, ay: Float) {
        val card = Cards.input(
            context, "Note for " + p.name, Store.note(context, p.number) ?: "", "Note or label",
            { t ->
                Store.setNote(context, p.number, t)
                dismissPopup()
                pane.refreshList()
            },
            { dismissPopup() }
        )
        showPopup(card, 270, ax, min(ay, realHeight() * 0.28f))
    }

    private fun showRemind(p: Person, ax: Float, ay: Float) {
        val card = Cards.menu(context, "Remind me to call " + p.name, Reminder.labels.toList(), -1) { i ->
            Reminder.schedule(context, p.number, p.name, Reminder.minutesFor(i))
            Toast.makeText(context, "Reminder set", Toast.LENGTH_SHORT).show()
            dismissPopup()
        }
        showPopup(card, 220, ax, ay)
    }

    private fun toggleBlock(p: Person) {
        val now = Store.isBlocked(context, p.number)
        Store.setBlocked(context, p.number, !now)
        Toast.makeText(context, (if (now) "Unblocked " else "Blocked ") + p.name, Toast.LENGTH_SHORT).show()
        dismissPopup()
    }

    private fun copyNumber(p: Person) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("number", p.number))
        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
        dismissPopup()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (layer != null) dismissPopup() else onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
