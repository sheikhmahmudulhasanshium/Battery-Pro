package com.example.batteryalert

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SYS_CHECK"
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Log.d(TAG, "Notification permission granted: $isGranted")
    }

    private val soundLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { 
                BatteryPrefs.setSound(this, it.toString())
                Log.d(TAG, "Sound selected: $it")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity Created")

        val etMin = findViewById<EditText>(R.id.etMin)
        val etMax = findViewById<EditText>(R.id.etMax)
        val etWA = findViewById<EditText>(R.id.etWhatsApp)
        val tvLogs = findViewById<TextView>(R.id.tvLogs)

        etMin.setText(BatteryPrefs.getMin(this).toString())
        etMax.setText(BatteryPrefs.getMax(this).toString())
        etWA.setText(BatteryPrefs.getWA(this))
        tvLogs.text = BatteryPrefs.getLogs(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btnPickSound).setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.btn_pick_sound))
                val existing = BatteryPrefs.getSound(this@MainActivity)
                existing?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.toUri()) }
            }
            soundLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            Log.d(TAG, "Start Button Clicked")
            val minStr = etMin.text.toString()
            val maxStr = etMax.text.toString()
            
            val min = minStr.toIntOrNull() ?: 20
            val max = maxStr.toIntOrNull() ?: 80

            if (min >= max) {
                Toast.makeText(this, "Min must be less than Max", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val wa = etWA.text.toString()

            BatteryPrefs.setRange(this, min, max)
            BatteryPrefs.setWA(this, wa)
            BatteryPrefs.setRun(this, r = true)
            BatteryPrefs.addLog(this, getString(R.string.log_start, min, max))

            ContextCompat.startForegroundService(this, Intent(this, BatteryService::class.java))

            if (wa.isNotEmpty()) {
                val lvl = getLvl()
                val msg = when {
                    lvl < min -> getString(R.string.msg_wa_low, lvl)
                    lvl > max -> getString(R.string.msg_wa_over, lvl)
                    else -> getString(R.string.msg_wa_optimal, lvl)
                }
                val url = "https://api.whatsapp.com/send?phone=$wa&text=$msg"
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            tvLogs.text = BatteryPrefs.getLogs(this)
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            Log.d(TAG, "Clear Logs Button Clicked")
            BatteryPrefs.clearLogs(this)
            tvLogs.text = ""
        }
    }

    private fun getLvl(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }
}