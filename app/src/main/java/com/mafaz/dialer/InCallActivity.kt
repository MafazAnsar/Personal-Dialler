package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.TextView
import android.widget.Toast

class InCallActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var avatar: AvatarView
    private lateinit var nameT: TextView
    private lateinit var numberT: TextView
    private lateinit var noteT: TextView
    private lateinit var statusT: TextView
    private lateinit var waitBar: LinearLayout
    private lateinit var waitT: TextView
    private lateinit var swapB: TextView
    private lateinit var mergeB: TextView
    private lateinit var grid: LinearLayout
    private lateinit var pad: KeypadView
    private lateinit var replyB: TextView
    private lateinit var declineB: TextView
    private lateinit var answerB: TextView
    private lateinit var endB: TextView
    private lateinit var muteC: TextView
    private lateinit var audioC: TextView
    private lateinit var audioL: TextView
    private lateinit var holdC: TextView

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

    private fun fnBtn(glyph: String, name: String, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        val c = TextView(this)
        c.text = glyph
        c.textSize = 22f
        c.gravity = Gravity.CENTER
        c.setTextColor(Color.WHITE)
        c.background = Ui.btn(this, 0xFF2B3844.toInt())
        c.setOnClickListener { onClick() }
        cell.addView(c, LinearLayout.LayoutParams(dp(60), dp(60)))
        val l = label(12f, 0xB3FFFFFF.toInt(), false)
        l.text = name
        val lp = LinearLayout.LayoutParams(WC, WC)
        lp.topMargin = dp(4)
        cell.addView(l, lp)
        return Pair(cell, c)
    }

    private fun bigBtn(color: Int, rotate: Float, onClick: () -> Unit): TextView {
        val c = TextView(this)
        c.text = "\uD83D\uDCDE"
        c.textSize = 28f
        c.gravity = Gravity.CENTER
        c.rotation = rotate
        c.background = Ui.btn(this, color)
        c.setOnClickListener { onClick() }
        return c
    }

    private fun smallBtn(text: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
        b.setTypeface(null, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.background = Ui.round(Ui.accent(this), dp(8))
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F151B.toInt(), 0xFF1D2A36.toInt())
        )
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(20), dp(64), dp(20), dp(32))
        root.addView(col, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)

        avatar = AvatarView(this)
        col.addView(avatar, LinearLayout.LayoutParams(dp(104), dp(104)))
        nameT = label(28f, Color.WHITE, true)
        numberT = label(16f, 0xB3FFFFFF.toInt(), false)
        noteT = label(14f, Ui.accent(this), false)
        statusT = label(18f, 0xFFFFFFFF.toInt(), false)
        val nlp = LinearLayout.LayoutParams(MP, WC)
        nlp.topMargin = dp(14)
        col.addView(nameT, nlp)
        col.addView(numberT, LinearLayout.LayoutParams(MP, WC))
        col.addView(noteT, LinearLayout.LayoutParams(MP, WC))
        val slp = LinearLayout.LayoutParams(MP, WC)
        slp.topMargin = dp(6)
        col.addView(statusT, slp)

        waitBar = LinearLayout(this)
        waitBar.orientation = LinearLayout.HORIZONTAL
        waitBar.gravity = Gravity.CENTER_VERTICAL
        waitBar.setPadding(dp(14), dp(8), dp(8), dp(8))
        waitBar.background = Ui.round(0xFF2B3844.toInt(), dp(10))
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
        wlp.topMargin = dp(10)
        col.addView(waitBar, wlp)

        val middle = FrameLayout(this)
        col.addView(middle, LinearLayout.LayoutParams(MP, 0, 1f))

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER
        val mute = fnBtn("\uD83C\uDFA4", "Mute") { muted = !muted; CallManager.service?.setMuted(muted); render() }
        val keys = fnBtn("\u2328", "Keypad") { showPad = true; render() }
        val spk = fnBtn("\uD83D\uDCF1", "Phone") { audioPicker() }
        val hold = fnBtn("\u23F8", "Hold") {
            val c = CallManager.current()
            if (c != null) { if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold() }
        }
        val add = fnBtn("\u2795", "Add call") { startActivity(Intent(this, DialActivity::class.java)) }
        val note = fnBtn("\uD83D\uDCDD", "Note") { withPerson { p -> Actions.note(this, p) } }
        val block = fnBtn("\uD83D\uDEAB", "Block") { withPerson { p -> Actions.block(this, p) } }
        muteC = mute.second
        audioC = spk.second
        audioL = spk.first.getChildAt(1) as TextView
        holdC = hold.second
        val r1 = LinearLayout(this)
        val r2 = LinearLayout(this)
        for (x in listOf(mute, keys, spk, hold)) r1.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        for (x in listOf(add, note, block)) r2.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        r2.addView(View(this), LinearLayout.LayoutParams(0, WC, 1f))
        grid.addView(r1, LinearLayout.LayoutParams(MP, WC))
        val r2lp = LinearLayout.LayoutParams(MP, WC)
        r2lp.topMargin = dp(18)
        grid.addView(r2, r2lp)
        middle.addView(grid, FrameLayout.LayoutParams(MP, MP))

        pad = KeypadView(this, showCall = false, onDigit = { ch -> dtmf(ch) })
        pad.visibility = View.GONE
        middle.addView(pad, FrameLayout.LayoutParams(MP, MP))

        replyB = TextView(this)
        replyB.text = "\u2709  Reply with message"
        replyB.textSize = 14f
        replyB.gravity = Gravity.CENTER
        replyB.setTextColor(Color.WHITE)
        replyB.setPadding(dp(16), dp(10), dp(16), dp(10))
        replyB.background = Ui.round(0xFF2B3844.toInt(), dp(10))
        replyB.setOnClickListener { replyAndDecline() }
        replyB.visibility = View.GONE
        val rlp = LinearLayout.LayoutParams(WC, WC)
        rlp.bottomMargin = dp(16)
        col.addView(replyB, rlp)

        val bottom = LinearLayout(this)
        bottom.gravity = Gravity.CENTER
        declineB = bigBtn(0xFFD32F2F.toInt(), 135f) { CallManager.current()?.reject(false, null) }
        answerB = bigBtn(0xFF2E9E4F.toInt(), 0f) { CallManager.current()?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        endB = bigBtn(0xFFD32F2F.toInt(), 135f) {
            if (showPad) { showPad = false; render() } else CallManager.current()?.disconnect()
        }
        val blp = LinearLayout.LayoutParams(dp(72), dp(72))
        blp.setMargins(dp(36), 0, dp(36), 0)
        bottom.addView(declineB, blp)
        bottom.addView(endB, LinearLayout.LayoutParams(blp))
        bottom.addView(answerB, LinearLayout.LayoutParams(blp))
        col.addView(bottom, LinearLayout.LayoutParams(MP, WC))

        CallManager.addListener(listener)
        render()
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
        if (number != cachedNumber) {
            cachedNumber = number
            cachedPerson = Repo.lookup(this, number)
        }
        val p = cachedPerson
        val name = p?.name ?: (if (number.isBlank()) "Unknown" else number)
        val note = Store.note(this, number)
        nameT.text = name
        numberT.text = if (p != null) number else ""
        noteT.text = if (note != null) "\uD83D\uDCDD " + note else ""
        avatar.bind(name, p?.contactId)

        val ringing = call.state == Call.STATE_RINGING
        statusT.text = when (call.state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling\u2026"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_ACTIVE -> timer(call)
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
            else -> ""
        }

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

        grid.visibility = if (!ringing && !showPad) View.VISIBLE else View.GONE
        pad.visibility = if (!ringing && showPad) View.VISIBLE else View.GONE
        replyB.visibility = if (ringing) View.VISIBLE else View.GONE
        declineB.visibility = if (ringing) View.VISIBLE else View.GONE
        answerB.visibility = if (ringing) View.VISIBLE else View.GONE
        endB.visibility = if (ringing) View.GONE else View.VISIBLE

        val on = Ui.accent(this)
        val off = 0xFF2B3844.toInt()
        muteC.background = Ui.btn(this, if (muted) on else off)
        holdC.background = Ui.btn(this, if (call.state == Call.STATE_HOLDING) on else off)
        val route = CallManager.service?.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val loud = route == CallAudioState.ROUTE_SPEAKER
        val bt = route == CallAudioState.ROUTE_BLUETOOTH
        val wired = route == CallAudioState.ROUTE_WIRED_HEADSET
        audioC.text = if (loud) "\uD83D\uDD0A" else if (bt || wired) "\uD83C\uDFA7" else "\uD83D\uDCF1"
        audioL.text = if (loud) "Speaker" else if (bt) "Bluetooth" else if (wired) "Headset" else "Phone"
        audioC.background = Ui.btn(this, if (loud || bt || wired) on else off)
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
        super.onPause()
    }

    override fun onDestroy() {
        CallManager.removeListener(listener)
        setProx(false)
        super.onDestroy()
    }
}
