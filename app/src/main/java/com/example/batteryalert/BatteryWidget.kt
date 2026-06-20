package com.example.batteryalert

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            val run = BatteryPrefs.isRun(context)
            views.setTextViewText(R.id.widget_btn, if (run) "STOP" else "START")
            views.setInt(R.id.widget_bg, "setBackgroundColor", if (run) Color.GREEN else Color.RED)
            val pi = PendingIntent.getBroadcast(context, 0, Intent(context, BatteryWidget::class.java).apply { action = "com.example.batteryalert.TOGGLE" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.batteryalert.TOGGLE") {
            val r = !BatteryPrefs.isRun(context!!)
            BatteryPrefs.setRun(context, r)
            if (r) ContextCompat.startForegroundService(context, Intent(context, BatteryService::class.java))
            else context.stopService(Intent(context, BatteryService::class.java))
            val m = AppWidgetManager.getInstance(context); onUpdate(context, m, m.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java)))
        }
    }
}