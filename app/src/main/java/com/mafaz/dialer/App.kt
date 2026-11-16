package com.mafaz.dialer

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
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
