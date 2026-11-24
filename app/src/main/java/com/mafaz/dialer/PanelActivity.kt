package com.mafaz.dialer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager

// The dialler panel as its own transparent screen. Because it is a real screen, Android closes it
// when you press Home or Recents, as well as Back.
class PanelActivity : Activity() {
    companion object {
        const val EXTRA_KEYPAD = "keypad"
        const val EXTRA_NUMBER = "number"
    }

    private lateinit var panel: PanelView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        val left = Ui.cfg(this).getString("side", "left") == "left"
        panel = PanelView(this, left) { closePanel() }
        setContentView(panel)
        OverlayService.panelState(true)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i != null && i.getBooleanExtra(EXTRA_KEYPAD, false)) {
            val n = i.getStringExtra(EXTRA_NUMBER)
            panel.post { panel.openKeypad(n) }
        }
    }

    private fun closePanel() {
        panel.dismiss {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onBackPressed() {
        if (!panel.handleBack()) closePanel()
    }

    override fun onUserLeaveHint() {
        if (!isFinishing) finish()
    }

    override fun onStop() {
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun onDestroy() {
        OverlayService.panelState(false)
        super.onDestroy()
    }
}
