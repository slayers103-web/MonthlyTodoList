package com.example.monthlytodolist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

object NotificationScheduler {

    fun scheduleMonthlyAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val daysBefore = listOf(7, 3, 1)

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        val lastDayCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        val lastDay = lastDayCal.get(Calendar.DAY_OF_MONTH)

        daysBefore.forEachIndexed { index, days ->
            val targetDay = lastDay - days + 1
            val alarmCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, targetDay)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (alarmCal.before(now)) {
                alarmCal.add(Calendar.MONTH, 1)
                val newLastDay = alarmCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                alarmCal.set(Calendar.DAY_OF_MONTH, newLastDay - days + 1)
            }

            val intent = Intent(context, TodoNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmCal.timeInMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    alarmCal.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}
