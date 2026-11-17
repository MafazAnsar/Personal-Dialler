package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

// The app icon. It stays invisible while Android plays its own launch animation, then opens the
// dialler panel once that has finished, so the two animations never fight each other.
class LaunchActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        // Safety net in case window focus never arrives (for example the screen turned off).
        h.postDelayed({ go() }, 700)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) go()
    }

    private fun go() {
        if (done) return
        done = true
        val ready = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        try {
            startActivity(Intent(this, if (ready) PanelActivity::class.java else MainActivity::class.java))
        } catch (e: Exception) {
            AppLog.w(this, "LaunchActivity", e)
        }
        // Let the panel appear first, then drop this invisible window without any animation.
        h.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, 250)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
