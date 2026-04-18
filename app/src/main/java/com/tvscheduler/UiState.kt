package com.tvscheduler

import android.bluetooth.BluetoothProfile

data class ConnectionState(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val state: Int = BluetoothProfile.STATE_DISCONNECTED,
    val isRegistered: Boolean = false
)

data class ScheduleState(
    val turnOnHour: Int = 8,
    val turnOnMinute: Int = 0,
    val turnOffHour: Int = 22,
    val turnOffMinute: Int = 0,
    val isTurnOnEnabled: Boolean = false,
    val isTurnOffEnabled: Boolean = false
)

data class UiState(
    val connectionState: ConnectionState = ConnectionState(),
    val scheduleState: ScheduleState = ScheduleState(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showSuccessMessage: String? = null
)
