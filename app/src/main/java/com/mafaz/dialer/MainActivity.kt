package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var sideBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(36), dp(20), dp(24))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this)
        title.text = "Personal Dialler"
        title.textSize = 24f
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        val how = TextView(this)
        how.text = "Floating dots stay on the screen edge. Swipe them inward, hold a contact, drag it onto Call, SMS, WhatsApp, Telegram or Email. Nothing leaves your phone."
        how.setPadding(0, dp(8), 0, dp(8))
        root.addView(how)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, dp(12), 0, dp(12))
        root.addView(status)

        addButton(root, "1. Allow contacts, call log and phone") {
            val p = arrayListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE
            )
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }
        addButton(root, "2. Allow display over other apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        addButton(root, "3. Stop battery restrictions") {
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
        toggle = addButton(root, "Start floating dots") { toggleService() }
        sideBtn = addButton(root, "Side") {
            val now = prefs.getString("side", "left")
            prefs.edit().putString("side", if (now == "left") "right" else "left").apply()
            if (prefs.getBoolean("enabled", false)) {
                stopService(Intent(this, OverlayService::class.java))
                startForegroundService(Intent(this, OverlayService::class.java))
            }
            refresh()
        }

        val ccLabel = TextView(this)
        ccLabel.text = "Default country code for WhatsApp and Telegram (digits only):"
        ccLabel.setPadding(0, dp(16), 0, 0)
        root.addView(ccLabel)
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

    private fun addButton(root: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        root.addView(b)
        return b
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    private fun refresh() {
        val perms = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        status.text = (if (perms) "\u2705" else "\u274C") + " Contacts, call log, phone\n" +
            (if (overlay) "\u2705" else "\u274C") + " Display over other apps\n" +
            (if (battery) "\u2705" else "\u26A0\uFE0F") + " Battery unrestricted"
        toggle.text = if (prefs.getBoolean("enabled", false)) "Stop floating dots" else "Start floating dots"
        sideBtn.text = "Dots on the " + prefs.getString("side", "left") + " edge (tap to switch)"
    }
}