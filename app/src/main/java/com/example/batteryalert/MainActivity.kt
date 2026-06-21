package com.example.batteryalert

import android.content.*
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.bottomnavigation.BottomNavigationView

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
        
        applyAppTheme(BatteryPrefs.getTheme(this))
        
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity Created")

        setSupportActionBar(findViewById(R.id.toolbar))

        val etMin = findViewById<EditText>(R.id.etMin)
        val etMax = findViewById<EditText>(R.id.etMax)
        val etWA = findViewById<EditText>(R.id.etWhatsApp)
        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        val monitoringLayout = findViewById<View>(R.id.monitoring_layout)
        val historyLayout = findViewById<View>(R.id.history_layout)
        val settingsLayout = findViewById<View>(R.id.settings_layout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val rgMode = findViewById<RadioGroup>(R.id.rgMode)
        val customRangeContainer = findViewById<View>(R.id.customRangeContainer)
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)

        etMin.setText(BatteryPrefs.getMin(this).toString())
        etMax.setText(BatteryPrefs.getMax(this).toString())
        etWA.setText(BatteryPrefs.getWA(this))
        tvLogs.text = BatteryPrefs.getLogs(this)

        val mode = BatteryPrefs.getMode(this)
        when (mode) {
            0 -> rgMode.check(R.id.rbDefault)
            1 -> {
                rgMode.check(R.id.rbCustom)
                customRangeContainer.visibility = View.VISIBLE
            }
            2 -> rgMode.check(R.id.rbExtreme)
        }

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            customRangeContainer.visibility = if (checkedId == R.id.rbCustom) View.VISIBLE else View.GONE
            val newMode = when (checkedId) {
                R.id.rbDefault -> 0
                R.id.rbCustom -> 1
                R.id.rbExtreme -> 2
                else -> 0
            }
            BatteryPrefs.setMode(this, newMode)
        }

        when (BatteryPrefs.getTheme(this)) {
            1 -> rgTheme.check(R.id.rbThemeLight)
            2 -> rgTheme.check(R.id.rbThemeDark)
        }
        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = if (checkedId == R.id.rbThemeLight) 1 else 2
            BatteryPrefs.setTheme(this, theme)
            applyAppTheme(theme)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        bottomNav.setOnItemSelectedListener { item ->
            monitoringLayout.visibility = View.GONE
            historyLayout.visibility = View.GONE
            settingsLayout.visibility = View.GONE
            
            when (item.itemId) {
                R.id.nav_monitoring -> {
                    monitoringLayout.visibility = View.VISIBLE
                    true
                }
                R.id.nav_history -> {
                    historyLayout.visibility = View.VISIBLE
                    tvLogs.text = BatteryPrefs.getLogs(this)
                    true
                }
                R.id.nav_settings -> {
                    settingsLayout.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
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
            
            var min = 20
            var max = 80
            val currentMode = BatteryPrefs.getMode(this)
            
            if (currentMode == 1) {
                min = etMin.text.toString().toIntOrNull() ?: 20
                max = etMax.text.toString().toIntOrNull() ?: 80
                if (min >= max) {
                    Toast.makeText(this, "Min must be less than Max", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else if (currentMode == 2) {
                min = 2; max = 99 // Safe range for Extreme
            }

            val wa = etWA.text.toString()
            BatteryPrefs.setRange(this, min, max)
            BatteryPrefs.setWA(this, wa)
            BatteryPrefs.setRun(this, r = true)
            BatteryPrefs.addLog(this, getString(R.string.log_start, min, max))

            ContextCompat.startForegroundService(this, Intent(this, BatteryService::class.java))

            if (wa.isNotEmpty()) {
                val lvl = getLvl()
                val status = getStatus()
                
                val isUnder = (currentMode == 2 && lvl <= 1) || (currentMode != 2 && lvl < min)
                val isOver = (currentMode == 2 && lvl >= 100) || (currentMode != 2 && lvl > max)

                val msg = when {
                    isUnder -> getString(R.string.msg_wa_low, lvl)
                    isOver -> getString(R.string.msg_wa_over, lvl)
                    else -> getString(R.string.msg_wa_optimal, lvl)
                }
                
                val url = "https://api.whatsapp.com/send?phone=$wa&text=${Uri.encode(msg)}"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: Exception) {
                    Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                }
            }
            tvLogs.text = BatteryPrefs.getLogs(this)
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            Log.d(TAG, "Clear Logs Button Clicked")
            BatteryPrefs.clearLogs(this)
            tvLogs.text = ""
        }

        findViewById<Button>(R.id.btnResetPrefs).setOnClickListener {
            BatteryPrefs.resetAll(this)
            Toast.makeText(this, R.string.msg_reset_success, Toast.LENGTH_SHORT).show()
            finish(); startActivity(intent)
        }
    }

    private fun applyAppTheme(theme: Int) {
        val themeMode = when (theme) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    private fun getLvl(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    private fun getStatus(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    }
}