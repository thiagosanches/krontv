package com.krontv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BluetoothHidManager(private val context: Context) {

    private val TAG = "BluetoothHidManager"
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    
    private var bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var savedDeviceAddress: String? = null
    private var shouldReconnect = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    
    var onConnectionStateChanged: ((Int, String) -> Unit)? = null
    var isRegistered = false
        private set

    private val hidSettings = BluetoothHidDeviceAppSdpSettings(
        "TV Scheduler",
        "Remote Control",
        "TV Scheduler",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        bluetoothHidDescriptor
    )

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = proxy as? BluetoothHidDevice
                bluetoothHidDevice?.let { hidDevice ->
                    if (checkBluetoothPermission()) {
                        isRegistered = hidDevice.registerApp(
                            hidSettings,
                            null,
                            null,
                            { it.run() },
                            hidCallback
                        )
                    }
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = null
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            synchronized(lock) {
                isRegistered = registered
                Log.d(TAG, "App registration changed: $registered")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (checkBluetoothPermission()) {
                synchronized(lock) {
                    val deviceName = device?.name ?: ""
                    Log.d(TAG, "Connection state: $state for $deviceName")
                    
                    onConnectionStateChanged?.invoke(state, deviceName)
                    
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        connectedDevice = device
                        reconnectAttempts = 0
                        cancelReconnect()
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        connectedDevice = null
                        // Auto-reconnect if enabled and not manual disconnect
                        if (shouldReconnect && reconnectAttempts < maxReconnectAttempts) {
                            scheduleReconnect()
                        }
                    }
                }
            }
        }
    }

    fun start() {
        synchronized(lock) {
            Log.d(TAG, "Starting Bluetooth HID")
            bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        }
    }
    
    fun restart() {
        synchronized(lock) {
            Log.d(TAG, "Restarting Bluetooth HID")
            stop()
            Thread.sleep(500) // Brief delay for cleanup
            start()
            
            // Auto-reconnect to saved device if available
            savedDeviceAddress?.let { address ->
                Thread.sleep(1000) // Wait for registration
                if (isRegistered) {
                    connectToDevice(address)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            Log.d(TAG, "Stopping Bluetooth HID")
            shouldReconnect = false
            cancelReconnect()
            
            bluetoothHidDevice?.let { hidDevice ->
                if (checkBluetoothPermission()) {
                    connectedDevice?.let { device ->
                        hidDevice.disconnect(device)
                    }
                    try {
                        hidDevice.unregisterApp()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unregistering app", e)
                    }
                }
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
            bluetoothHidDevice = null
            connectedDevice = null
            isRegistered = false
        }
    }

    fun connectToDevice(deviceAddress: String): Boolean {
        synchronized(lock) {
            if (!checkBluetoothPermission()) return false
            
            Log.d(TAG, "Connecting to device: $deviceAddress")
            savedDeviceAddress = deviceAddress
            shouldReconnect = true
            reconnectAttempts = 0
            
            bluetoothHidDevice?.let { hidDevice ->
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress.uppercase())
                device?.let {
                    return hidDevice.connect(it)
                }
            }
            return false
        }
    }

    fun disconnect(): Boolean {
        synchronized(lock) {
            if (!checkBluetoothPermission()) return false
            
            Log.d(TAG, "Manual disconnect")
            shouldReconnect = false
            cancelReconnect()
            
            bluetoothHidDevice?.let { hidDevice ->
                connectedDevice?.let { device ->
                    return hidDevice.disconnect(device)
                }
            }
            return false
        }
    }

    fun sendPowerCommand(): Boolean {
        synchronized(lock) {
            if (!checkBluetoothPermission()) return false
            
            var success = false
            connectedDevice?.let { device ->
                bluetoothHidDevice?.let { hidDevice ->
                    // Send power button press
                    success = hidDevice.sendReport(device, REMOTE_REPORT_ID, POWER_COMMAND)
                    if (success) {
                        // Send power button release
                        Thread.sleep(50) // Small delay between press and release
                        success = hidDevice.sendReport(device, REMOTE_REPORT_ID, REMOTE_INPUT_NONE)
                    }
                    Log.d(TAG, "Power command sent: $success")
                }
            }
            return success
        }
    }

    fun isConnected(): Boolean {
        synchronized(lock) {
            return connectedDevice != null
        }
    }

    fun getConnectedDeviceName(): String? {
        synchronized(lock) {
            if (!checkBluetoothPermission()) return null
            return connectedDevice?.name
        }
    }
    
    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectAttempts++
        val delayMs = (2000L * reconnectAttempts).coerceAtMost(30000L) // Exponential backoff, max 30s
        
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms")
        
        reconnectRunnable = Runnable {
            synchronized(lock) {
                savedDeviceAddress?.let { address ->
                    if (shouldReconnect && !isConnected()) {
                        Log.d(TAG, "Auto-reconnect attempt $reconnectAttempts")
                        connectToDevice(address)
                    }
                }
            }
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }
    
    private fun cancelReconnect() {
        reconnectRunnable?.let {
            handler.removeCallbacks(it)
            reconnectRunnable = null
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
