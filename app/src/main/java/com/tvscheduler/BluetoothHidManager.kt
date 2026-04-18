package com.tvscheduler

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BluetoothHidManager(private val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    
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
            isRegistered = registered
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (checkBluetoothPermission()) {
                val deviceName = device?.name ?: ""
                onConnectionStateChanged?.invoke(state, deviceName)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                }
            }
        }
    }

    fun start() {
        bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        bluetoothHidDevice?.let { hidDevice ->
            if (checkBluetoothPermission()) {
                connectedDevice?.let { device ->
                    hidDevice.disconnect(device)
                }
                hidDevice.unregisterApp()
            }
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        bluetoothHidDevice = null
        connectedDevice = null
        isRegistered = false
    }

    fun connectToDevice(deviceAddress: String): Boolean {
        if (!checkBluetoothPermission()) return false
        
        bluetoothHidDevice?.let { hidDevice ->
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress.uppercase())
            device?.let {
                return hidDevice.connect(it)
            }
        }
        return false
    }

    fun disconnect(): Boolean {
        if (!checkBluetoothPermission()) return false
        
        bluetoothHidDevice?.let { hidDevice ->
            connectedDevice?.let { device ->
                hidDevice.disconnect(device)
                return hidDevice.disconnect(device) // Called twice to ensure disconnection
            }
        }
        return false
    }

    fun sendPowerCommand(): Boolean {
        if (!checkBluetoothPermission()) return false
        
        var success = false
        connectedDevice?.let { device ->
            bluetoothHidDevice?.let { hidDevice ->
                // Send power button press
                success = hidDevice.sendReport(device, REMOTE_REPORT_ID, POWER_COMMAND)
                if (success) {
                    // Send power button release
                    Thread.sleep(50) // Small delay between press and release
                    hidDevice.sendReport(device, REMOTE_REPORT_ID, REMOTE_INPUT_NONE)
                }
            }
        }
        return success
    }

    fun isConnected(): Boolean {
        return connectedDevice != null
    }

    fun getConnectedDeviceName(): String? {
        if (!checkBluetoothPermission()) return null
        return connectedDevice?.name
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
