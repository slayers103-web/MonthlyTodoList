package com.example.monthlytodolist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

object NotificationScheduler {
    private const val REQUEST_BASE = 4100
    private val daysBefore = intArrayOf(7, 3, 1)

    fun scheduleMonthlyAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = Calendar.getInstance()
        var month = YearMonth.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)

        // Always schedule the current month's future reminders and next month's reminders.
        for (monthOffset in 0..1) {
            scheduleForMonth(context, alarmManager, month.plusMonths(monthOffset.toLong()))
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        daysBefore.forEachIndexed { index, _ ->
            val intent = Intent(context, TodoNotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    private fun scheduleForMonth(context: Context, alarmManager: AlarmManager, month: YearMonth) {
        daysBefore.forEachIndexed { index, days ->
            val day = month.lengthOfMonth() - days + 1
            val trigger = month.atDay(day).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (trigger <= System.currentTimeMillis()) return@forEachIndexed

            val intent = Intent(context, TodoNotificationReceiver::class.java).apply {
                putExtra(TodoNotificationReceiver.EXTRA_YEAR, month.year)
                putExtra(TodoNotificationReceiver.EXTRA_MONTH, month.monthValue)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + monthOffsetId(month) * 10 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    private fun monthOffsetId(month: YearMonth): Int = month.year * 12 + month.monthValue
}
