package com.mafaz.dialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout

class QuickActivity : Activity() {
    private val dialogTheme = android.R.style.Theme_Material_Light_Dialog_Alert

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "note"
        val number = intent.getStringExtra("number") ?: ""
        val name = intent.getStringExtra("name") ?: number
        val dlg = if (mode == "block") blockDialog(number, name) else noteDialog(number, name)
        dlg.setOnDismissListener { finish() }
        dlg.show()
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
}
