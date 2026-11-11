package com.mafaz.dialer

import android.telecom.Call
import android.telecom.CallScreeningService

class ScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val incoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val resp = CallScreeningService.CallResponse.Builder()
        if (incoming && Store.isBlocked(this, number)) {
            resp.setDisallowCall(true)
            resp.setRejectCall(true)
            resp.setSkipNotification(true)
        }
        respondToCall(callDetails, resp.build())
    }
}
