package com.mafaz.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("enabled", false) && Settings.canDrawOverlays(c)) {
            try {
                c.startForegroundService(Intent(c, OverlayService::class.java))
            } catch (e: Exception) { }
        }
    }
}