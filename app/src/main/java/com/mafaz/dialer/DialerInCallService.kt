package com.mafaz.dialer

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.PowerManager
import android.telecom.CallAudioState
import android.telecom.Call
import android.telecom.InCallService

class DialerInCallService : InCallService() {

    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.notifyChanged()
            updateNotification()
            if (state == Call.STATE_ACTIVE) openUi()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallManager.notifyChanged()
        }
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        CallManager.service = this
        CallManager.calls.add(call)
        CallManager.notifyChanged()
        updateNotification()
        if (call.state != Call.STATE_RINGING || screenOffOrLocked()) openUi()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.notifyChanged()
    }

    private fun screenOffOrLocked(): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        return km.isKeyguardLocked || !pm.isInteractive
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(cb)
        CallManager.calls.remove(call)
        CallManager.notifyChanged()
        updateNotification()
        PostCall.show(this, CallManager.numberOf(call))
        if (CallManager.calls.isEmpty()) CallManager.service = null
    }

    private fun openUi() {
        try {
            val i = Intent(this, InCallActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) { }
    }

    private fun act(title: String, action: String, icon: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(this, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val call = CallManager.current()
        if (call == null) {
            nm.cancel(NOTIF_ID)
            return
        }
        val incoming = NotificationChannel("incoming", "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
        incoming.setSound(null, null)
        incoming.enableVibration(false)
        nm.createNotificationChannel(incoming)
        nm.createNotificationChannel(
            NotificationChannel("ongoing", "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
        )
        val number = CallManager.numberOf(call)
        val who = Repo.lookup(this, number)?.name ?: (if (number.isBlank()) "Unknown" else number)
        val ringing = call.state == Call.STATE_RINGING
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, InCallActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, if (ringing) "incoming" else "ongoing")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(who)
            .setContentText(if (ringing) "Incoming call" else "Ongoing call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
        if (ringing) {
            b.setFullScreenIntent(open, true)
            b.addAction(act("Decline", "decline", android.R.drawable.ic_menu_close_clear_cancel))
            b.addAction(act("Answer", "answer", android.R.drawable.ic_menu_call))
        } else {
            b.addAction(act("Hang up", "hangup", android.R.drawable.ic_menu_close_clear_cancel))
        }
        nm.notify(NOTIF_ID, b.build())
    }

    private companion object {
        const val NOTIF_ID = 42
    }
}
