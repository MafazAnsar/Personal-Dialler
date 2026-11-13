package com.mafaz.dialer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract

object Favs {
    private fun sp(c: Context) = c.getSharedPreferences("favs", Context.MODE_PRIVATE)

    // Favourites saved by this app (used for numbers that are not in your contacts).
    fun app(c: Context): List<Person> {
        val out = ArrayList<Person>()
        for (e in sp(c).all.entries) {
            val v = e.value as? String ?: continue
            val i = v.indexOf('\n')
            if (i < 0) continue
            out.add(Person(v.substring(0, i), v.substring(i + 1)))
        }
        return out
    }

    private fun starred(ctx: Context, id: Long): Boolean {
        var s = false
        try {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.STARRED),
                ContactsContract.Contacts._ID + "=?",
                arrayOf(id.toString()), null
            )?.use { c -> if (c.moveToFirst()) s = c.getInt(0) == 1 }
        } catch (e: Exception) { }
        return s
    }

    private fun setStarred(ctx: Context, id: Long, on: Boolean): Boolean = try {
        val v = ContentValues()
        v.put(ContactsContract.Contacts.STARRED, if (on) 1 else 0)
        ctx.contentResolver.update(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), v, null, null
        )
        true
    } catch (e: Exception) {
        false
    }

    fun isFav(ctx: Context, p: Person): Boolean {
        val k = Store.key(p.number)
        if (k.isNotEmpty() && sp(ctx).contains(k)) return true
        val id = p.contactId ?: Repo.lookup(ctx, p.number)?.contactId
        return id != null && starred(ctx, id)
    }

    fun toggle(ctx: Context, p: Person): String {
        val k = Store.key(p.number)
        val id = p.contactId ?: Repo.lookup(ctx, p.number)?.contactId
        Cache.favs = null
        if (isFav(ctx, p)) {
            if (k.isNotEmpty()) sp(ctx).edit().remove(k).apply()
            if (id != null && starred(ctx, id)) {
                if (!setStarred(ctx, id, false)) {
                    return "Allow contacts write permission (setup step 1) to remove this favourite"
                }
            }
            return "Removed from favourites"
        }
        if (id != null && setStarred(ctx, id, true)) return "Added to favourites"
        if (k.isEmpty()) return "Can't favourite this number"
        sp(ctx).edit().putString(k, p.name + "\n" + p.number).apply()
        return "Added to favourites"
    }
}
