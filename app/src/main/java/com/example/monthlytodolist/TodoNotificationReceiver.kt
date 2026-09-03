package com.example.monthlytodolist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.YearMonth

class TodoNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val month = if (intent.hasExtra(EXTRA_YEAR) && intent.hasExtra(EXTRA_MONTH)) {
            YearMonth.of(intent.getIntExtra(EXTRA_YEAR, 0), intent.getIntExtra(EXTRA_MONTH, 0))
        } else YearMonth.now()

        val repository = TodoRepository(context)
        val todos = repository.getTodos()
        val remaining = todos.count { !repository.isDone(month, it.id) }

        if (remaining > 0 && notificationsAllowed(context)) {
            showNotification(context, remaining, month)
        }
        NotificationScheduler.scheduleMonthlyAlarms(context)
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun showNotification(context: Context, count: Int, month: YearMonth) {
        val channelId = "monthly_todo_channel"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "매월 할 일 알림", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_check)
            .setContentTitle("${month.monthValue}월 할 일 점검")
            .setContentText("아직 완료하지 않은 할 일이 ${count}개 있습니다.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(1001, notification)
    }

    companion object {
        const val EXTRA_YEAR = "year"
        const val EXTRA_MONTH = "month"
    }
}
