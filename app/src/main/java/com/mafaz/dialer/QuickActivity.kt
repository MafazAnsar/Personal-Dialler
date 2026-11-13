package com.mafaz.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateFormat
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import java.util.Calendar

class QuickActivity : Activity() {
    private val dialogTheme = android.R.style.Theme_Material_Light_Dialog_Alert
    private var chained = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "note"
        val number = intent.getStringExtra("number") ?: ""
        val name = intent.getStringExtra("name") ?: number
        if (mode == "sim" && Sims.list(this).size < 2) {
            Actions.callPlain(this, Person(name, number))
            finish()
            return
        }
        val dlg = dialogFor(mode, number, name)
        dlg.setOnDismissListener { if (!chained) finish() }
        dlg.show()
    }

    private fun dialogFor(mode: String, number: String, name: String): AlertDialog = when (mode) {
        "block" -> blockDialog(number, name)
        "sim" -> simDialog(number, name)
        "history" -> historyDialog(number, name)
        "remind" -> remindDialog(number, name)
        "menu" -> menuDialog(number, name)
        else -> noteDialog(number, name)
    }

    private fun menuDialog(number: String, name: String): AlertDialog {
        val p = Person(name, number)
        val inContacts = Repo.lookup(this, number) != null
        val labels = ArrayList<String>()
        val ids = ArrayList<String>()
        if (!inContacts) {
            labels.add("Save to contacts")
            ids.add("addcontact")
        }
        labels.add(if (Favs.isFav(this, p)) "Remove from favourites" else "Add to favourites")
        ids.add("fav")
        labels.add("Call history")
        ids.add("history")
        labels.add("Note")
        ids.add("note")
        labels.add("Remind me to call back")
        ids.add("remind")
        labels.add(if (Store.isBlocked(this, number)) "Unblock" else "Block")
        ids.add("block")
        labels.add("Copy number")
        ids.add("copy")
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle(name)
            .setItems(labels.toTypedArray()) { _, i -> pick(ids[i], number, name) }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun pick(id: String, number: String, name: String) {
        val p = Person(name, number)
        when (id) {
            "addcontact" -> Actions.addContact(this, p)
            "fav" -> Toast.makeText(this, Favs.toggle(this, p), Toast.LENGTH_SHORT).show()
            "copy" -> {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("number", number))
                Toast.makeText(this, "Number copied", Toast.LENGTH_SHORT).show()
            }
            else -> {
                chained = true
                val d = dialogFor(id, number, name)
                d.setOnDismissListener { finish() }
                d.show()
            }
        }
    }

    private fun noteDialog(number: String, name: String): AlertDialog {
        val et = EditText(this)
        et.setText(Store.note(this, number) ?: "")
        et.hint = "Note or label"
        val box = FrameLayout(this)
        val pad = Ui.dp(this, 20)
        box.setPadding(pad, Ui.dp(this, 8), pad, 0)
        box.addView(et)
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle("Note for " + name)
            .setView(box)
            .setPositiveButton("Save") { _, _ -> Store.setNote(this, number, et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun blockDialog(number: String, name: String): AlertDialog {
        val blocked = Store.isBlocked(this, number)
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle(if (blocked) "Unblock " + name + "?" else "Block " + name + "?")
            .setMessage(number)
            .setPositiveButton(if (blocked) "Unblock" else "Block") { _, _ ->
                Store.setBlocked(this, number, !blocked)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun simDialog(number: String, name: String): AlertDialog {
        val sims = Sims.list(this)
        val labels = Array(sims.size) { i -> "SIM " + (i + 1) + "   " + sims[i].label }
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle("Call " + name)
            .setItems(labels) { _, i -> Sims.place(this, number, sims[i].handle) }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun remindDialog(number: String, name: String): AlertDialog {
        val labels = arrayOf("In 15 minutes", "In 1 hour", "In 3 hours", "Tomorrow at 9 am")
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle("Remind me to call " + name)
            .setItems(labels) { _, i ->
                val minutes: Long = when (i) {
                    0 -> 15L
                    1 -> 60L
                    2 -> 180L
                    else -> {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, 1)
                        c.set(Calendar.HOUR_OF_DAY, 9)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        (c.timeInMillis - System.currentTimeMillis()) / 60000L
                    }
                }
                Reminder.schedule(this, number, name, minutes)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun historyDialog(number: String, name: String): AlertDialog {
        val rows = ArrayList<String>()
        var total = 0L
        var count = 0
        val key = Store.key(number)
        if (key.isNotEmpty() &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        ) {
            val proj = arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
            )
            contentResolver.query(
                CallLog.Calls.CONTENT_URI, proj, null, null, CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                var scanned = 0
                while (c.moveToNext() && scanned < 4000) {
                    scanned++
                    val n = c.getString(0) ?: continue
                    if (Store.key(n) != key) continue
                    count++
                    val dur = c.getLong(3)
                    total += dur
                    if (rows.size < 60) {
                        val icon = when (c.getInt(1)) {
                            1 -> "\u2199"
                            2 -> "\u2197"
                            3 -> "\u2716"
                            5 -> "\u2298"
                            else -> "\u2022"
                        }
                        val whenText = DateFormat.format("d MMM  HH:mm", c.getLong(2)).toString()
                        rows.add(icon + "  " + whenText + (if (dur > 0) "   " + fmt(dur) else ""))
                    }
                }
            }
        }
        if (rows.isEmpty()) rows.add("No calls found")
        val title = name + "  \u00B7  " + count + " calls  \u00B7  " + fmt(total) + " talk time"
        return AlertDialog.Builder(this, dialogTheme)
            .setTitle(title)
            .setItems(rows.toTypedArray(), null)
            .setPositiveButton("Close", null)
            .create()
    }

    private fun fmt(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        val mm = (if (m < 10 && h > 0) "0" else "") + m
        val rr = (if (r < 10) "0" else "") + r
        return if (h > 0) h.toString() + ":" + mm + ":" + rr else mm + ":" + rr
    }
}
