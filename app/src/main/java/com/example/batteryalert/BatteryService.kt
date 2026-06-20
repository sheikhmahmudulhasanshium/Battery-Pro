@file:Suppress("UnusedResources", "UnusedImport")
package com.example.batteryalert
import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat

class BatteryService : Service() {
    private val cId = "BP_V7"
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val alarm = object : Runnable {
        override fun run() {
            val lvl = getLvl()
            if (lvl !in BatteryPrefs.getMin(this@BatteryService)..BatteryPrefs.getMax(this@BatteryService)) {
                send(lvl)
                handler.postDelayed(this, 300000)
            } else active = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopAlarm(); return START_STICKY }

        val chan = NotificationChannel(cId, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true)
            val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attr)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)

        startForeground(1, NotificationCompat.Builder(this, cId)
            .setContentTitle("Battery Pro Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).build())

        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return START_STICKY
    }

    private fun send(lvl: Int) {
        val stopPi = PendingIntent.getService(this, 0, Intent(this, BatteryService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, cId)
            .setContentTitle("Battery Breach: $lvl%")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "STOP", stopPi).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    private fun stopAlarm() {
        active = false
        handler.removeCallbacks(alarm)
        getSystemService(NotificationManager::class.java).cancel(2)
    }

    private fun getLvl(): Int = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (lvl !in BatteryPrefs.getMin(c!!)..BatteryPrefs.getMax(c) && !active) {
                active = true
                handler.post(alarm)
            }
        }
    }

    override fun onDestroy() { stopAlarm(); try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(i: Intent?) = null
}