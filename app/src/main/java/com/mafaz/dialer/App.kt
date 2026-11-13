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
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "Time: " + Date() + "\nThread: " + t.name + "\n" + sw.toString()
                File(filesDir, "crash.txt").writeText(text)
            } catch (x: Exception) { }
            old?.uncaughtException(t, e)
        }
    }
}

object CrashLog {
    fun read(ctx: Context): String? {
        val f = File(ctx.filesDir, "crash.txt")
        return if (f.exists()) f.readText().take(12000) else null
    }
}
