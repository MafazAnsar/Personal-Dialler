package com.mafaz.dialer

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

class DialActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val MP = ViewGroup.LayoutParams.MATCH_PARENT
        val WC = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.solid(this))
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 40), Ui.dp(this, 16), Ui.dp(this, 24))

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "Dialler"
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Ui.text(this))
        bar.addView(title, LinearLayout.LayoutParams(0, WC, 1f))
        val gear = TextView(this)
        gear.text = "\u2699"
        gear.textSize = 26f
        gear.setTextColor(Ui.sub(this))
        gear.setPadding(Ui.dp(this, 12), 0, 0, 0)
        gear.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        bar.addView(gear)
        root.addView(bar, LinearLayout.LayoutParams(MP, WC))

        val pad = KeypadView(this, showCall = true, onCall = { n ->
            if (n.isNotBlank()) {
                Actions.call(this, Person(n, n))
                finish()
            }
        })
        val data = intent?.data
        if (data != null && data.scheme == "tel") pad.setNumber(Uri.decode(data.schemeSpecificPart))
        root.addView(pad, LinearLayout.LayoutParams(MP, 0, 1f))
        setContentView(root)

        thread {
            val all = Repo.contacts(this)
            runOnUiThread { pad.contacts = all }
        }
    }
}
