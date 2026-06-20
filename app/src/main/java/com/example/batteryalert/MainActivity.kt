package com.example.batteryalert

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etMin = findViewById<EditText>(R.id.etMin)
        val etMax = findViewById<EditText>(R.id.etMax)
        val etWA = findViewById<EditText>(R.id.etWhatsApp)
        val tvLogs = findViewById<TextView>(R.id.tvLogs)

        etMin.setText(BatteryPrefs.getMin(this).toString())
        etMax.setText(BatteryPrefs.getMax(this).toString())
        etWA.setText(BatteryPrefs.getWA(this))
        tvLogs.text = BatteryPrefs.getLogs(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val min = etMin.text.toString().toIntOrNull() ?: 20
            val max = etMax.text.toString().toIntOrNull() ?: 80
            val wa = etWA.text.toString()

            BatteryPrefs.setRange(this, min, max)
            BatteryPrefs.setWA(this, wa)
            BatteryPrefs.setRun(this, true)
            BatteryPrefs.addLog(this, getString(R.string.log_start, min, max))

            ContextCompat.startForegroundService(this, Intent(this, BatteryService::class.java))

            if (wa.isNotEmpty()) {
                val url = "https://api.whatsapp.com/send?phone=$wa&text=${getString(R.string.msg_wa, getLvl())}"
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            tvLogs.text = BatteryPrefs.getLogs(this)
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            BatteryPrefs.clearLogs(this)
            tvLogs.text = ""
        }
    }

    private fun getLvl(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }
}