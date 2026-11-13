package com.mafaz.dialer

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast

object Toggles {
    var torchOn = false
    private var registered = false

    fun init(ctx: Context) {
        if (registered) return
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            }, null)
            registered = true
        } catch (e: Exception) { }
    }

    private fun start(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't open that setting", Toast.LENGTH_SHORT).show()
        }
    }

    fun torch(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                Toast.makeText(ctx, "No flashlight found", Toast.LENGTH_SHORT).show()
                return
            }
            cm.setTorchMode(id, !torchOn)
            torchOn = !torchOn
        } catch (e: Exception) {
            Toast.makeText(ctx, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun btOn(ctx: Context): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    fun bluetooth(ctx: Context) {
        if (btOn(ctx)) {
            start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } else {
            try {
                val i = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) {
                start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }
    }

    fun wifiOn(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    fun wifi(ctx: Context) {
        start(ctx, Intent(Settings.Panel.ACTION_WIFI))
    }

    fun soundNormal(ctx: Context): Boolean = try {
        ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
    } catch (e: Exception) {
        true
    }

    fun toggleSound(ctx: Context) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL)
                AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't change sound mode", Toast.LENGTH_SHORT).show()
        }
    }

    fun dataOn(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    } catch (e: Exception) {
        false
    }

    fun data(ctx: Context) {
        start(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    }

    fun dndOn(ctx: Context): Boolean = try {
        ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (e: Exception) {
        false
    }

    fun toggleDnd(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) {
                start(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return
            }
            nm.setInterruptionFilter(
                if (dndOn(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't change Do Not Disturb", Toast.LENGTH_SHORT).show()
        }
    }
}
