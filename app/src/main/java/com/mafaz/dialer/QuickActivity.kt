package com.mafaz.dialer

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast

class QuickActivity : Activity() {
    private var dlg: Dialog? = null

    private fun close() {
        dlg?.dismiss()
    }

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
        val card: View = when (mode) {
            "block" -> blockCard(number, name)
            "sim" -> simCard(number, name)
            "history" -> historyCard(number, name)
            "remind" -> remindCard(number, name)
            else -> noteCard(number, name)
        }
        val d = Pop.dialog(this, card, 280) { finish() }
        dlg = d
        d.show()
    }

    private fun noteCard(number: String, name: String): View =
        Cards.input(
            this, "Note for " + name, Store.note(this, number) ?: "", "Note or label",
            { t -> Store.setNote(this, number, t); close() },
            { close() }
        )

    private fun blockCard(number: String, name: String): View {
        val blocked = Store.isBlocked(this, number)
        val labels = listOf(if (blocked) "Unblock" else "Block", "Cancel")
        return Cards.menu(this, name + "  " + number, labels, if (blocked) -1 else 0) { i ->
            if (i == 0) {
                Store.setBlocked(this, number, !blocked)
                Toast.makeText(this, if (blocked) "Unblocked" else "Blocked", Toast.LENGTH_SHORT).show()
            }
            close()
        }
    }

    private fun simCard(number: String, name: String): View {
        val sims = Sims.list(this)
        val labels = ArrayList<String>()
        for ((i, s) in sims.withIndex()) labels.add("SIM " + (i + 1) + "   " + s.label)
        return Cards.menu(this, "Call " + name, labels, -1) { i ->
            Sims.place(this, number, sims[i].handle)
            close()
        }
    }

    private fun remindCard(number: String, name: String): View =
        Cards.menu(this, "Remind me to call " + name, Reminder.labels.toList(), -1) { i ->
            Reminder.schedule(this, number, name, Reminder.minutesFor(i))
            Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show()
            close()
        }

    private fun historyCard(number: String, name: String): View {
        val h = History.load(this, number)
        val title = name + "  \u00B7  " + h.count + " calls  \u00B7  " + History.fmt(h.total)
        val rows = if (h.rows.isEmpty()) listOf("No calls found") else h.rows
        return Cards.rows(this, title, rows)
    }
}
