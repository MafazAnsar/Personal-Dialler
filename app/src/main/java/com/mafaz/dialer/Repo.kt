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
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import kotlin.concurrent.thread

data class Person(
    val name: String,
    val number: String,
    val contactId: Long? = null,
    val callType: Int = 0,
    val date: Long = 0L,
    val duration: Long = 0L,
    val count: Int = 0,
    val label: String = ""
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
    private val P_TYPE = ContactsContract.CommonDataKinds.Phone.TYPE
    private val P_LABEL = ContactsContract.CommonDataKinds.Phone.LABEL

    private fun typeLabel(ctx: Context, type: Int, custom: String?): String = try {
        ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, type, custom).toString()
    } catch (e: Exception) {
        ""
    }
    private const val P_SORT = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"

    fun contacts(ctx: Context): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Person>()
        val seen = HashSet<String>()
        val proj = arrayOf(P_ID, P_NAME, P_NUM, P_TYPE, P_LABEL)
        ctx.contentResolver.query(PHONE, proj, null, null, P_SORT)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val num = c.getString(2) ?: continue
                val key = id.toString() + "|" + Store.key(num)
                if (seen.add(key)) out.add(Person(name, num, id, 0, 0L, 0L, 0, typeLabel(ctx, c.getInt(3), c.getString(4))))
            }
        }
        return out
    }

    // One row per starred contact: its default number if you chose one, otherwise its first number.
    private fun systemFavourites(ctx: Context, limit: Int): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val byContact = LinkedHashMap<Long, ArrayList<Person>>()
        val seen = HashSet<String>()
        val sel = ContactsContract.CommonDataKinds.Phone.STARRED + "=1"
        val proj = arrayOf(P_ID, P_NAME, P_NUM, P_TYPE, P_LABEL)
        ctx.contentResolver.query(PHONE, proj, sel, null, P_SORT)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val num = c.getString(2) ?: continue
                if (seen.add(id.toString() + "|" + Store.key(num))) {
                    byContact.getOrPut(id) { ArrayList() }
                        .add(Person(name, num, id, 0, 0L, 0L, 0, typeLabel(ctx, c.getInt(3), c.getString(4))))
                }
            }
        }
        val defs = Store.defaults(ctx)
        val out = ArrayList<Person>()
        for ((id, list) in byContact) {
            if (out.size >= limit) break
            val d = defs[id]
            val pick = (if (d != null) list.firstOrNull { Store.key(it.number) == Store.key(d) } else null) ?: list[0]
            out.add(pick)
        }
        return out
    }

    fun favourites(ctx: Context, limit: Int = 80): List<Person> {
        val out = ArrayList<Person>(systemFavourites(ctx, limit))
        val keys = HashSet<String>()
        for (p in out) keys.add(Store.key(p.number))
        for (a in Favs.app(ctx)) {
            if (keys.add(Store.key(a.number))) out.add(a)
        }
        return out
    }

    // Contact id from a call-log lookup uri such as content://com.android.contacts/contacts/lookup/<key>/<id>.
    private fun idFromLookupUri(raw: String?): Long? {
        if (raw.isNullOrEmpty()) return null
        val seg = Uri.parse(raw).pathSegments
        return if (seg.size >= 4 && seg[1] == "lookup") seg[3].toLongOrNull() else null
    }

    // No per-row queries: contact ids and names come from the call log itself, or from the
    // contacts that were already loaded (known, keyed by Store.key).
    fun recents(ctx: Context, limit: Int = 250, known: Map<String, Person> = emptyMap()): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<Person>()
        val proj = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_LOOKUP_URI
        )
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val num = c.getString(0) ?: continue
                if (num.isBlank()) continue
                val cached = c.getString(1)
                val match = known[Store.key(num)]
                val id = idFromLookupUri(c.getString(5)) ?: match?.contactId
                val name = if (!cached.isNullOrBlank()) cached else (match?.name ?: num)
                out.add(Person(name, num, id, c.getInt(2), c.getLong(3), c.getLong(4), 0, match?.label ?: ""))
            }
        }
        return out
    }

    // Loads everything off the main thread and calls back on the main thread.
    // If the cache is warm it calls back straight away (on the calling thread) and then refreshes.
    fun loadAll(ctx: Context, cb: (List<Person>, List<Person>, List<Person>) -> Unit) {
        val app = ctx.applicationContext
        val cr = Cache.recents
        val ca = Cache.all
        val cf = Cache.favs
        if (cr != null && ca != null && cf != null) cb(cr, ca, cf)
        val main = Handler(Looper.getMainLooper())
        thread {
            val gen = Cache.generation()
            val all = contacts(app)
            val known = HashMap<String, Person>()
            for (p in all) known.putIfAbsent(Store.key(p.number), p)
            val rec = recents(app, 250, known)
            val fav = favourites(app)
            Cache.put(gen, rec, all, fav)
            main.post { cb(rec, all, fav) }
        }
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
        } catch (e: Exception) {
            AppLog.w(ctx, "Repo.lookup", e)
        }
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
        } catch (e: Exception) {
            AppLog.w(ctx, "Repo.photo", e)
        }
        if (bmp != null) photoCache.put(id, bmp) else noPhoto.add(id)
        return bmp
    }
}

object Cache {
    private val lock = Any()
    private var gen = 0

    @Volatile
    var recents: List<Person>? = null

    @Volatile
    var all: List<Person>? = null

    @Volatile
    var favs: List<Person>? = null

    fun generation(): Int = synchronized(lock) { gen }

    // Stores a finished load unless the cache was invalidated while it was running.
    fun put(startedAt: Int, r: List<Person>, a: List<Person>, f: List<Person>) {
        synchronized(lock) {
            if (startedAt != gen) return
            recents = r
            all = a
            favs = f
        }
    }

    fun clear() {
        synchronized(lock) {
            gen++
            recents = null
            all = null
            favs = null
        }
    }
}
