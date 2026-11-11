package com.mafaz.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.VideoProfile

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val call = CallManager.current() ?: return
        when (i.action) {
            "answer" -> call.answer(VideoProfile.STATE_AUDIO_ONLY)
            "decline" -> call.reject(false, null)
            "hangup" -> call.disconnect()
        }
    }
}
