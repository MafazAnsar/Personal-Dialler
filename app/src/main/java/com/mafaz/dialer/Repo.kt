package com.mafaz.dialer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.LruCache

data class Person(
    val name: String,
    val number: String,
    val contactId: Long? = null,
    val callType: Int = 0,
    val date: Long = 0L,
    val duration: Long = 0L
)

object T9 {
    fun digits(s: String): String = buildString {
        for (ch in s.lowercase()) {
            append(
                when (ch) {
                    in 'a'..'c' -> '2'
                    in 'd'..'f' -> '3'
                    in 'g'..'i' -> '4'
                    in 'j'..'l' -> '5'
                    in 'm'..'o' -> '6'
                    in 'p'..'s' -> '7'
                    in 't'..'v' -> '8'
                    in 'w'..'z' -> '9'
                    in '0'..'9' -> ch
                    else -> continue
                }
            )
        }
    }
}

object Repo {
    private val photoCache = LruCache<Long, Bitmap>(120)
    private val noPhoto = HashSet<Long>()

    private fun has(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private val PHONE = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    private val P_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID
    private val P_NAME = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    private val P_NUM = ContactsContract.CommonDataKinds.Phone.NUMBER
    private const val P_SORT = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"

    fun contacts(ctx: Context): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Person>()
        val seen = HashSet<String>()
        ctx.contentResolver.query(PHONE, arrayOf(P_ID, P_NAME, P_NUM), null, null, P_SORT)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val num = c.getString(2) ?: continue
                val key = id.toString() + "|" + Store.key(num)
                if (seen.add(key)) out.add(Person(name, num, id))
            }
        }
        return out
    }

    fun favourites(ctx: Context, limit: Int = 24): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Person>()
        val seen = HashSet<Long>()
        val sel = ContactsContract.CommonDataKinds.Phone.STARRED + "=1"
        ctx.contentResolver.query(PHONE, arrayOf(P_ID, P_NAME, P_NUM), sel, null, P_SORT)?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val num = c.getString(2) ?: continue
                if (seen.add(id)) out.add(Person(name, num, id))
            }
        }
        return out
    }

    fun recents(ctx: Context, limit: Int = 100): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<Person>()
        val ids = HashMap<String, Long?>()
        val proj = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.DURATION
        )
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val num = c.getString(0) ?: continue
                if (num.isBlank()) continue
                val cached = c.getString(1)
                var id: Long? = null
                if (!cached.isNullOrBlank()) {
                    val k = Store.key(num)
                    if (!ids.containsKey(k)) ids[k] = lookup(ctx, num)?.contactId
                    id = ids[k]
                }
                val name = if (cached.isNullOrBlank()) num else cached
                out.add(Person(name, num, id, c.getInt(2), c.getLong(3), c.getLong(4)))
            }
        }
        return out
    }

    fun lookup(ctx: Context, number: String): Person? {
        if (number.isBlank() || !has(ctx, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        val proj = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME)
        var out: Person? = null
        try {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) out = Person(c.getString(1) ?: number, number, c.getLong(0))
            }
        } catch (e: Exception) { }
        return out
    }

    fun email(ctx: Context, p: Person): String? {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return null
        val id = p.contactId ?: lookup(ctx, p.number)?.contactId ?: return null
        var result: String? = null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
            arrayOf(id.toString()), null
        )?.use { c -> if (c.moveToFirst()) result = c.getString(0) }
        return result
    }

    fun photo(ctx: Context, id: Long): Bitmap? {
        val cached = photoCache.get(id)
        if (cached != null) return cached
        if (noPhoto.contains(id)) return null
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
        var bmp: Bitmap? = null
        try {
            val s = ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri)
            if (s != null) {
                bmp = BitmapFactory.decodeStream(s)
                s.close()
            }
        } catch (e: Exception) { }
        if (bmp != null) photoCache.put(id, bmp) else noPhoto.add(id)
        return bmp
    }
}
