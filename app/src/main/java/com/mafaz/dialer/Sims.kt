package com.mafaz.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class Sim(val handle: PhoneAccountHandle, val label: String)

object Sims {
    fun list(ctx: Context): List<Sim> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val out = ArrayList<Sim>()
            for (h in tm.callCapablePhoneAccounts) {
                val acc = tm.getPhoneAccount(h) ?: continue
                if (!acc.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) continue
                out.add(Sim(h, acc.label?.toString() ?: "SIM"))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun place(ctx: Context, number: String, handle: PhoneAccountHandle) {
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val extras = Bundle()
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            tm.placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (e: Exception) { }
    }
}
