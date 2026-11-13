package com.mafaz.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

object Actions {
    fun intl(raw: String, cc: String): String {
        val t = raw.trim()
        val d = t.filter { it.isDigit() }
        return when {
            t.startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.startsWith("0") -> cc + d.drop(1)
            else -> d
        }
    }

    private fun cc(ctx: Context): String = Ui.cfg(ctx).getString("cc", "94") ?: "94"

    private fun launch(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    fun call(ctx: Context, p: Person) {
        val pref = Ui.cfg(ctx).getString("sim", "ask") ?: "ask"
        if (pref != "sys") {
            val sims = Sims.list(ctx)
            if (sims.size >= 2) {
                if (pref == "ask") {
                    quick(ctx, "sim", p)
                    return
                }
                val idx = pref.toIntOrNull()
                if (idx != null && idx in sims.indices) {
                    Sims.place(ctx, p.number, sims[idx].handle)
                    return
                }
            }
        }
        callPlain(ctx, p)
    }

    fun callPlain(ctx: Context, p: Person) {
        val ok = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL
        launch(ctx, Intent(action, Uri.parse("tel:" + Uri.encode(p.number))))
    }

    fun sms(ctx: Context, p: Person) =
        launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.number))))

    private val WA = arrayOf("com.whatsapp", "com.whatsapp.w4b")
    private val TG = arrayOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus")
    private val SIG = arrayOf("org.thoughtcrime.securesms")
    private val VIB = arrayOf("com.viber.voip")

    private fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    // Opens a link inside a specific installed app; falls back to any app that can handle it.
    private fun viewIn(ctx: Context, uri: String, pkgs: Array<String>, appName: String) {
        for (pkg in pkgs) {
            if (installed(ctx, pkg)) {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                i.setPackage(pkg)
                launch(ctx, i)
                return
            }
        }
        val any = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        any.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(any)
        } catch (e: Exception) {
            Toast.makeText(ctx, appName + " is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun whatsapp(ctx: Context, p: Person) =
        viewIn(ctx, "https://wa.me/" + intl(p.number, cc(ctx)), WA, "WhatsApp")

    fun telegram(ctx: Context, p: Person) =
        viewIn(ctx, "tg://resolve?phone=" + intl(p.number, cc(ctx)), TG, "Telegram")

    fun signal(ctx: Context, p: Person) =
        viewIn(ctx, "https://signal.me/#p/+" + intl(p.number, cc(ctx)), SIG, "Signal")

    fun viber(ctx: Context, p: Person) =
        viewIn(ctx, "viber://chat?number=" + Uri.encode("+" + intl(p.number, cc(ctx))), VIB, "Viber")

    private fun openPkg(ctx: Context, pkgs: Array<String>, appName: String) {
        for (pkg in pkgs) {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                launch(ctx, i)
                return
            }
        }
        Toast.makeText(ctx, appName + " is not installed", Toast.LENGTH_SHORT).show()
    }

    // Used when an icon is tapped with no contact selected: just open the app itself.
    fun openApp(ctx: Context, id: String): Boolean {
        when (id) {
            "call" -> launch(ctx, Intent(ctx, DialActivity::class.java))
            "sms" -> launch(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
            "mail" -> launch(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL))
            "wa" -> openPkg(ctx, WA, "WhatsApp")
            "tg" -> openPkg(ctx, TG, "Telegram")
            "signal" -> openPkg(ctx, SIG, "Signal")
            "viber" -> openPkg(ctx, VIB, "Viber")
            else -> {
                Toast.makeText(ctx, "Tap a contact first, then tap this icon", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    fun email(ctx: Context, p: Person) {
        val addr = Repo.email(ctx, p)
        if (addr == null) {
            Toast.makeText(ctx, "No email saved for " + p.name, Toast.LENGTH_SHORT).show()
            return
        }
        launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(addr))))
    }

    fun openSettings(ctx: Context) = launch(ctx, Intent(ctx, MainActivity::class.java))

    fun addContact(ctx: Context, p: Person) {
        val i = Intent(Intent.ACTION_INSERT_OR_EDIT)
        i.type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        i.putExtra(ContactsContract.Intents.Insert.PHONE, p.number)
        if (p.name != p.number) i.putExtra(ContactsContract.Intents.Insert.NAME, p.name)
        launch(ctx, i)
    }

    fun favourite(ctx: Context, p: Person) {
        Toast.makeText(ctx, Favs.toggle(ctx, p), Toast.LENGTH_SHORT).show()
    }

    fun menu(ctx: Context, p: Person) = quick(ctx, "menu", p)

    fun note(ctx: Context, p: Person) = quick(ctx, "note", p)

    fun block(ctx: Context, p: Person) = quick(ctx, "block", p)

    fun history(ctx: Context, p: Person) = quick(ctx, "history", p)

    fun remind(ctx: Context, p: Person) = quick(ctx, "remind", p)

    fun run(ctx: Context, id: String, p: Person) {
        when (id) {
            "sms" -> sms(ctx, p)
            "wa" -> whatsapp(ctx, p)
            "tg" -> telegram(ctx, p)
            "signal" -> signal(ctx, p)
            "viber" -> viber(ctx, p)
            "mail" -> email(ctx, p)
            "addcontact" -> addContact(ctx, p)
            "fav" -> favourite(ctx, p)
            "history" -> history(ctx, p)
            "remind" -> remind(ctx, p)
            "note" -> note(ctx, p)
            "block" -> block(ctx, p)
            else -> call(ctx, p)
        }
    }

    private fun quick(ctx: Context, mode: String, p: Person) {
        val i = Intent(ctx, QuickActivity::class.java)
        i.putExtra("mode", mode)
        i.putExtra("number", p.number)
        i.putExtra("name", p.name)
        launch(ctx, i)
    }
}
