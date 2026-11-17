package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings

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
        val ready = Settings.canDrawOverlays(this) &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (ready) {
            try {
                val i = Intent(this, OverlayService::class.java)
                i.action = OverlayService.ACTION_OPEN
                startForegroundService(i)
            } catch (e: Exception) {
                AppLog.w(this, "LaunchActivity", e)
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            startActivity(Intent(this, MainActivity::class.java))
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
