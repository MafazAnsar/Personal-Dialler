package com.mafaz.dialer

import android.content.Context
import android.telephony.PhoneNumberUtils

// Pure key logic, kept separate so it can be unit-tested on the JVM with a stand-in normaliser.
object Keys {
    fun keyOf(raw: String, len: Int, normalize: (String) -> String): String {
        val digits = normalize(raw).filter { it.isDigit() }
        val n = len.coerceIn(7, 12)
        return if (digits.length > n) digits.takeLast(n) else digits
    }
}

object Store {
    // How many trailing digits identify a person. Set from the "numlen" setting in init().
    @Volatile
    var numLen = 9
        private set

    // Length of the keys older notes, blocks and last actions were stored with.
    @Volatile
    private var usedLen = 9

    fun key(number: String): String =
        Keys.keyOf(number, numLen) { PhoneNumberUtils.normalizeNumber(it) ?: it }

    private fun sp(c: Context, name: String) =
        c.getSharedPreferences(name, Context.MODE_PRIVATE)

    // Entries saved with a shorter key still match after the length is raised.
    private fun legacyKey(k: String): String? =
        if (usedLen < numLen && k.length > usedLen) k.takeLast(usedLen) else null

    fun init(ctx: Context) {
        numLen = Ui.cfg(ctx).getInt("numlen", 9).coerceIn(7, 12)
        migrate(ctx)
    }

    fun setNumLen(ctx: Context, n: Int) {
        val v = n.coerceIn(7, 12)
        Ui.cfg(ctx).edit().putInt("numlen", v).apply()
        numLen = v
        migrate(ctx)
        Cache.clear()
    }

    private fun migrate(ctx: Context) {
        val cfg = Ui.cfg(ctx)
        val used = cfg.getInt("keylen_used", 9)
        val want = numLen
        if (want < used) {
            // Shortening is lossless: keep the last `want` digits of every stored key.
            for (name in arrayOf("notes", "blocks", "lastact")) {
                val prefs = sp(ctx, name)
                val all: Map<String, Any?> = HashMap(prefs.all)
                if (all.isEmpty()) continue
                val e = prefs.edit()
                e.clear()
                for ((k, v) in all) {
                    val nk = k.takeLast(want)
                    if (v is Boolean) e.putBoolean(nk, v) else if (v is String) e.putString(nk, v)
                }
                e.apply()
            }
            cfg.edit().putInt("keylen_used", want).apply()
            usedLen = want
        } else {
            if (!cfg.contains("keylen_used")) cfg.edit().putInt("keylen_used", used).apply()
            usedLen = used
        }
        // Favourites keep the number itself, so they can always be re-keyed exactly.
        if (cfg.getInt("keylen_favs", 9) != want || !cfg.contains("keylen_favs")) {
            val fav = sp(ctx, "favs")
            val all: Map<String, Any?> = HashMap(fav.all)
            if (all.isNotEmpty()) {
                val e = fav.edit()
                e.clear()
                for (v in all.values) {
                    val s = v as? String ?: continue
                    val i = s.indexOf('\n')
                    if (i < 0) continue
                    e.putString(key(s.substring(i + 1)), s)
                }
                e.apply()
            }
            cfg.edit().putInt("keylen_favs", want).apply()
        }
    }

    fun note(c: Context, number: String): String? {
        val k = key(number)
        if (k.isEmpty()) return null
        val p = sp(c, "notes")
        val v = p.getString(k, null)
        if (v != null) return v
        val l = legacyKey(k) ?: return null
        return p.getString(l, null)
    }

    fun setNote(c: Context, number: String, text: String) {
        val k = key(number)
        if (k.isEmpty()) return
        val e = sp(c, "notes").edit()
        legacyKey(k)?.let { e.remove(it) }
        if (text.isBlank()) e.remove(k) else e.putString(k, text.trim())
        e.apply()
    }

    fun isBlocked(c: Context, number: String): Boolean {
        val k = key(number)
        if (k.isEmpty()) return false
        val p = sp(c, "blocks")
        if (p.contains(k)) return true
        val l = legacyKey(k) ?: return false
        return p.contains(l)
    }

    fun setBlocked(c: Context, number: String, on: Boolean) {
        val k = key(number)
        if (k.isEmpty()) return
        val e = sp(c, "blocks").edit()
        legacyKey(k)?.let { e.remove(it) }
        if (on) e.putBoolean(k, true) else e.remove(k)
        e.apply()
    }

    fun lastAction(c: Context, number: String): String? {
        val k = key(number)
        if (k.isEmpty()) return null
        val p = sp(c, "lastact")
        val v = p.getString(k, null)
        if (v != null) return v
        val l = legacyKey(k) ?: return null
        return p.getString(l, null)
    }

    fun setLastAction(c: Context, number: String, id: String) {
        val k = key(number)
        if (k.isEmpty()) return
        sp(c, "lastact").edit().putString(k, id).apply()
    }
}
