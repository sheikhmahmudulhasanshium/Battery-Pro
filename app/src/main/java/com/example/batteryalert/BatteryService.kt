package com.example.batteryalert

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
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
            val mode = BatteryPrefs.getMode(this@BatteryService)
            val status = getStatus()

            Log.d(TAG, "Alarm Task Running: lvl=$lvl, status=$status, mode=$mode")

            val (isUndercharged, isOvercharged, min, max) = checkBreach(lvl, status, mode)

            if (isUndercharged || isOvercharged) {
                maximizeVolume()
                sendAlert(lvl, min, max, isOvercharged)
                handler.removeCallbacks(this)
                handler.postDelayed(this, 300000)
            } else {
                isAlarmLooping = false
                handler.removeCallbacks(this)
                getSystemService(NotificationManager::class.java).cancel(2)
            }
        }
    }

    private fun checkBreach(lvl: Int, status: Int, mode: Int): BreachResult {
        var min = 20
        var max = 80
        var isUnder = false
        var isOver = false

        when (mode) {
            0 -> { // Default
                min = 20; max = 80
                isUnder = lvl < 20 && status != BatteryManager.BATTERY_STATUS_CHARGING
                isOver = lvl > 80 && status == BatteryManager.BATTERY_STATUS_CHARGING
            }
            2 -> { // Extreme
                min = 2; max = 99
                isUnder = lvl <= 1 && status != BatteryManager.BATTERY_STATUS_CHARGING
                isOver = lvl >= 100 && status == BatteryManager.BATTERY_STATUS_CHARGING
            }
            else -> { // Custom
                min = BatteryPrefs.getMin(this)
                max = BatteryPrefs.getMax(this)
                isUnder = lvl < min && status != BatteryManager.BATTERY_STATUS_CHARGING
                isOver = lvl > max && status == BatteryManager.BATTERY_STATUS_CHARGING
            }
        }
        return BreachResult(isUnder, isOver, min, max)
    }

    data class BreachResult(val isUnder: Boolean, val isOver: Boolean, val min: Int, val max: Int)

    private fun maximizeVolume() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
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
        val mode = BatteryPrefs.getMode(this)

        val (isUnder, isOver, min, max) = checkBreach(lvl, status, mode)

        if ((isUnder || isOver) && !isAlarmLooping) {
            maximizeVolume()
            isAlarmLooping = true
            handler.post(alarmTask)
        }
        
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(getString(R.string.notif_active_desc, min, max))
            .setSmallIcon(R.drawable.ic_monitoring)
            .setContentIntent(getAppIntent())
            .setOngoing(true).build()
        startForeground(1, n)
        return START_STICKY
    }

    private fun getAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun sendAlert(lvl: Int, min: Int, max: Int, isOvercharged: Boolean) {
        Log.d(TAG, "Breach detected: $lvl")
        val stopPi = PendingIntent.getService(this, 0, Intent(this, BatteryService::class.java).apply { action = STOP_ACTION }, PendingIntent.FLAG_IMMUTABLE)
        
        val statusStr = if (isOvercharged) "OVERCHARGED" else "UNDERCHARGE"
        val msgText = "Battery: $lvl%, Status: $statusStr (Optimal: $min-$max%)"
        
        val wa = BatteryPrefs.getWA(this)
        val waPi = if (!wa.isNullOrEmpty()) {
            val waMsg = when {
                isOvercharged -> getString(R.string.msg_wa_over, lvl)
                else -> getString(R.string.msg_wa_low, lvl)
            }
            val waIntent = Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?phone=$wa&text=${Uri.encode(waMsg)}".toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            PendingIntent.getActivity(this, 1, waIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else null

        val soundUri = BatteryPrefs.getSound(this)?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(statusStr + " ALERT!")
            .setContentText(msgText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msgText + "\nAction Required: Correct charging state."))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(getAppIntent())
            .addAction(0, getString(R.string.btn_stop_alarm), stopPi)
            .setSound(soundUri)
            
        waPi?.let { builder.addAction(android.R.drawable.ic_menu_send, "WhatsApp Alert", it) }
        
        getSystemService(NotificationManager::class.java).notify(2, builder.build())
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
            val mode = BatteryPrefs.getMode(c!!)

            val (isUnder, isOver, _, _) = checkBreach(lvl, status, mode)

            if ((isUnder || isOver) && !isAlarmLooping) {
                Log.d(TAG, "Breach detected in receiver: lvl=$lvl")
                maximizeVolume()
                isAlarmLooping = true
                handler.post(alarmTask)
            } else if (!isUnder && !isOver && isAlarmLooping) {
                stopAlarm()
            }
        }
    }
    override fun onDestroy() { stopAlarm(); try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}