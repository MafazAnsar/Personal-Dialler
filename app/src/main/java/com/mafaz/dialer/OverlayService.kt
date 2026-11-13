package com.mafaz.dialer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var handle: View? = null
    private var panel: PanelView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        startInForeground()
        Toggles.init(this)
        if (Settings.canDrawOverlays(this)) showHandle() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        panel = null
        handle?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        handle = null
        super.onDestroy()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("dots", "Floating dots", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "dots")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Floating dots active")
            .setContentText("Tap to open settings")
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showHandle() {
        val left = prefs.getString("side", "left") == "left"
        val svc = this
        val dotDp = when (prefs.getInt("dotsize", 1)) {
            0 -> 6
            1 -> 8
            else -> 11
        }
        val box = LinearLayout(svc)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(dp(12), dp(14), dp(12), dp(14))
        box.alpha = max(20, prefs.getInt("dotalpha", 80)) / 100f
        val pill = GradientDrawable()
        pill.setColor(0x33000000)
        pill.cornerRadius = dp(14).toFloat()
        box.background = pill
        repeat(3) {
            val dot = View(svc)
            dot.background = Ui.circle(Ui.accent(svc))
            val lp = LinearLayout.LayoutParams(dp(dotDp), dp(dotDp))
            lp.setMargins(0, dp(3), 0, dp(3))
            box.addView(dot, lp)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = (if (left) Gravity.START else Gravity.END) or Gravity.TOP
        lp.x = 0
        lp.y = prefs.getInt("y", dp(300))

        var downX = 0f
        var downY = 0f
        var startY = 0
        var dragged = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startY = lp.y; dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    val inward = if (left) dx else -dx
                    if (inward > dp(28) && abs(dx) > abs(dy)) {
                        openPanel()
                    } else if (abs(dy) > dp(8)) {
                        dragged = true
                        lp.y = max(0, (startY + dy).toInt())
                        wm.updateViewLayout(box, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        prefs.edit().putInt("y", lp.y).apply()
                    } else if (abs(e.rawX - downX) < dp(8) && abs(e.rawY - downY) < dp(8)) {
                        openPanel()
                    }
                }
            }
            true
        }
        wm.addView(box, lp)
        handle = box
    }

    private fun openPanel() {
        if (panel != null) return
        val left = prefs.getString("side", "left") == "left"
        val p = PanelView(this, left) { closePanel() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        wm.addView(p, lp)
        panel = p
        handle?.visibility = View.GONE
    }

    private fun closePanel() {
        val p = panel ?: return
        p.dismiss {
            try { wm.removeView(p) } catch (e: Exception) { }
            if (panel === p) panel = null
            handle?.visibility = View.VISIBLE
        }
    }
}
