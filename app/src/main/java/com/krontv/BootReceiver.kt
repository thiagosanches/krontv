package com.krontv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule alarms after reboot
            CoroutineScope(Dispatchers.IO).launch {
                rescheduleAlarms(context)
            }
        }
    }

    private suspend fun rescheduleAlarms(context: Context) {
        val prefs = context.dataStore.data.first()
        
        val turnOnHour = prefs[intPreferencesKey("turn_on_hour")] ?: 8
        val turnOnMinute = prefs[intPreferencesKey("turn_on_minute")] ?: 0
        val turnOffHour = prefs[intPreferencesKey("turn_off_hour")] ?: 22
        val turnOffMinute = prefs[intPreferencesKey("turn_off_minute")] ?: 0
        
        val turnOnEnabled = prefs[booleanPreferencesKey("turn_on_enabled")] ?: false
        val turnOffEnabled = prefs[booleanPreferencesKey("turn_off_enabled")] ?: false

        val scheduleManager = ScheduleManager(context)
        
        if (turnOnEnabled) {
            scheduleManager.scheduleTurnOn(turnOnHour, turnOnMinute)
        }
        
        if (turnOffEnabled) {
            scheduleManager.scheduleTurnOff(turnOffHour, turnOffMinute)
        }
    }
}
