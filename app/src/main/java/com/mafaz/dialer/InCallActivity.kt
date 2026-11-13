package com.mafaz.dialer

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.SmsManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

class RingView(ctx: Context, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = Ui.dp(context, 3).toFloat()
        p.color = color
        val r = min(width, height) / 2f - p.strokeWidth
        c.drawCircle(width / 2f, height / 2f, r, p)
    }
}

class InCallActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val dark = 0xFF0B0F14.toInt()
    private val glass = 0x26FFFFFF
    private val glassOn = 0xFFFFFFFF.toInt()
    private val inkOn = 0xFF101418.toInt()

    private lateinit var root: FrameLayout
    private lateinit var avatar: AvatarView
    private lateinit var nameT: TextView
    private lateinit var numberT: TextView
    private lateinit var noteT: TextView
    private lateinit var statusT: TextView
    private lateinit var waitBar: LinearLayout
    private lateinit var waitT: TextView
    private lateinit var swapB: TextView
    private lateinit var mergeB: TextView
    private lateinit var card: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var padWrap: LinearLayout
    private lateinit var pad: KeypadView
    private lateinit var replyB: TextView
    private lateinit var swipeWrap: LinearLayout
    private lateinit var swipe: SwipeAnswer
    private lateinit var endCell: LinearLayout
    private lateinit var muteV: IconView
    private lateinit var audioV: IconView
    private lateinit var audioL: TextView
    private lateinit var holdV: IconView
    private val rings = ArrayList<RingView>()
    private val anims = ArrayList<ObjectAnimator>()
    private var answerAnim: ObjectAnimator? = null
    private var pulsing = false
    private var wasRinging = false

    private var muted = false
    private var showPad = false
    private var cachedNumber: String? = null
    private var cachedPerson: Person? = null
    private var cachedOtherNumber: String? = null
    private var cachedOther: Person? = null
    private var prox: PowerManager.WakeLock? = null
    private val listener: () -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() {
            render()
            h.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun label(size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.textSize = size
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        t.setSingleLine()
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun radius(round: Int, square: Int): Int = dp(if (Ui.square(this)) square else round)

    private fun ctl(kind: String, text: String, onClick: () -> Unit): Triple<LinearLayout, IconView, TextView> {
        val v = IconView(this, kind)
        v.bgOverride = glass
        v.glyphColor = Color.WHITE
        v.forceCircle = !Ui.square(this)
        v.setOnClickListener { onClick() }
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.addView(v, LinearLayout.LayoutParams(dp(58), dp(58)))
        val l = label(12f, 0xCCFFFFFF.toInt(), false)
        l.text = text
        val lp = LinearLayout.LayoutParams(WC, WC)
        lp.topMargin = dp(6)
        cell.addView(l, lp)
        return Triple(cell, v, l)
    }

    private fun bigCell(kind: String, color: Int, text: String, onClick: () -> Unit): Pair<LinearLayout, IconView> {
        val v = IconView(this, kind)
        v.bgOverride = color
        v.glyphColor = Color.WHITE
        v.forceCircle = !Ui.square(this)
        v.setOnClickListener { onClick() }
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.addView(v, LinearLayout.LayoutParams(dp(72), dp(72)))
        val l = label(12f, 0xCCFFFFFF.toInt(), false)
        l.text = text
        val lp = LinearLayout.LayoutParams(WC, WC)
        lp.topMargin = dp(6)
        cell.addView(l, lp)
        return Pair(cell, v)
    }

    private fun chip(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(8), dp(16), dp(8))
        t.background = Ui.round(glass, dp(18))
        return t
    }

    private fun smallBtn(text: String, onClick: () -> Unit): TextView {
        val b = chip(text)
        b.setTypeface(null, Typeface.BOLD)
        b.background = Ui.round(Ui.accent(this), dp(14))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun setOn(v: IconView, on: Boolean) {
        v.bgOverride = if (on) glassOn else glass
        v.glyphColor = if (on) inkOn else Color.WHITE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this)
        root.setBackgroundColor(dark)
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(20), dp(40), dp(20), dp(150))
        scroll.addView(col, FrameLayout.LayoutParams(MP, WC))
        root.addView(scroll, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)

        val hDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val avDp = (hDp * 0.15f).coerceIn(84f, 128f).toInt()
        val stageDp = (avDp * 1.75f).toInt()
        val stage = FrameLayout(this)
        for (i in 0..1) {
            val r = RingView(this, Ui.accent(this))
            r.visibility = View.INVISIBLE
            rings.add(r)
            stage.addView(r, FrameLayout.LayoutParams(dp(avDp + 8), dp(avDp + 8), Gravity.CENTER))
        }
        avatar = AvatarView(this)
        stage.addView(avatar, FrameLayout.LayoutParams(dp(avDp), dp(avDp), Gravity.CENTER))
        col.addView(stage, LinearLayout.LayoutParams(dp(stageDp), dp(stageDp)))

        nameT = label(30f, Color.WHITE, false)
        nameT.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        numberT = label(15f, 0xB3FFFFFF.toInt(), false)
        noteT = chip("")
        noteT.visibility = View.GONE
        statusT = label(15f, 0xFFFFFFFF.toInt(), false)
        statusT.letterSpacing = 0.12f
        col.addView(nameT, LinearLayout.LayoutParams(MP, WC))
        col.addView(numberT, LinearLayout.LayoutParams(MP, WC))
        val nlp = LinearLayout.LayoutParams(WC, WC)
        nlp.topMargin = dp(8)
        col.addView(noteT, nlp)
        val slp = LinearLayout.LayoutParams(MP, WC)
        slp.topMargin = dp(10)
        col.addView(statusT, slp)

        waitBar = LinearLayout(this)
        waitBar.orientation = LinearLayout.HORIZONTAL
        waitBar.gravity = Gravity.CENTER_VERTICAL
        waitBar.setPadding(dp(16), dp(8), dp(8), dp(8))
        waitBar.background = Ui.round(glass, dp(16))
        waitBar.visibility = View.GONE
        waitT = label(14f, Color.WHITE, false)
        waitT.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        swapB = smallBtn("Swap") { CallManager.other()?.unhold() }
        mergeB = smallBtn("Merge") {
            val c = CallManager.current()
            val o = CallManager.other()
            if (c != null && o != null) c.conference(o)
        }
        waitBar.addView(waitT, LinearLayout.LayoutParams(0, WC, 1f))
        val b1 = LinearLayout.LayoutParams(WC, WC)
        b1.leftMargin = dp(6)
        waitBar.addView(swapB, b1)
        val b2 = LinearLayout.LayoutParams(WC, WC)
        b2.leftMargin = dp(6)
        waitBar.addView(mergeB, b2)
        val wlp = LinearLayout.LayoutParams(MP, WC)
        wlp.topMargin = dp(12)
        col.addView(waitBar, wlp)

        col.addView(View(this), LinearLayout.LayoutParams(MP, 0, 1f))

        replyB = chip("\u2709   Reply with message")
        replyB.setOnClickListener { replyAndDecline() }
        replyB.visibility = View.GONE
        val rlp = LinearLayout.LayoutParams(WC, WC)
        rlp.bottomMargin = dp(18)
        col.addView(replyB, rlp)

        card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.round(0x1AFFFFFF, radius(28, 10))
        card.setPadding(dp(12), dp(18), dp(12), dp(14))

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        val mute = ctl("mic", "Mute") {
            muted = !muted
            CallManager.service?.setMuted(muted)
            render()
        }
        val keys = ctl("keypad", "Keypad") { showPad = true; render() }
        val spk = ctl("phone", "Phone") { audioPicker() }
        val hold = ctl("pause", "Hold") {
            val c = CallManager.current()
            if (c != null) { if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold() }
        }
        val add = ctl("plus", "Add call") { startActivity(Intent(this, DialActivity::class.java)) }
        val note = ctl("cnote", "Note") { withPerson { p -> Actions.note(this, p) } }
        val block = ctl("block", "Block") { withPerson { p -> Actions.block(this, p) } }
        muteV = mute.second
        audioV = spk.second
        audioL = spk.third
        holdV = hold.second
        val r1 = LinearLayout(this)
        val r2 = LinearLayout(this)
        for (x in listOf(mute, keys, spk, hold)) r1.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        for (x in listOf(add, note, block)) r2.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        r2.addView(View(this), LinearLayout.LayoutParams(0, WC, 1f))
        grid.addView(r1, LinearLayout.LayoutParams(MP, WC))
        val r2lp = LinearLayout.LayoutParams(MP, WC)
        r2lp.topMargin = dp(16)
        grid.addView(r2, r2lp)
        card.addView(grid, LinearLayout.LayoutParams(MP, WC))

        padWrap = LinearLayout(this)
        padWrap.orientation = LinearLayout.VERTICAL
        padWrap.visibility = View.GONE
        val hide = label(14f, Ui.accent(this), true)
        hide.text = "Hide keypad"
        hide.setPadding(0, 0, 0, dp(6))
        hide.setOnClickListener { showPad = false; render() }
        padWrap.addView(hide, LinearLayout.LayoutParams(MP, WC))
        pad = KeypadView(
            this, showCall = false, onDigit = { ch -> dtmf(ch) },
            onDark = true, letters = false, bottomRow = false
        )
        padWrap.addView(pad, LinearLayout.LayoutParams(MP, dp(230)))
        card.addView(padWrap, LinearLayout.LayoutParams(MP, WC))
        col.addView(card, LinearLayout.LayoutParams(MP, WC))

        val bar = FrameLayout(this)
        bar.setPadding(dp(24), 0, dp(24), 0)

        val endV = IconView(this, "hang")
        endV.bgOverride = 0xFFE53935.toInt()
        endV.glyphColor = Color.WHITE
        endV.forceCircle = !Ui.square(this)
        endV.setOnClickListener { CallManager.current()?.disconnect() }
        endCell = LinearLayout(this)
        endCell.orientation = LinearLayout.VERTICAL
        endCell.gravity = Gravity.CENTER_HORIZONTAL
        endCell.addView(endV, LinearLayout.LayoutParams(dp(76), dp(76)))
        val endL = label(12f, 0xCCFFFFFF.toInt(), false)
        endL.text = "End call"
        val elp = LinearLayout.LayoutParams(WC, WC)
        elp.topMargin = dp(6)
        endCell.addView(endL, elp)
        bar.addView(endCell, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))

        swipeWrap = LinearLayout(this)
        swipeWrap.orientation = LinearLayout.VERTICAL
        swipeWrap.gravity = Gravity.CENTER_HORIZONTAL
        swipe = SwipeAnswer(
            this,
            { CallManager.current()?.answer(VideoProfile.STATE_AUDIO_ONLY) },
            { CallManager.current()?.reject(false, null) }
        )
        swipeWrap.addView(swipe, LinearLayout.LayoutParams(MP, dp(84)))
        val hint = label(12f, 0x99FFFFFF.toInt(), false)
        hint.text = "\u2039  swipe left to decline  \u00B7  swipe right to answer  \u203A"
        val hlp = LinearLayout.LayoutParams(WC, WC)
        hlp.topMargin = dp(10)
        swipeWrap.addView(hint, hlp)
        swipeWrap.visibility = View.GONE
        bar.addView(swipeWrap, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))

        val barLp = FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM)
        barLp.bottomMargin = dp(28)
        root.addView(bar, barLp)

        CallManager.addListener(listener)
        render()
    }

    private fun startPulse() {
        if (pulsing) return
        pulsing = true
        for ((i, r) in rings.withIndex()) {
            r.visibility = View.VISIBLE
            val a = ObjectAnimator.ofPropertyValuesHolder(
                r,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.75f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.75f),
                PropertyValuesHolder.ofFloat("alpha", 0.5f, 0f)
            )
            a.duration = 2000
            a.repeatCount = ValueAnimator.INFINITE
            a.startDelay = (i * 800).toLong()
            a.start()
            anims.add(a)
        }
    }

    private fun stopPulse() {
        for (a in anims) a.cancel()
        anims.clear()
        for (r in rings) {
            r.visibility = View.INVISIBLE
            r.scaleX = 1f
            r.scaleY = 1f
        }
        pulsing = false
    }

    private fun startAnswerPulse() {
        if (answerAnim != null) return
        val a = ObjectAnimator.ofPropertyValuesHolder(
            swipe.handle,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f)
        )
        a.duration = 700
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.start()
        answerAnim = a
    }

    private fun stopAnswerPulse() {
        answerAnim?.cancel()
        answerAnim = null
        swipe.handle.scaleX = 1f
        swipe.handle.scaleY = 1f
    }

    private fun withPerson(f: (Person) -> Unit) {
        val call = CallManager.current() ?: return
        val n = CallManager.numberOf(call)
        f(cachedPerson ?: Person(n, n))
    }

    private fun dtmf(ch: Char) {
        val c = CallManager.current() ?: return
        if (ch == '+') return
        c.playDtmfTone(ch)
        h.postDelayed({ c.stopDtmfTone() }, 150)
    }

    private fun replies(): List<String> {
        val s = Ui.cfg(this).getString("replies", Ui.defaultReplies)
        val l = s?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
        return if (l.isNullOrEmpty()) Ui.defaultReplies.lines() else l
    }

    private fun sendSms(number: String, text: String) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow SMS permission in the app (step 1) to send replies", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't send the message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replyAndDecline() {
        val call = CallManager.current() ?: return
        val number = CallManager.numberOf(call)
        val list = replies()
        AlertDialog.Builder(this)
            .setTitle("Decline and reply")
            .setItems(list.toTypedArray()) { _, i ->
                sendSms(number, list[i])
                call.reject(false, null)
            }
            .show()
    }

    private fun audioPicker() {
        val svc = CallManager.service ?: return
        val st = svc.callAudioState ?: return
        val mask = st.supportedRouteMask
        val names = ArrayList<String>()
        val routes = ArrayList<Int>()
        if ((mask and CallAudioState.ROUTE_EARPIECE) != 0) {
            names.add("Phone"); routes.add(CallAudioState.ROUTE_EARPIECE)
        }
        if ((mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
            names.add("Wired headset"); routes.add(CallAudioState.ROUTE_WIRED_HEADSET)
        }
        if ((mask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
            names.add("Bluetooth"); routes.add(CallAudioState.ROUTE_BLUETOOTH)
        }
        if ((mask and CallAudioState.ROUTE_SPEAKER) != 0) {
            names.add("Speaker"); routes.add(CallAudioState.ROUTE_SPEAKER)
        }
        if (routes.size < 2) return
        if (routes.size == 2) {
            val next = if (st.route == routes[0]) routes[1] else routes[0]
            svc.setAudioRoute(next)
            render()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Audio output")
            .setItems(names.toTypedArray()) { _, i ->
                svc.setAudioRoute(routes[i])
                h.postDelayed({ render() }, 300)
            }
            .show()
    }

    private fun timer(call: Call): String {
        val start = call.details.connectTimeMillis
        if (start <= 0L) return "00:00"
        val s = (System.currentTimeMillis() - start) / 1000
        val m = s / 60
        val r = s % 60
        return (if (m < 10) "0" else "") + m + ":" + (if (r < 10) "0" else "") + r
    }

    private fun canMerge(c: Call?): Boolean {
        if (c == null) return false
        return (c.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
    }

    private fun render() {
        val call = CallManager.current()
        if (call == null) {
            finish()
            return
        }
        val number = CallManager.numberOf(call)
        var changed = false
        if (number != cachedNumber) {
            cachedNumber = number
            cachedPerson = Repo.lookup(this, number)
            changed = true
        }
        val p = cachedPerson
        val name = p?.name ?: (if (number.isBlank()) "Unknown" else number)
        val note = Store.note(this, number)
        nameT.text = name
        numberT.text = if (p != null) number else ""
        if (note != null) {
            noteT.text = "\uD83D\uDCDD  " + note
            noteT.visibility = View.VISIBLE
        } else {
            noteT.visibility = View.GONE
        }
        avatar.bind(name, p?.contactId)
        if (changed) {
            val base = avatarColor(name)
            root.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.mix(base, dark, 0.62f), 0xFF080B0F.toInt())
            )
        }

        val ringing = call.state == Call.STATE_RINGING
        val dialing = call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING
        statusT.text = when (call.state) {
            Call.STATE_RINGING -> "INCOMING CALL"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "CALLING\u2026"
            Call.STATE_HOLDING -> "ON HOLD"
            Call.STATE_ACTIVE -> timer(call)
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "CALL ENDED"
            else -> ""
        }
        if (ringing || dialing) startPulse() else stopPulse()
        if (ringing) startAnswerPulse() else stopAnswerPulse()

        val other = CallManager.other()
        if (other != null) {
            val on = CallManager.numberOf(other)
            if (on != cachedOtherNumber) {
                cachedOtherNumber = on
                cachedOther = Repo.lookup(this, on)
            }
            val who = cachedOther?.name ?: (if (on.isBlank()) "Unknown" else on)
            val state = when (other.state) {
                Call.STATE_HOLDING -> "on hold"
                Call.STATE_ACTIVE -> "in call"
                else -> "waiting"
            }
            waitT.text = who + "  \u00B7  " + state
            swapB.visibility = if (other.state == Call.STATE_HOLDING && !ringing) View.VISIBLE else View.GONE
            mergeB.visibility = if (!ringing && (canMerge(call) || canMerge(other))) View.VISIBLE else View.GONE
            waitBar.visibility = View.VISIBLE
        } else {
            waitBar.visibility = View.GONE
        }

        card.visibility = if (ringing) View.GONE else View.VISIBLE
        grid.visibility = if (showPad) View.GONE else View.VISIBLE
        padWrap.visibility = if (showPad) View.VISIBLE else View.GONE
        replyB.visibility = if (ringing) View.VISIBLE else View.GONE
        swipeWrap.visibility = if (ringing) View.VISIBLE else View.GONE
        endCell.visibility = if (ringing) View.GONE else View.VISIBLE
        if (ringing && !wasRinging) swipe.reset()
        wasRinging = ringing

        muteV.kind = if (muted) "micoff" else "mic"
        setOn(muteV, muted)
        setOn(holdV, call.state == Call.STATE_HOLDING)
        val route = CallManager.service?.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val loud = route == CallAudioState.ROUTE_SPEAKER
        val bt = route == CallAudioState.ROUTE_BLUETOOTH
        val wired = route == CallAudioState.ROUTE_WIRED_HEADSET
        audioV.kind = if (loud) "sound" else if (bt) "bt" else if (wired) "head" else "phone"
        audioL.text = if (loud) "Speaker" else if (bt) "Bluetooth" else if (wired) "Headset" else "Phone"
        setOn(audioV, loud || bt || wired)
        setProx(call.state == Call.STATE_ACTIVE && !loud && !bt && !wired)
    }

    private fun setProx(on: Boolean) {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (on) {
                if (prox == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    prox = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dialler:prox")
                }
                if (prox?.isHeld == false) prox?.acquire(3600000L)
            } else if (prox?.isHeld == true) {
                prox?.release()
            }
        } catch (e: Exception) { }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ringing = CallManager.current()?.state == Call.STATE_RINGING
        if (ringing && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            try {
                getSystemService(TelecomManager::class.java).silenceRinger()
            } catch (e: Exception) { }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        h.removeCallbacks(tick)
        setProx(false)
        stopPulse()
        stopAnswerPulse()
        super.onPause()
    }

    override fun onDestroy() {
        CallManager.removeListener(listener)
        setProx(false)
        super.onDestroy()
    }
}
