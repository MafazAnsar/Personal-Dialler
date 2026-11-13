package com.mafaz.dialer

import android.content.Context
import org.json.JSONObject

object Backup {
    private val files = arrayOf("settings", "notes", "blocks", "lastact")

    fun export(ctx: Context): String {
        val root = JSONObject()
        root.put("version", 1)
        for (name in files) {
            val o = JSONObject()
            val all = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all
            for (entry in all.entries) {
                o.put(entry.key, entry.value)
            }
            root.put(name, o)
        }
        return root.toString(2)
    }

    fun import(ctx: Context, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            for (name in files) {
                val o = root.optJSONObject(name) ?: continue
                val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                e.clear()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = o.get(k)
                    if (v is Boolean) e.putBoolean(k, v)
                    else if (v is Int) e.putInt(k, v)
                    else if (v is Long) e.putLong(k, v)
                    else if (v is String) e.putString(k, v)
                }
                e.apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
