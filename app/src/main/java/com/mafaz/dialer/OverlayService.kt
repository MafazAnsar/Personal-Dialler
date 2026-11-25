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
import android.graphics.drawable.Icon
import android.graphics.drawable.GradientDrawable
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
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
    companion object {
        const val ACTION_OPEN = "com.mafaz.dialer.OPEN_PANEL"

        @Volatile
        private var instance: OverlayService? = null

        // Called by the panel screen: hide the dots while it is open, show them again after.
        fun panelState(open: Boolean) {
            val s = instance ?: return
            Handler(Looper.getMainLooper()).post { s.onPanelState(open) }
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var handle: View? = null
    private var observer: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        startInForeground()
        Toggles.init(this)
        registerObservers()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
        } else if (prefs.getBoolean("enabled", false)) {
            showHandle()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null && !prefs.getBoolean("enabled", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OPEN) openPanel()
        return START_STICKY
    }

    private fun registerObservers() {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Cache.clear()
            }
        }
        try {
            contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, o)
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, o)
            observer = o
        } catch (e: Exception) {
            AppLog.w(this, "OverlayService.observers", e)
        }
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        instance = null
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
            this, 1, Intent(this, PanelActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val settings = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "dots")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Floating dots active")
            .setContentText("Tap to open the dialler")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_preferences),
                    "Settings", settings
                ).build()
            )
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
        val i = Intent(this, PanelActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            startActivity(i)
            handle?.visibility = View.GONE
        } catch (e: Exception) {
            AppLog.w(this, "OverlayService.openPanel", e)
        }
    }

    private fun onPanelState(open: Boolean) {
        handle?.visibility = if (open) View.GONE else View.VISIBLE
        if (!open && !prefs.getBoolean("enabled", false)) stopSelf()
    }
}
