package com.mafaz.dialer

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

object Pop {
    // Shows a themed card as a dialog over an activity.
    fun dialog(act: Activity, card: View, widthDp: Int, onDismiss: () -> Unit): Dialog {
        val d = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
        val wrap = FrameLayout(act)
        val pad = Ui.dp(act, 24)
        wrap.setPadding(pad, pad, pad, pad)
        wrap.setOnClickListener { d.dismiss() }
        if (Build.VERSION.SDK_INT >= 35) {
            wrap.setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(pad + b.left, pad + b.top, pad + b.right, pad + b.bottom)
                insets
            }
        }
        wrap.addView(
            card,
            FrameLayout.LayoutParams(Ui.dp(act, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        d.setContentView(wrap)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Ui.scrim(act)))
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.setOnDismissListener { onDismiss() }
        return d
    }
}
