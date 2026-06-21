package com.example.batteryalert
import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*

object BatteryPrefs {
    private const val P = "bp"
    fun setRange(c: Context, min: Int, max: Int) = c.getSharedPreferences(P, 0).edit { putInt("n", min); putInt("x", max) }
    fun getMin(c: Context) = c.getSharedPreferences(P, 0).getInt("n", 20)
    fun getMax(c: Context) = c.getSharedPreferences(P, 0).getInt("x", 80)
    fun setWA(c: Context, n: String) = c.getSharedPreferences(P, 0).edit { putString("w", n) }
    fun getWA(c: Context) = c.getSharedPreferences(P, 0).getString("w", "")
    fun addLog(c: Context, msg: String) {
        val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLogs = "$date: $msg\n${getLogs(c)}".take(1000)
        c.getSharedPreferences(P, 0).edit { putString("l", newLogs) }
    }
    fun getLogs(c: Context) = c.getSharedPreferences(P, 0).getString("l", "") ?: ""
    fun clearLogs(c: Context) = c.getSharedPreferences(P, 0).edit { remove("l") }
    fun setRun(c: Context, r: Boolean) = c.getSharedPreferences(P, 0).edit { putBoolean("r", r) }
    fun isRun(c: Context) = c.getSharedPreferences(P, 0).getBoolean("r", false)
    fun setSound(c: Context, uri: String) = c.getSharedPreferences(P, 0).edit { putString("s", uri) }
    fun getSound(c: Context): String? = c.getSharedPreferences(P, 0).getString("s", null)

    fun setMode(c: Context, m: Int) = c.getSharedPreferences(P, 0).edit { putInt("m", m) }
    fun getMode(c: Context) = c.getSharedPreferences(P, 0).getInt("m", 0) // 0: Default, 1: Custom, 2: Extreme

    fun setTheme(c: Context, t: Int) = c.getSharedPreferences(P, 0).edit { putInt("t", t) }
    fun getTheme(c: Context) = c.getSharedPreferences(P, 0).getInt("t", -1) // -1: System, 1: Light, 2: Dark

    fun resetAll(c: Context) = c.getSharedPreferences(P, 0).edit { clear() }
}