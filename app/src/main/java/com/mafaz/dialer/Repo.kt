package com.mafaz.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

data class Person(val name: String, val number: String, val contactId: Long? = null)

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
    private fun has(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun contacts(ctx: Context): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Person>()
        val seen = HashSet<String>()
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        ctx.contentResolver.query(phone, proj, null, null, sort)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val num = c.getString(2) ?: continue
                val key = id.toString() + "|" + num.filter { it.isDigit() }.takeLast(9)
                if (seen.add(key)) out.add(Person(name, num, id))
            }
        }
        return out
    }

    fun recents(ctx: Context, limit: Int = 60): List<Person> {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<Person>()
        val seen = HashSet<String>()
        val proj = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME)
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val num = c.getString(0) ?: continue
                if (num.isBlank()) continue
                val key = num.filter { it.isDigit() }.takeLast(9)
                if (!seen.add(key)) continue
                val name = c.getString(1)
                out.add(Person(if (name.isNullOrBlank()) num else name, num))
            }
        }
        return out
    }

    private fun contactIdFor(ctx: Context, number: String): Long? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        var id: Long? = null
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) id = c.getLong(0) }
        return id
    }

    fun email(ctx: Context, p: Person): String? {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return null
        val id = p.contactId ?: contactIdFor(ctx, p.number) ?: return null
        var result: String? = null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
            arrayOf(id.toString()), null
        )?.use { c -> if (c.moveToFirst()) result = c.getString(0) }
        return result
    }
}