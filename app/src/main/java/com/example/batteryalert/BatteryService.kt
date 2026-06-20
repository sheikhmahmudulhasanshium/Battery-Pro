package com.example.batteryalert

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

class BatteryService : Service() {
    private val cId = "BP_V6"
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val alarm = object : Runnable {
        override fun run() {
            val lvl = getLvl()
            Log.d("SYS_CHECK", "Level $lvl% | Range: ${BatteryPrefs.getMin(this@BatteryService)}-${BatteryPrefs.getMax(this@BatteryService)}")
            if (lvl !in BatteryPrefs.getMin(this@BatteryService)..BatteryPrefs.getMax(this@BatteryService)) {
                send(lvl)
                handler.postDelayed(this, 300000)
            } else active = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (lvl !in BatteryPrefs.getMin(c!!)..BatteryPrefs.getMax(c) && !active) {
                active = true; handler.post(alarm)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chan = NotificationChannel(cId, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true)
            val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attr)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        startForeground(1, NotificationCompat.Builder(this, cId).setContentTitle("Monitoring Active").setSmallIcon(android.R.drawable.ic_dialog_info).build())
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return START_STICKY
    }

    private fun send(lvl: Int) {
        val s = BatteryPrefs.getSnd(this)?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val n = NotificationCompat.Builder(this, cId).setContentTitle("Battery Breach: $lvl%")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setSound(s).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    private fun getLvl(): Int = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    override fun onDestroy() { handler.removeCallbacks(alarm); try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}