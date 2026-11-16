package com.mafaz.dialer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val number = i.getStringExtra("number") ?: return
        val name = i.getStringExtra("name") ?: number
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("reminders", "Call back reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        dial.setPackage(c.packageName)
        val pi = PendingIntent.getActivity(
            c, number.hashCode(), dial, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, "reminders")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Call back " + name)
            .setContentText(number)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(number.hashCode(), n)
    }
}

object Reminder {
    fun schedule(ctx: Context, number: String, name: String, minutes: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("number", number)
        i.putExtra("name", name)
        val pi = PendingIntent.getBroadcast(
            ctx, number.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60000L, pi)
    }

    val labels = arrayOf("In 15 minutes", "In 1 hour", "In 3 hours", "Tomorrow at 9 am")

    fun minutesFor(choice: Int): Long = when (choice) {
        0 -> 15L
        1 -> 60L
        2 -> 180L
        else -> {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_YEAR, 1)
            c.set(java.util.Calendar.HOUR_OF_DAY, 9)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            (c.timeInMillis - System.currentTimeMillis()) / 60000L
        }
    }
}
