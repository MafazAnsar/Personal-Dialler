package com.mafaz.dialer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable

object Ui {
    val accents = intArrayOf(
        0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(),
        0xFFE57373.toInt(), 0xFFBA68C8.toInt(), 0xFFF06292.toInt()
    )

    fun cfg(c: Context): SharedPreferences =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun dark(c: Context): Boolean = cfg(c).getBoolean("dark", true)

    fun accent(c: Context): Int =
        accents[cfg(c).getInt("accent", 0).coerceIn(0, accents.size - 1)]

    fun bg(c: Context): Int = if (dark(c)) 0xF2161B20.toInt() else 0xF5FFFFFF.toInt()
    fun solid(c: Context): Int = if (dark(c)) 0xFF161B20.toInt() else 0xFFFFFFFF.toInt()
    fun card(c: Context): Int = if (dark(c)) 0xFF252C33.toInt() else 0xFFEAEFF3.toInt()
    fun text(c: Context): Int = if (dark(c)) 0xFFFFFFFF.toInt() else 0xFF1B1F23.toInt()
    fun sub(c: Context): Int = if (dark(c)) 0xB3FFFFFF.toInt() else 0x99000000.toInt()
    fun scrim(c: Context): Int = if (dark(c)) 0xB3000000.toInt() else 0x66000000.toInt()

    fun round(color: Int, radiusPx: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radiusPx.toFloat()
        return g
    }

    fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }
}
