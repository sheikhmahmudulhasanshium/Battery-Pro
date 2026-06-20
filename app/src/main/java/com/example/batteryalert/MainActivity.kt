package com.example.batteryalert

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

class MainActivity : AppCompatActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val u = IntentCompat.getParcelableExtra(res.data!!, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            u?.let { BatteryPrefs.setSnd(this, it.toString()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val rg = findViewById<RadioGroup>(R.id.rangeGroup)
        val et = findViewById<EditText>(R.id.etWhatsApp)
        et.setText(BatteryPrefs.getWA(this))

        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        findViewById<Button>(R.id.btnSound).setOnClickListener {
            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply { putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM) })
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val (min, max) = if (rg.checkedRadioButtonId == R.id.rbDefault) 20 to 80 else 10 to 100
            val wa = et.text.toString()
            BatteryPrefs.setRange(this, min, max); BatteryPrefs.setWA(this, wa); BatteryPrefs.setRun(this, true)
            ContextCompat.startForegroundService(this, Intent(this, BatteryService::class.java))
            if (wa.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$wa&text=Alert%20Active")))
        }
    }
}