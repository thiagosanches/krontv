package com.tvscheduler

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.tvscheduler.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var bluetoothHidManager: BluetoothHidManager
    private lateinit var scheduleManager: ScheduleManager

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startBluetooth()
        } else {
            showSnackbar("Bluetooth permissions required", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeManagers()
        setupUI()
        observeViewModel()
        requestPermissions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun initializeManagers() {
        bluetoothHidManager = BluetoothHidManager(this)
        scheduleManager = ScheduleManager(this)
        viewModel.initialize(bluetoothHidManager, scheduleManager)
    }

    private fun setupUI() {
        binding.connectButton.setOnClickListener {
            val state = viewModel.uiState.value.connectionState
            if (state.isConnected) {
                viewModel.disconnect()
            } else {
                showDeviceSelectionDialog()
            }
        }

        binding.testPowerButton.setOnClickListener {
            viewModel.sendPowerCommand()
        }

        binding.turnOnTimeButton.setOnClickListener {
            showTimePicker(true)
        }

        binding.turnOffTimeButton.setOnClickListener {
            showTimePicker(false)
        }

        binding.enableTurnOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTurnOnEnabled(isChecked)
        }

        binding.enableTurnOffSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTurnOffEnabled(isChecked)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: UiState) {
        // Update connection state
        updateConnectionUI(state.connectionState)
        
        // Update schedule state
        updateScheduleUI(state.scheduleState)
        
        // Show loading
        binding.connectionProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        // Show messages
        state.errorMessage?.let {
            showSnackbar(it, isError = true)
            viewModel.clearMessages()
        }
        
        state.showSuccessMessage?.let {
            showSnackbar(it, isError = false)
            viewModel.clearMessages()
        }
    }

    private fun updateConnectionUI(state: ConnectionState) {
        when (state.state) {
            BluetoothProfile.STATE_CONNECTED -> {
                binding.connectionStatus.text = "Connected to ${state.deviceName}"
                binding.testPowerButton.isEnabled = true
                binding.connectButton.text = "Disconnect"
                binding.connectButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_bluetooth)
                updateStatusIndicator(true)
                animateCardElevation(binding.connectionCard, true)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                binding.connectionStatus.text = "Not Connected"
                binding.testPowerButton.isEnabled = false
                binding.connectButton.text = "Connect to TV"
                binding.connectButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_bluetooth)
                updateStatusIndicator(false)
                animateCardElevation(binding.connectionCard, false)
            }
            BluetoothProfile.STATE_CONNECTING -> {
                binding.connectionStatus.text = "Connecting..."
                binding.testPowerButton.isEnabled = false
                updateStatusIndicator(false)
            }
        }
    }

    private fun updateScheduleUI(state: ScheduleState) {
        binding.turnOnTimeButton.text = String.format("%02d:%02d", state.turnOnHour, state.turnOnMinute)
        binding.turnOffTimeButton.text = String.format("%02d:%02d", state.turnOffHour, state.turnOffMinute)
        
        // Update switches without triggering listeners
        binding.enableTurnOnSwitch.setOnCheckedChangeListener(null)
        binding.enableTurnOffSwitch.setOnCheckedChangeListener(null)
        
        binding.enableTurnOnSwitch.isChecked = state.isTurnOnEnabled
        binding.enableTurnOffSwitch.isChecked = state.isTurnOffEnabled
        
        binding.enableTurnOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTurnOnEnabled(isChecked)
        }
        binding.enableTurnOffSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTurnOffEnabled(isChecked)
        }
    }

    private fun updateStatusIndicator(connected: Boolean) {
        val color = if (connected) {
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        } else {
            ContextCompat.getColor(this, android.R.color.darker_gray)
        }
        binding.statusIndicator.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun animateCardElevation(view: View, elevated: Boolean) {
        val targetElevation = if (elevated) 4f else 0f
        val animator = ValueAnimator.ofFloat(view.elevation, targetElevation)
        animator.duration = 300
        animator.addUpdateListener { animation ->
            view.elevation = animation.animatedValue as Float
        }
        animator.start()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            // We don't need BLUETOOTH_SCAN since we only use paired devices
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            viewModel.startBluetooth()
        }

        // Check for exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to schedule exact alarms for TV power control.")
                    .setPositiveButton("Grant") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showDeviceSelectionDialog() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showSnackbar("Bluetooth permission required", isError = true)
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        
        if (pairedDevices.isNullOrEmpty()) {
            showSnackbar("No paired devices found. Pair your TV in Android Bluetooth settings first.", isError = true)
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        val deviceAddresses = pairedDevices.map { it.address }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select TV Device")
            .setItems(deviceNames) { _, which ->
                val selectedAddress = deviceAddresses[which]
                viewModel.connectToDevice(selectedAddress)
            }
            .show()
    }

    private fun showTimePicker(isTurnOn: Boolean) {
        val state = viewModel.uiState.value.scheduleState
        val currentHour = if (isTurnOn) state.turnOnHour else state.turnOffHour
        val currentMinute = if (isTurnOn) state.turnOnMinute else state.turnOffMinute

        TimePickerDialog(this, { _, hour, minute ->
            if (isTurnOn) {
                viewModel.updateTurnOnTime(hour, minute)
            } else {
                viewModel.updateTurnOffTime(hour, minute)
            }
        }, currentHour, currentMinute, true).show()
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        Snackbar.make(binding.root, message, 
            if (isError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        ).apply {
            if (isError) {
                setBackgroundTint(ContextCompat.getColor(this@MainActivity, 
                    com.google.android.material.R.color.design_default_color_error))
            }
            show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopBluetooth()
    }
}
