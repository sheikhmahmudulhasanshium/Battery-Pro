package com.example.batteryalert

import android.content.Context
import androidx.core.content.edit

object BatteryPrefs {
    private const val P = "bp"
    fun setRange(c: Context, min: Int, max: Int) = c.getSharedPreferences(P, 0).edit { putInt("n", min); putInt("x", max) }
    fun getMin(c: Context) = c.getSharedPreferences(P, 0).getInt("n", 20)
    fun getMax(c: Context) = c.getSharedPreferences(P, 0).getInt("x", 80)
    fun setSnd(c: Context, u: String) = c.getSharedPreferences(P, 0).edit { putString("s", u) }
    fun getSnd(c: Context) = c.getSharedPreferences(P, 0).getString("s", null)
    fun setWA(c: Context, n: String) = c.getSharedPreferences(P, 0).edit { putString("w", n) }
    fun getWA(c: Context) = c.getSharedPreferences(P, 0).getString("w", "")
    fun setRun(c: Context, r: Boolean) = c.getSharedPreferences(P, 0).edit { putBoolean("r", r) }
    fun isRun(c: Context) = c.getSharedPreferences(P, 0).getBoolean("r", false)
}