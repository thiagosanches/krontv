package com.krontv

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BluetoothStateReceiver"
        var onBluetoothStateChanged: ((Boolean) -> Unit)? = null
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned ON")
                    onBluetoothStateChanged?.invoke(true)
                }
                BluetoothAdapter.STATE_OFF -> {
                    Log.d(TAG, "Bluetooth turned OFF")
                    onBluetoothStateChanged?.invoke(false)
                }
            }
        }
    }
}
