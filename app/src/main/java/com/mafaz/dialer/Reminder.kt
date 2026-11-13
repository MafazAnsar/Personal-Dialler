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
}
