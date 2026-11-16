package com.mafaz.dialer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        try {
            val sm = getSystemService(ShortcutManager::class.java)
            val settings = Intent(this, MainActivity::class.java)
            settings.action = Intent.ACTION_VIEW
            val keypad = Intent(this, DialActivity::class.java)
            keypad.action = Intent.ACTION_VIEW
            val s1 = ShortcutInfo.Builder(this, "settings")
                .setShortLabel("Settings")
                .setLongLabel("Dialler settings")
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_preferences))
                .setIntent(settings)
                .build()
            val s2 = ShortcutInfo.Builder(this, "keypad")
                .setShortLabel("Keypad")
                .setLongLabel("Open the keypad")
                .setIcon(Icon.createWithResource(this, android.R.drawable.sym_action_call))
                .setIntent(keypad)
                .build()
            sm.dynamicShortcuts = listOf(s1, s2)
        } catch (e: Exception) {
            android.util.Log.w("App", "could not publish shortcuts", e)
        }
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "CRASH " + Date() + "\nThread: " + t.name + "\n" + sw.toString() + "\n"
                AppLog.append(filesDir, text)
            } catch (x: Exception) {
                android.util.Log.w("App", "could not write the crash report", x)
            }
            old?.uncaughtException(t, e)
        }
    }
}

// Appends warnings and crashes to crash.txt, capped at 32 KB (oldest text is dropped first).
object AppLog {
    private const val MAX = 32 * 1024
    private val lock = Any()

    fun append(dir: File, text: String) {
        synchronized(lock) {
            try {
                val f = File(dir, "crash.txt")
                var cur = if (f.exists()) f.readText() else ""
                cur += text
                if (cur.length > MAX) cur = cur.takeLast(MAX * 3 / 4)
                f.writeText(cur)
            } catch (e: Exception) {
                android.util.Log.w("AppLog", "could not append to crash.txt", e)
            }
        }
    }

    fun w(ctx: Context, where: String, e: Throwable) {
        android.util.Log.w(where, e)
        append(ctx.filesDir, Date().toString() + "  WARN " + where + ": " + e + "\n")
    }
}

object CrashLog {
    fun read(ctx: Context): String? {
        val f = File(ctx.filesDir, "crash.txt")
        return if (f.exists()) f.readText().takeLast(12000) else null
    }
}
