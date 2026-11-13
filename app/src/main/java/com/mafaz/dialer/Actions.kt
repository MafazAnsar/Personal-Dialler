package com.mafaz.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
        val ok = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL
        launch(ctx, Intent(action, Uri.parse("tel:" + Uri.encode(p.number))))
    }

    fun sms(ctx: Context, p: Person) =
        launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.number))))

    fun whatsapp(ctx: Context, p: Person) =
        launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + intl(p.number, cc(ctx)))))

    fun telegram(ctx: Context, p: Person) =
        launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=" + intl(p.number, cc(ctx)))))

    fun email(ctx: Context, p: Person) {
        val addr = Repo.email(ctx, p)
        if (addr == null) {
            Toast.makeText(ctx, "No email saved for " + p.name, Toast.LENGTH_SHORT).show()
            return
        }
        launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(addr))))
    }

    fun note(ctx: Context, p: Person) = quick(ctx, "note", p)

    fun block(ctx: Context, p: Person) = quick(ctx, "block", p)

    private fun quick(ctx: Context, mode: String, p: Person) {
        val i = Intent(ctx, QuickActivity::class.java)
        i.putExtra("mode", mode)
        i.putExtra("number", p.number)
        i.putExtra("name", p.name)
        launch(ctx, i)
    }

    fun run(ctx: Context, id: String, p: Person) {
        when (id) {
            "sms" -> sms(ctx, p)
            "wa" -> whatsapp(ctx, p)
            "tg" -> telegram(ctx, p)
            "mail" -> email(ctx, p)
            "note" -> note(ctx, p)
            "block" -> block(ctx, p)
            else -> call(ctx, p)
        }
    }
}
