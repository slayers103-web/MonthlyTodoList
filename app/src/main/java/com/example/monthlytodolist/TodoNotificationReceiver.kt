package com.example.monthlytodolist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class TodoNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pref = context.getSharedPreferences("MonthlyHistoryPref", Context.MODE_PRIVATE)
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        
        val jsonString = pref.getString("data_$currentMonth", null) ?: return
        
        var uncompletedCount = 0
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (!obj.getBoolean("isDone")) uncompletedCount++
        }

        if (uncompletedCount > 0) {
            showNotification(context, uncompletedCount)
        }
        
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    private fun showNotification(context: Context, uncompletedCount: Int) {
        val channelId = "monthly_todo_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "이달의 할 일 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("이달의 할 일 점검")
            .setContentText("이번 달 미완료된 할 일이 ${uncompletedCount}개 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())
    }
}
