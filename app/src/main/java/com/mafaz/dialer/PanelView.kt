package com.mafaz.dialer

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
import kotlin.math.abs
import kotlin.math.max

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
    private val pane = ContactsPane(ctx, this, { onClose() }, { toggleSheet() })
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
    private var rowRef: LinearLayout? = null

    override val dragging: Boolean get() = dragPerson != null

    init {
        setBackgroundColor(Ui.scrim(ctx))
        isFocusableInTouchMode = true
        setOnClickListener { onClose() }

        actionsCol.orientation = LinearLayout.VERTICAL
        actionsCol.gravity = Gravity.CENTER_VERTICAL
        actionsCol.isClickable = true
        actionsCol.setPadding(dp(8), 0, dp(8), 0)
        for (id in ActionDefs.enabled(ctx)) {
            icon(id, id) { p -> Actions.run(context, id, p) }
        }
        actionsCol.addView(View(ctx), LinearLayout.LayoutParams(MP, dp(10)))
        val gear = IconView(ctx, "gear")
        gear.setOnClickListener {
            Actions.openSettings(context)
            onClose()
        }
        val gLpIcon = LinearLayout.LayoutParams(dp(40), dp(40))
        gLpIcon.setMargins(0, dp(6), 0, dp(6))
        gLpIcon.gravity = Gravity.CENTER_HORIZONTAL
        actionsCol.addView(gear, gLpIcon)
        if (Ui.cfg(ctx).getBoolean("showtoggles", false)) {
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
        actionsScroll.isFillViewport = true
        actionsScroll.isVerticalScrollBarEnabled = false
        actionsScroll.addView(actionsCol, FrameLayout.LayoutParams(MP, WC))

        holder.addView(pane, FrameLayout.LayoutParams(MP, MP))
        buildSheet()
        holder.addView(sheet, FrameLayout.LayoutParams(MP, sheetH, Gravity.BOTTOM))

        val row = LinearLayout(ctx)
        rowRef = row
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), dp(40), dp(8), dp(24))
        val frac = 0.55f + 0.04f * Ui.cfg(ctx).getInt("panelw", 3)
        val hLp = LinearLayout.LayoutParams(0, MP, frac)
        val gLp = LinearLayout.LayoutParams(0, MP, max(0.05f, 1f - frac))
        val aLp = LinearLayout.LayoutParams(dp(78), MP)
        val gap = View(ctx)
        if (leftSide) {
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
        val target = dp(24) + extra
        if (row.paddingBottom != target) row.setPadding(dp(8), dp(40), dp(8), target)
    }

    private fun dp(v: Int) = Ui.dp(context, v)

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

    private fun onIconTap(a: Act) {
        val p = pane.selectedPerson()
        if (p != null) {
            a.fn(p)
            if (a.id in repeatable) Store.setLastAction(context, p.number, a.id)
            onClose()
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
        var over: IconView? = null
        val r = Rect()
        val l = IntArray(2)
        for (a in acts) {
            val v = a.view
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
        for (a in acts) {
            a.view.hot = false
            a.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        if (drop && p != null && target != null) {
            val a = acts.first { it.view === target }
            a.fn(p)
            if (a.id in repeatable) Store.setLastAction(context, p.number, a.id)
            onClose()
        }
    }

    private fun inView(v: View, x: Float, y: Float): Boolean {
        val l = IntArray(2)
        v.getLocationOnScreen(l)
        return x >= l[0] && x <= l[0] + v.width && y >= l[1] && y <= l[1] + v.height
    }

    private fun swipeCheck(e: MotionEvent): Boolean {
        if (swipeDone || dragging) return false
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
