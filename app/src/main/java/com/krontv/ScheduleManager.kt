package com.krontv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

class ScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTurnOn(hour: Int, minute: Int) {
        scheduleAlarm(hour, minute, PowerAlarmReceiver.ACTION_POWER_ON, REQUEST_CODE_TURN_ON)
    }

    fun scheduleTurnOff(hour: Int, minute: Int) {
        scheduleAlarm(hour, minute, PowerAlarmReceiver.ACTION_POWER_OFF, REQUEST_CODE_TURN_OFF)
    }

    fun cancelTurnOn() {
        cancelAlarm(PowerAlarmReceiver.ACTION_POWER_ON, REQUEST_CODE_TURN_ON)
    }

    fun cancelTurnOff() {
        cancelAlarm(PowerAlarmReceiver.ACTION_POWER_OFF, REQUEST_CODE_TURN_OFF)
    }

    private fun scheduleAlarm(hour: Int, minute: Int, action: String, requestCode: Int) {
        val intent = Intent(context, PowerAlarmReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(context, PowerAlarmReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE_TURN_ON = 1001
        private const val REQUEST_CODE_TURN_OFF = 1002
    }
}
