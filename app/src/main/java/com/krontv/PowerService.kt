package com.krontv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

class PowerService : Service() {

    private val TAG = "PowerService"
    private lateinit var bluetoothHidManager: BluetoothHidManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private val maxRetries = 3

    override fun onCreate() {
        super.onCreate()
        bluetoothHidManager = BluetoothHidManager(this)
        createNotificationChannel()
        
        // Acquire wake lock to keep device awake during command execution
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "krontv::PowerServiceWakeLock"
        )
        wakeLock?.acquire(60000) // 60 seconds max
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Scheduler")
            .setContentText("Sending power command to TV...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Handle the power command
        when (intent?.action) {
            PowerAlarmReceiver.ACTION_POWER_ON,
            PowerAlarmReceiver.ACTION_POWER_OFF -> {
                sendPowerCommand()
            }
        }

        return START_NOT_STICKY
    }

    private fun sendPowerCommand() {
        serviceScope.launch {
            var commandSent = false
            var errorMessage: String? = null
            var retryCount = 0
            
            try {
                Log.d(TAG, "Starting power command sequence")
                
                // Retry loop
                while (!commandSent && retryCount < maxRetries) {
                    if (retryCount > 0) {
                        Log.d(TAG, "Retry attempt $retryCount/$maxRetries")
                        delay(2000) // Wait 2s between retries
                    }
                    
                    // Initialize Bluetooth HID
                    bluetoothHidManager.start()
                    
                    // Wait for registration with timeout
                    var attempts = 0
                    while (!bluetoothHidManager.isRegistered && attempts < 30) {
                        delay(100)
                        attempts++
                    }

                    if (!bluetoothHidManager.isRegistered) {
                        errorMessage = "Failed to register Bluetooth HID"
                        Log.e(TAG, errorMessage)
                        retryCount++
                        continue
                    }

                    Log.d(TAG, "HID app registered")

                    // If already connected, send command directly
                    if (bluetoothHidManager.isConnected()) {
                        Log.d(TAG, "Already connected, sending command")
                        commandSent = bluetoothHidManager.sendPowerCommand()
                        if (!commandSent) {
                            errorMessage = "Failed to send command"
                        }
                    } else {
                        // Load saved TV address from DataStore
                        val prefs = dataStore.data.first()
                        val savedAddress = prefs[stringPreferencesKey("tv_address")]
                        
                        if (savedAddress != null) {
                            Log.d(TAG, "Connecting to saved device: $savedAddress")
                            bluetoothHidManager.connectToDevice(savedAddress)
                            
                            // Wait for connection with timeout
                            attempts = 0
                            val maxAttempts = 50 // 5 seconds total
                            
                            while (!bluetoothHidManager.isConnected() && attempts < maxAttempts) {
                                delay(100)
                                attempts++
                            }
                            
                            if (bluetoothHidManager.isConnected()) {
                                Log.d(TAG, "Connected, sending command")
                                // Stability delay
                                delay(300)
                                commandSent = bluetoothHidManager.sendPowerCommand()
                                if (!commandSent) {
                                    errorMessage = "Failed to send command"
                                }
                            } else {
                                errorMessage = "Failed to connect to TV"
                                Log.e(TAG, "$errorMessage after $attempts attempts")
                            }
                        } else {
                            errorMessage = "No saved TV device"
                            Log.e(TAG, errorMessage)
                            break // No point retrying without device
                        }
                    }
                    
                    if (!commandSent) {
                        retryCount++
                        bluetoothHidManager.stop()
                    }
                }
                
                Log.d(TAG, "Command sent: $commandSent after $retryCount retries")
                
                // Save command history
                val cmdMessage = if (commandSent) {
                    "Scheduled command" + if (retryCount > 0) " (retry $retryCount)" else ""
                } else {
                    errorMessage ?: "Unknown error"
                }
                
                dataStore.edit { prefs ->
                    prefs[longPreferencesKey("last_cmd_time")] = System.currentTimeMillis()
                    prefs[booleanPreferencesKey("last_cmd_success")] = commandSent
                    prefs[stringPreferencesKey("last_cmd_msg")] = cmdMessage
                }
                
                // Show result notification
                showResultNotification(commandSent, errorMessage, retryCount)
                
                // Wait before cleanup
                delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "Error in power command", e)
                showResultNotification(false, "Error: ${e.message}", retryCount)
            } finally {
                bluetoothHidManager.stop()
                wakeLock?.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    
    private fun showResultNotification(success: Boolean, errorMsg: String?, retryCount: Int) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = if (success) "TV Command Sent ✓" else "TV Command Failed ✗"
        val message = if (success) {
            "Power command sent successfully at $timeStr" + 
            if (retryCount > 0) " (after $retryCount ${if (retryCount == 1) "retry" else "retries"})" else ""
        } else {
            "$errorMsg at $timeStr" + 
            if (retryCount > 0) " (tried $retryCount times)" else ""
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(if (success) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TV Scheduler Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Power control service"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "tv_scheduler_service"
        private const val NOTIFICATION_ID = 1001
        private const val RESULT_NOTIFICATION_ID = 1002
    }
}
