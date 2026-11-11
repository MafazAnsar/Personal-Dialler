package com.mafaz.dialer

import android.content.Context

object Store {
    fun key(number: String): String = number.filter { it.isDigit() }.takeLast(9)

    private fun sp(c: Context, name: String) =
        c.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun note(c: Context, number: String): String? {
        val k = key(number)
        if (k.isEmpty()) return null
        return sp(c, "notes").getString(k, null)
    }

    fun setNote(c: Context, number: String, text: String) {
        val k = key(number)
        if (k.isEmpty()) return
        val e = sp(c, "notes").edit()
        if (text.isBlank()) e.remove(k) else e.putString(k, text.trim())
        e.apply()
    }

    fun isBlocked(c: Context, number: String): Boolean {
        val k = key(number)
        if (k.isEmpty()) return false
        return sp(c, "blocks").contains(k)
    }

    fun setBlocked(c: Context, number: String, on: Boolean) {
        val k = key(number)
        if (k.isEmpty()) return
        val e = sp(c, "blocks").edit()
        if (on) e.putBoolean(k, true) else e.remove(k)
        e.apply()
    }
}
