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
    companion object {
        private const val CHANNEL_ID = "BatteryPro_V7"
        private const val STOP_ACTION = "STOP_ALARM_ACTION"
        private const val TAG = "SYS_CHECK"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isAlarmLooping = false

    private val alarmTask = object : Runnable {
        override fun run() {
            if (!BatteryPrefs.isRun(this@BatteryService)) return
            val lvl = getLvl()
            val min = BatteryPrefs.getMin(this@BatteryService)
            val max = BatteryPrefs.getMax(this@BatteryService)
            val status = getStatus()
            
            Log.d(TAG, "Alarm Task Running: lvl=$lvl, status=$status, range=$min-$max")
            
            // Logical check: Trigger alarm ONLY if level is out of range AND state is wrong
            // (e.g., Low battery AND NOT charging, OR High battery AND charging)
            val isLowAndNotCharging = lvl < min && status != BatteryManager.BATTERY_STATUS_CHARGING
            val isHighAndCharging = lvl > max && status == BatteryManager.BATTERY_STATUS_CHARGING

            if (isLowAndNotCharging || isHighAndCharging) {
                sendAlert(lvl, min, max)
                handler.postDelayed(this, 300000)
            } else {
                isAlarmLooping = false
                getSystemService(NotificationManager::class.java).cancel(2)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: action=${intent?.action}")
        if (intent?.action == STOP_ACTION) { stopAlarm(); return START_STICKY }
        createChannel()
        val lvl = getLvl()
        val status = getStatus()
        val min = BatteryPrefs.getMin(this)
        val max = BatteryPrefs.getMax(this)

        val isLowAndNotCharging = lvl < min && status != BatteryManager.BATTERY_STATUS_CHARGING
        val isHighAndCharging = lvl > max && status == BatteryManager.BATTERY_STATUS_CHARGING

        if ((isLowAndNotCharging || isHighAndCharging) && !isAlarmLooping) {
            isAlarmLooping = true
            handler.post(alarmTask)
        }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_active_title))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).build()
        startForeground(1, n)
        return START_STICKY
    }

    private fun sendAlert(lvl: Int, min: Int, max: Int) {
        Log.d(TAG, "Breach detected: $lvl (Min: $min, Max: $max)")
        val stopPi = PendingIntent.getService(this, 0, Intent(this, BatteryService::class.java).apply { action = STOP_ACTION }, PendingIntent.FLAG_IMMUTABLE)
        val soundUri = BatteryPrefs.getSound(this)?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alert = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.range_breach_title))
            .setContentText(getString(R.string.range_breach_desc, lvl, min, max))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, getString(R.string.btn_stop_alarm), stopPi)
            .setSound(soundUri)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, alert)
    }

    private fun stopAlarm() { 
        Log.d(TAG, "Alarm Stopped by User")
        isAlarmLooping = false
        handler.removeCallbacks(alarmTask)
        getSystemService(NotificationManager::class.java).cancel(2) 
    }

    private fun getLvl(): Int = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    
    private fun getStatus(): Int = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    private fun createChannel() {
        val soundUri = BatteryPrefs.getSound(this)?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val chan = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            setSound(soundUri, attr)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (!BatteryPrefs.isRun(this@BatteryService)) return
            val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val min = BatteryPrefs.getMin(c!!)
            val max = BatteryPrefs.getMax(c)
            
            val isLowAndNotCharging = lvl < min && status != BatteryManager.BATTERY_STATUS_CHARGING
            val isHighAndCharging = lvl > max && status == BatteryManager.BATTERY_STATUS_CHARGING

            if ((isLowAndNotCharging || isHighAndCharging) && !isAlarmLooping) {
                Log.d(TAG, "Breach detected in receiver: lvl=$lvl")
                isAlarmLooping = true
                handler.post(alarmTask)
            } else if (!isLowAndNotCharging && !isHighAndCharging && isAlarmLooping) {
                stopAlarm()
            }
        }
    }
    override fun onDestroy() { stopAlarm(); try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}