package com.mafaz.dialer

import android.telecom.Call
import android.telecom.CallScreeningService

class ScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val incoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val cfg = Ui.cfg(this)
        var block = incoming && Store.isBlocked(this, number)
        if (incoming && !block) {
            if (number.isBlank()) {
                if (cfg.getBoolean("blockhidden", false)) block = true
            } else if (cfg.getBoolean("blockunknown", false)) {
                if (Repo.lookup(this, number) == null) block = true
            }
        }
        val resp = CallScreeningService.CallResponse.Builder()
        if (block) {
            resp.setDisallowCall(true)
            resp.setRejectCall(true)
            resp.setSkipNotification(true)
        }
        respondToCall(callDetails, resp.build())
    }
}
