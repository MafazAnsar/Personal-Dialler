package com.mafaz.dialer

import android.content.Context

object ActionDefs {
    val all = listOf(
        "call", "sms", "wa", "tg", "signal", "viber", "mail",
        "addcontact", "fav", "history", "remind", "note", "block"
    )
    val defaults = listOf("call", "sms", "wa", "tg", "mail", "addcontact", "fav", "note", "block")

    fun title(id: String): String = when (id) {
        "call" -> "Call"
        "sms" -> "SMS"
        "wa" -> "WhatsApp"
        "tg" -> "Telegram"
        "signal" -> "Signal"
        "viber" -> "Viber"
        "mail" -> "Email"
        "addcontact" -> "Save to contacts"
        "fav" -> "Add or remove favourite"
        "history" -> "Call history"
        "remind" -> "Remind me to call back"
        "note" -> "Note"
        "block" -> "Block"
        else -> id
    }

    fun enabled(ctx: Context): List<String> {
        val s = Ui.cfg(ctx).getString("acts", null)
        val list = s?.split(",")?.filter { it in all }
        return if (list.isNullOrEmpty()) defaults else list
    }

    fun save(ctx: Context, list: List<String>) {
        Ui.cfg(ctx).edit().putString("acts", list.joinToString(",")).apply()
    }
}
