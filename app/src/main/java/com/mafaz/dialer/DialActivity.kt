package com.mafaz.dialer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

// Phone links (tel:), the default-dialler hook and the Keypad shortcut land here.
// There is no screen of its own: it opens the keypad inside the panel.
class DialActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(this, PanelActivity::class.java)
        i.putExtra(PanelActivity.EXTRA_KEYPAD, true)
        val data = intent?.data
        if (data != null && data.scheme == "tel") {
            i.putExtra(PanelActivity.EXTRA_NUMBER, Uri.decode(data.schemeSpecificPart))
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            AppLog.w(this, "DialActivity", e)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
