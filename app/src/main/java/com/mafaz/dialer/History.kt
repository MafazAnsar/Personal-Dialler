package com.mafaz.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.text.format.DateFormat

class Hist(val rows: List<String>, val count: Int, val total: Long)

object History {
    fun fmt(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        val mm = (if (m < 10 && h > 0) "0" else "") + m
        val rr = (if (r < 10) "0" else "") + r
        return if (h > 0) h.toString() + ":" + mm + ":" + rr else mm + ":" + rr
    }

    fun load(ctx: Context, number: String): Hist {
        val rows = ArrayList<String>()
        var total = 0L
        var count = 0
        val key = Store.key(number)
        if (key.isNotEmpty() &&
            ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        ) {
            val proj = arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
            )
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                var scanned = 0
                while (c.moveToNext() && scanned < 4000) {
                    scanned++
                    val n = c.getString(0) ?: continue
                    if (Store.key(n) != key) continue
                    count++
                    val dur = c.getLong(3)
                    total += dur
                    if (rows.size < 60) {
                        val icon = when (c.getInt(1)) {
                            1 -> "\u2199"
                            2 -> "\u2197"
                            3 -> "\u2716"
                            5 -> "\u2298"
                            else -> "\u2022"
                        }
                        val whenText = DateFormat.format("d MMM  HH:mm", c.getLong(2)).toString()
                        rows.add(icon + "  " + whenText + (if (dur > 0) "   " + fmt(dur) else ""))
                    }
                }
            }
        }
        return Hist(rows, count, total)
    }
}
