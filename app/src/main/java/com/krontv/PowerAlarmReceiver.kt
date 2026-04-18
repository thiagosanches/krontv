package com.krontv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_POWER_ON, ACTION_POWER_OFF -> {
                // Start the foreground service to send the power command
                val serviceIntent = Intent(context, PowerService::class.java).apply {
                    action = intent.action
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_POWER_ON = "com.tvscheduler.POWER_ON"
        const val ACTION_POWER_OFF = "com.tvscheduler.POWER_OFF"
    }
}
