package com.tvscheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PowerService : Service() {

    private lateinit var bluetoothHidManager: BluetoothHidManager

    override fun onCreate() {
        super.onCreate()
        bluetoothHidManager = BluetoothHidManager(this)
        createNotificationChannel()
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
        Thread {
            try {
                // Initialize Bluetooth HID
                bluetoothHidManager.start()
                
                // Wait for registration (with timeout)
                var attempts = 0
                while (!bluetoothHidManager.isRegistered && attempts < 20) {
                    Thread.sleep(100)
                    attempts++
                }

                if (bluetoothHidManager.isRegistered) {
                    // If already connected, send command directly
                    if (bluetoothHidManager.isConnected()) {
                        bluetoothHidManager.sendPowerCommand()
                    } else {
                        // Try to load saved TV address and connect
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        val savedAddress = prefs.getString("tv_address", null)
                        
                        if (savedAddress != null) {
                            bluetoothHidManager.connectToDevice(savedAddress)
                            
                            // Wait for connection (with timeout)
                            attempts = 0
                            while (!bluetoothHidManager.isConnected() && attempts < 30) {
                                Thread.sleep(100)
                                attempts++
                            }
                            
                            if (bluetoothHidManager.isConnected()) {
                                // Small delay to ensure connection is stable
                                Thread.sleep(200)
                                bluetoothHidManager.sendPowerCommand()
                            }
                        }
                    }
                }
                
                // Wait a bit before cleaning up
                Thread.sleep(500)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bluetoothHidManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
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

    companion object {
        private const val CHANNEL_ID = "tv_scheduler_service"
        private const val NOTIFICATION_ID = 1001
    }
}
