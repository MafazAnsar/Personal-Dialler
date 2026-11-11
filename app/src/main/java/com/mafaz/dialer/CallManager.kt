package com.mafaz.dialer

import android.telecom.Call
import android.telecom.InCallService

object CallManager {
    val calls = ArrayList<Call>()
    var service: InCallService? = null
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        for (l in ArrayList(listeners)) l()
    }

    fun current(): Call? =
        calls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.state != Call.STATE_DISCONNECTED }
            ?: calls.firstOrNull()

    fun numberOf(call: Call): String = call.details?.handle?.schemeSpecificPart ?: ""
}
