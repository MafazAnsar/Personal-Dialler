package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings

// The app icon: opens the dialler panel straight away. Falls back to settings until set up.
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The launcher opens this Activity with its own window transition. The overlay panel
        // also animates open immediately after, so suppress the activity transition to avoid
        // overlapping animations when the app icon is tapped.
        window.setWindowAnimations(0)

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
        finish()
    }
}
