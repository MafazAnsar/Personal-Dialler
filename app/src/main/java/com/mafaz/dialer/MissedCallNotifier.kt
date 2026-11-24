package com.mafaz.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object MissedCallNotifier {
    private const val CHANNEL_ID = "missed_calls"
    private const val GROUP_KEY = "com.mafaz.dialer.MISSED_CALLS"

    fun show(ctx: Context, name: String, number: String) {
        if (number.isBlank()) return

        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Missed calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for calls you did not answer"
            }
        )

        val id = (System.currentTimeMillis() and 0x7fffffff).toInt().coerceAtLeast(1)
        val title = name.ifBlank { number }
        val open = PendingIntent.getActivity(
            ctx,
            id,
            Intent(ctx, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val callbackIntent = Intent(ctx, CallActionReceiver::class.java)
            .setAction("missed_callback")
            .putExtra("number", number)
            .putExtra("name", title)
        val callback = PendingIntent.getBroadcast(
            ctx,
            id,
            callbackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Missed call: $title")
            .setContentText(number)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, android.R.drawable.ic_menu_call),
                    "Call back",
                    callback
                ).build()
            )
            .build()

        manager.notify(id, notification)
    }
}
