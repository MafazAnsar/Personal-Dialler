package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var sideBtn: Button
    private lateinit var swatches: LinearLayout
    private lateinit var simBtn: Button
    private lateinit var actionsBox: LinearLayout

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Ui.cfg(this)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(36), dp(20), dp(32))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        heading("Personal Dialler", 26f)
        text("Floating dots shortcut and a private phone app. Nothing leaves your phone.")
        addBtn("Open keypad") { startActivity(Intent(this, DialActivity::class.java)) }
        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, dp(12), 0, dp(4))
        root.addView(status)

        heading("Setup", 18f)
        addBtn("1. Allow contacts, call log and phone") {
            val p = arrayListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
            requestPermissions(p.toTypedArray(), 1)
        }
        addBtn("2. Allow display over other apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        addBtn("3. Stop battery restrictions") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        addBtn("4. Make this my Phone app") { requestRole(RoleManager.ROLE_DIALER) }
        addBtn("5. Use for caller ID and call blocking") { requestRole(RoleManager.ROLE_CALL_SCREENING) }
        addBtn("6. Allow full-screen call alerts (screen off)") {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(i)
            }
        }

        heading("Floating dots", 18f)
        toggle = addBtn("Start floating dots") { toggleService() }
        sideBtn = addBtn("Side") {
            val now = prefs.getString("side", "left")
            prefs.edit().putString("side", if (now == "left") "right" else "left").apply()
            restartService()
            refresh()
        }
        seek("Dot size", "dotsize", 2, 1)
        seek("Dot opacity", "dotalpha", 100, 80)
        seek("Panel width", "panelw", 10, 3)

        heading("Look", 18f)
        switch("Dark theme", "dark", true)
        switch("Square buttons (off = rounded)", "square", true)
        seek("Keypad opacity (lower = more see-through)", "keyalpha", 100, 85)
        switch("List starts from the bottom (newest nearest your thumb)", "revlist", false)
        text("Accent colour")
        swatches = LinearLayout(this)
        root.addView(swatches)
        buildSwatches()
        switch("Show popup after calls", "postcall", true)

        heading("Calls", 18f)
        simBtn = addBtn("SIM") {
            val order = listOf("ask", "0", "1", "sys")
            val now = prefs.getString("sim", "ask") ?: "ask"
            prefs.edit().putString("sim", order[(order.indexOf(now) + 1) % order.size]).apply()
            refresh()
        }
        switch("Block calls from numbers not in your contacts (careful: may block delivery or hospital calls)", "blockunknown", false)
        switch("Block hidden numbers", "blockhidden", false)
        text("Quick replies when declining a call (one per line):")
        val rep = EditText(this)
        rep.setText(prefs.getString("replies", Ui.defaultReplies))
        rep.minLines = 4
        rep.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        rep.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("replies", s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(rep)

        heading("Panel actions", 18f)
        text("Tick the icons you want and use the arrows to change the order.")
        actionsBox = LinearLayout(this)
        actionsBox.orientation = LinearLayout.VERTICAL
        root.addView(actionsBox)
        buildActionRows()

        heading("Text", 18f)
        seek("Text size in lists (small, normal, large)", "fontsize", 2, 1)

        heading("Backup and support", 18f)
        addBtn("Back up notes, blocks and settings") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "dialler-backup.json")
            startActivityForResult(i, 21)
        }
        addBtn("Restore from a backup file") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 22)
        }
        addBtn("Share last error report") { shareCrash() }

        heading("WhatsApp and Telegram", 18f)
        text("Default country code for numbers starting with 0 (digits only):")
        val cc = EditText(this)
        cc.inputType = InputType.TYPE_CLASS_NUMBER
        cc.setText(prefs.getString("cc", "94"))
        cc.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("cc", s?.toString()?.filter { it.isDigit() } ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(cc)

        val tip = TextView(this)
        tip.text = "If a permission switch is greyed out: Settings > Apps > Personal Dialler > three-dot menu > Allow restricted settings."
        tip.textSize = 12f
        tip.setPadding(0, dp(20), 0, 0)
        root.addView(tip)
    }

    private fun heading(t: String, size: Float) {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTypeface(null, Typeface.BOLD)
        v.setPadding(0, dp(18), 0, dp(4))
        root.addView(v)
    }

    private fun text(t: String) {
        val v = TextView(this)
        v.text = t
        v.setPadding(0, dp(6), 0, dp(4))
        root.addView(v)
    }

    private fun addBtn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        root.addView(b)
        return b
    }

    private fun switch(label: String, key: String, def: Boolean) {
        val s = Switch(this)
        s.text = label
        s.isChecked = prefs.getBoolean(key, def)
        s.setPadding(0, dp(8), 0, dp(8))
        s.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(key, on).apply()
            restartService()
        }
        root.addView(s)
    }

    private fun seek(label: String, key: String, max: Int, def: Int) {
        text(label)
        val sb = SeekBar(this)
        sb.max = max
        sb.progress = prefs.getInt(key, def)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) prefs.edit().putInt(key, p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                restartService()
            }
        })
        root.addView(sb)
    }

    private fun buildSwatches() {
        swatches.removeAllViews()
        val sel = prefs.getInt("accent", 0)
        for (i in Ui.accents.indices) {
            val t = TextView(this)
            t.text = if (i == sel) "\u2713" else ""
            t.gravity = Gravity.CENTER
            t.textSize = 18f
            t.setTextColor(Color.BLACK)
            t.background = Ui.circle(Ui.accents[i])
            t.setOnClickListener {
                prefs.edit().putInt("accent", i).apply()
                buildSwatches()
                restartService()
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.setMargins(0, dp(4), dp(10), dp(4))
            swatches.addView(t, lp)
        }
    }

    private fun requestRole(role: String) {
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(role)) {
            Toast.makeText(this, "Not available on this phone", Toast.LENGTH_LONG).show()
        } else if (rm.isRoleHeld(role)) {
            Toast.makeText(this, "Already set", Toast.LENGTH_SHORT).show()
        } else {
            startActivityForResult(rm.createRequestRoleIntent(role), 10)
        }
    }

    private fun toast(t: String) {
        Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    }

    private fun shareCrash() {
        val t = CrashLog.read(this)
        if (t == null) {
            toast("No error report saved")
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, t)
        startActivity(Intent.createChooser(i, "Share error report"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 21) {
                contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray()) }
                toast("Backup saved")
            } else if (requestCode == 22) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                if (Backup.import(this, text)) {
                    toast("Restored. Close and reopen the app.")
                } else {
                    toast("That file is not a valid backup")
                }
            }
        } catch (e: Exception) {
            toast("Something went wrong")
        }
    }

    private fun buildActionRows() {
        actionsBox.removeAllViews()
        val on = ActionDefs.enabled(this)
        val order = on + ActionDefs.all.filter { it !in on }
        for (id in order) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val cb = CheckBox(this)
            cb.text = ActionDefs.title(id)
            cb.isChecked = id in on
            cb.setOnCheckedChangeListener { _, checked ->
                val cur = ActionDefs.enabled(this).toMutableList()
                if (checked) {
                    if (id !in cur) cur.add(id)
                } else {
                    cur.remove(id)
                }
                if (cur.isNotEmpty()) {
                    ActionDefs.save(this, cur)
                    restartService()
                }
                actionsBox.post { buildActionRows() }
            }
            row.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val up = TextView(this)
            up.text = "\u25B2"
            up.textSize = 20f
            up.setPadding(dp(14), dp(6), dp(14), dp(6))
            up.setOnClickListener { move(id, -1) }
            val down = TextView(this)
            down.text = "\u25BC"
            down.textSize = 20f
            down.setPadding(dp(14), dp(6), dp(14), dp(6))
            down.setOnClickListener { move(id, 1) }
            row.addView(up)
            row.addView(down)
            actionsBox.addView(row)
        }
    }

    private fun move(id: String, d: Int) {
        val cur = ActionDefs.enabled(this).toMutableList()
        val i = cur.indexOf(id)
        val j = i + d
        if (i < 0 || j < 0 || j >= cur.size) return
        java.util.Collections.swap(cur, i, j)
        ActionDefs.save(this, cur)
        restartService()
        buildActionRows()
    }

    private fun simLabel(): String = when (prefs.getString("sim", "ask")) {
        "0" -> "SIM 1"
        "1" -> "SIM 2"
        "sys" -> "Phone default"
        else -> "Ask every time"
    }

    private fun restartService() {
        if (!prefs.getBoolean("enabled", false)) return
        stopService(Intent(this, OverlayService::class.java))
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    private fun toggleService() {
        if (prefs.getBoolean("enabled", false)) {
            stopService(Intent(this, OverlayService::class.java))
            prefs.edit().putBoolean("enabled", false).apply()
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow display over other apps first (step 2)", Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            prefs.edit().putBoolean("enabled", true).apply()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun mark(ok: Boolean, warn: Boolean = false) =
        if (ok) "\u2705" else if (warn) "\u26A0\uFE0F" else "\u274C"

    private fun refresh() {
        val perms = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        val rm = getSystemService(RoleManager::class.java)
        val phone = rm.isRoleHeld(RoleManager.ROLE_DIALER)
        val screen = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val fsi = if (Build.VERSION.SDK_INT >= 34)
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true
        status.text = mark(perms) + " Contacts, call log, phone\n" +
            mark(overlay) + " Display over other apps\n" +
            mark(battery, true) + " Battery unrestricted\n" +
            mark(phone, true) + " Default Phone app\n" +
            mark(screen, true) + " Caller ID and blocking\n" +
            mark(fsi) + " Full-screen call alerts"
        toggle.text = if (prefs.getBoolean("enabled", false)) "Stop floating dots" else "Start floating dots"
        sideBtn.text = "Dots on the " + prefs.getString("side", "left") + " edge (tap to switch)"
        simBtn.text = "SIM for calls: " + simLabel() + " (tap to change)"
    }
}
