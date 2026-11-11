package com.mafaz.dialer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class InCallActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var avatar: AvatarView
    private lateinit var nameT: TextView
    private lateinit var numberT: TextView
    private lateinit var noteT: TextView
    private lateinit var statusT: TextView
    private lateinit var grid: LinearLayout
    private lateinit var pad: KeypadView
    private lateinit var declineB: TextView
    private lateinit var answerB: TextView
    private lateinit var endB: TextView
    private lateinit var muteC: TextView
    private lateinit var padC: TextView
    private lateinit var spkC: TextView
    private lateinit var holdC: TextView

    private var muted = false
    private var speaker = false
    private var showPad = false
    private var cachedNumber: String? = null
    private var cachedPerson: Person? = null
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
        c.textSize = 24f
        c.gravity = Gravity.CENTER
        c.setTextColor(Color.WHITE)
        c.background = Ui.circle(0xFF2B3844.toInt())
        c.setOnClickListener { onClick() }
        cell.addView(c, LinearLayout.LayoutParams(dp(64), dp(64)))
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
        c.background = Ui.circle(color)
        c.setOnClickListener { onClick() }
        return c
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
        col.setPadding(dp(24), dp(72), dp(24), dp(40))
        root.addView(col, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)

        avatar = AvatarView(this)
        col.addView(avatar, LinearLayout.LayoutParams(dp(112), dp(112)))
        nameT = label(28f, Color.WHITE, true)
        numberT = label(16f, 0xB3FFFFFF.toInt(), false)
        noteT = label(14f, Ui.accent(this), false)
        statusT = label(18f, 0xFFFFFFFF.toInt(), false)
        val nlp = LinearLayout.LayoutParams(MP, WC)
        nlp.topMargin = dp(16)
        col.addView(nameT, nlp)
        col.addView(numberT, LinearLayout.LayoutParams(MP, WC))
        col.addView(noteT, LinearLayout.LayoutParams(MP, WC))
        val slp = LinearLayout.LayoutParams(MP, WC)
        slp.topMargin = dp(8)
        col.addView(statusT, slp)

        val middle = FrameLayout(this)
        col.addView(middle, LinearLayout.LayoutParams(MP, 0, 1f))

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER
        val mute = fnBtn("\uD83C\uDFA4", "Mute") { muted = !muted; CallManager.service?.setMuted(muted); render() }
        val keys = fnBtn("\u2328", "Keypad") { showPad = true; render() }
        val spk = fnBtn("\uD83D\uDD0A", "Speaker") {
            speaker = !speaker
            CallManager.service?.setAudioRoute(
                if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            )
            render()
        }
        val hold = fnBtn("\u23F8", "Hold") {
            val c = CallManager.current()
            if (c != null) { if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold() }
        }
        val note = fnBtn("\uD83D\uDCDD", "Note") { withPerson { p -> Actions.note(this, p) } }
        val block = fnBtn("\uD83D\uDEAB", "Block") { withPerson { p -> Actions.block(this, p) } }
        muteC = mute.second; padC = keys.second; spkC = spk.second; holdC = hold.second
        val r1 = LinearLayout(this)
        val r2 = LinearLayout(this)
        for (x in listOf(mute, keys, spk)) r1.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        for (x in listOf(hold, note, block)) r2.addView(x.first, LinearLayout.LayoutParams(0, WC, 1f))
        grid.addView(r1, LinearLayout.LayoutParams(MP, WC))
        val r2lp = LinearLayout.LayoutParams(MP, WC)
        r2lp.topMargin = dp(20)
        grid.addView(r2, r2lp)
        middle.addView(grid, FrameLayout.LayoutParams(MP, MP))

        pad = KeypadView(this, showCall = false, onDigit = { ch -> dtmf(ch) })
        pad.visibility = View.GONE
        middle.addView(pad, FrameLayout.LayoutParams(MP, MP))

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

    private fun timer(call: Call): String {
        val start = call.details.connectTimeMillis
        if (start <= 0L) return "00:00"
        val s = (System.currentTimeMillis() - start) / 1000
        val m = s / 60
        val r = s % 60
        return (if (m < 10) "0" else "") + m + ":" + (if (r < 10) "0" else "") + r
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
        grid.visibility = if (!ringing && !showPad) View.VISIBLE else View.GONE
        pad.visibility = if (!ringing && showPad) View.VISIBLE else View.GONE
        declineB.visibility = if (ringing) View.VISIBLE else View.GONE
        answerB.visibility = if (ringing) View.VISIBLE else View.GONE
        endB.visibility = if (ringing) View.GONE else View.VISIBLE

        val on = Ui.accent(this)
        val off = 0xFF2B3844.toInt()
        muteC.background = Ui.circle(if (muted) on else off)
        spkC.background = Ui.circle(if (speaker) on else off)
        holdC.background = Ui.circle(if (call.state == Call.STATE_HOLDING) on else off)
        setProx(call.state == Call.STATE_ACTIVE && !speaker)
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
