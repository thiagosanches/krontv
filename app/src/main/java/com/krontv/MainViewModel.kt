package com.krontv

import android.app.Application
import android.bluetooth.BluetoothProfile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore(name = "settings")

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private lateinit var bluetoothHidManager: BluetoothHidManager
    private lateinit var scheduleManager: ScheduleManager

    companion object {
        private val KEY_TURN_ON_HOUR = intPreferencesKey("turn_on_hour")
        private val KEY_TURN_ON_MINUTE = intPreferencesKey("turn_on_minute")
        private val KEY_TURN_OFF_HOUR = intPreferencesKey("turn_off_hour")
        private val KEY_TURN_OFF_MINUTE = intPreferencesKey("turn_off_minute")
        private val KEY_TURN_ON_ENABLED = booleanPreferencesKey("turn_on_enabled")
        private val KEY_TURN_OFF_ENABLED = booleanPreferencesKey("turn_off_enabled")
        private val KEY_TV_ADDRESS = stringPreferencesKey("tv_address")
    }

    fun initialize(bluetoothManager: BluetoothHidManager, scheduleManager: ScheduleManager) {
        this.bluetoothHidManager = bluetoothManager
        this.scheduleManager = scheduleManager

        bluetoothManager.onConnectionStateChanged = { state, deviceName ->
            updateConnectionState(state, deviceName)
        }

        loadSettings()
    }

    fun startBluetooth() {
        bluetoothHidManager.start()
        _uiState.update { it.copy(
            connectionState = it.connectionState.copy(isRegistered = bluetoothHidManager.isRegistered)
        )}
        
        // Try to auto-connect to saved device
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            val savedAddress = prefs[KEY_TV_ADDRESS]
            if (savedAddress != null) {
                connectToDevice(savedAddress)
            }
        }
    }

    fun connectToDevice(address: String) {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_TV_ADDRESS] = address
            }
        }
        
        val success = bluetoothHidManager.connectToDevice(address)
        if (!success) {
            _uiState.update { it.copy(
                isLoading = false,
                errorMessage = "Failed to connect to device"
            )}
        }
    }

    fun disconnect() {
        bluetoothHidManager.disconnect()
    }

    fun sendPowerCommand() {
        val success = bluetoothHidManager.sendPowerCommand()
        _uiState.update { it.copy(
            showSuccessMessage = if (success) "Power command sent!" else null,
            errorMessage = if (!success) "Failed to send command" else null
        )}
    }

    fun updateTurnOnTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(
            scheduleState = it.scheduleState.copy(
                turnOnHour = hour,
                turnOnMinute = minute
            )
        )}
        
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_TURN_ON_HOUR] = hour
                prefs[KEY_TURN_ON_MINUTE] = minute
            }
            updateSchedules()
        }
    }

    fun updateTurnOffTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(
            scheduleState = it.scheduleState.copy(
                turnOffHour = hour,
                turnOffMinute = minute
            )
        )}
        
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_TURN_OFF_HOUR] = hour
                prefs[KEY_TURN_OFF_MINUTE] = minute
            }
            updateSchedules()
        }
    }

    fun setTurnOnEnabled(enabled: Boolean) {
        _uiState.update { it.copy(
            scheduleState = it.scheduleState.copy(isTurnOnEnabled = enabled)
        )}
        
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_TURN_ON_ENABLED] = enabled
            }
            updateSchedules()
        }
    }

    fun setTurnOffEnabled(enabled: Boolean) {
        _uiState.update { it.copy(
            scheduleState = it.scheduleState.copy(isTurnOffEnabled = enabled)
        )}
        
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_TURN_OFF_ENABLED] = enabled
            }
            updateSchedules()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(
            errorMessage = null,
            showSuccessMessage = null
        )}
    }

    private fun updateConnectionState(state: Int, deviceName: String) {
        _uiState.update { it.copy(
            connectionState = ConnectionState(
                isConnected = state == BluetoothProfile.STATE_CONNECTED,
                deviceName = deviceName,
                state = state,
                isRegistered = bluetoothHidManager.isRegistered
            ),
            isLoading = state == BluetoothProfile.STATE_CONNECTING
        )}
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            
            val scheduleState = ScheduleState(
                turnOnHour = prefs[KEY_TURN_ON_HOUR] ?: 8,
                turnOnMinute = prefs[KEY_TURN_ON_MINUTE] ?: 0,
                turnOffHour = prefs[KEY_TURN_OFF_HOUR] ?: 22,
                turnOffMinute = prefs[KEY_TURN_OFF_MINUTE] ?: 0,
                isTurnOnEnabled = prefs[KEY_TURN_ON_ENABLED] ?: false,
                isTurnOffEnabled = prefs[KEY_TURN_OFF_ENABLED] ?: false
            )
            
            _uiState.update { it.copy(scheduleState = scheduleState) }
            updateSchedules()
        }
    }

    private suspend fun updateSchedules() {
        val state = _uiState.value.scheduleState
        
        if (state.isTurnOnEnabled) {
            scheduleManager.scheduleTurnOn(state.turnOnHour, state.turnOnMinute)
        } else {
            scheduleManager.cancelTurnOn()
        }

        if (state.isTurnOffEnabled) {
            scheduleManager.scheduleTurnOff(state.turnOffHour, state.turnOffMinute)
        } else {
            scheduleManager.cancelTurnOff()
        }
    }

    fun stopBluetooth() {
        bluetoothHidManager.stop()
    }
}
