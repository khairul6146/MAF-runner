package com.mafrunner.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)

class BleHeartRateManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val parser = BleHeartRateParser()
    private val batteryParser = BleBatteryParser()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _heartRateFlow = MutableStateFlow<Int?>(null)
    val heartRateFlow: StateFlow<Int?> = _heartRateFlow.asStateFlow()

    private val _batteryLevelFlow = MutableStateFlow<Int?>(null)
    val batteryLevelFlow: StateFlow<Int?> = _batteryLevelFlow.asStateFlow()

    private val _ecgSamplesFlow = MutableStateFlow<List<Int>>(emptyList())
    val ecgSamplesFlow: StateFlow<List<Int>> = _ecgSamplesFlow.asStateFlow()

    private val _currentHrvRmssd = MutableStateFlow<Double?>(null)
    val currentHrvRmssd: StateFlow<Double?> = _currentHrvRmssd.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var targetMacAddress: String? = null
    private var isManualDisconnect = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val pendingDescriptorWrites = ArrayDeque<BluetoothGattDescriptor>()
    private var isWritingDescriptor = false

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        val FITCARE_ECG_SERVICE_UUID: UUID = UUID.fromString("0000EC00-0000-1000-8000-00805F9B34FB")
        val FITCARE_ECG_STREAM_UUID: UUID = UUID.fromString("0000EC09-0000-1000-8000-00805F9B34FB")
        val FITCARE_ECG_CONTROL_UUID: UUID = UUID.fromString("0000EC0A-0000-1000-8000-00805F9B34FB")
        val FITCARE_ARMBAND_CONTROL_UUID: UUID = UUID.fromString("A430FCA2-044B-C6E2-92B2-61774EFF0F01")

        private const val PREFS_NAME = "maf_ble_prefs"
        private const val KEY_SAVED_MAC = "saved_mac_address"
    }

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        targetMacAddress = prefs.getString(KEY_SAVED_MAC, null)
    }

    fun getSavedMacAddress(): String? = targetMacAddress

    fun saveMacAddress(mac: String) {
        targetMacAddress = mac
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_MAC, mac)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.SCANNING

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "HR Strap"
            val address = device.address

            val current = _discoveredDevices.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.address == address }
            val item = DiscoveredBleDevice(device, name, address, result.rssi)
            if (existingIndex >= 0) {
                current[existingIndex] = item
            } else {
                current.add(item)
            }
            _discoveredDevices.value = current

            // Auto-connect if matches saved MAC
            if (targetMacAddress != null && address.equals(targetMacAddress, ignoreCase = true)) {
                stopScan()
                connect(address)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        isManualDisconnect = false
        targetMacAddress = macAddress
        saveMacAddress(macAddress)
        stopScan()

        val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: return
        _connectionState.value = BleConnectionState.CONNECTING

        activeGatt?.close()
        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isManualDisconnect = true
        synchronized(pendingDescriptorWrites) {
            pendingDescriptorWrites.clear()
            isWritingDescriptor = false
        }
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _heartRateFlow.value = null
        _batteryLevelFlow.value = null
        _currentHrvRmssd.value = null
        _ecgSamplesFlow.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun sendStrapMaxHrThreshold(maxHr: Int) {
        val gatt = activeGatt ?: return
        if (_connectionState.value != BleConnectionState.CONNECTED) return

        val command = FitcareProtocolEngine.buildArmbandThresholdCommand(maxHr)

        var targetChar: BluetoothGattCharacteristic? = null
        for (service in gatt.services) {
            val found = service.getCharacteristic(FITCARE_ECG_CONTROL_UUID)
                ?: service.getCharacteristic(FITCARE_ARMBAND_CONTROL_UUID)
            if (found != null) {
                targetChar = found
                break
            }
        }

        if (targetChar != null) {
            runCatching {
                val writeType = if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                    (targetChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(targetChar, command, writeType)
                } else {
                    @Suppress("DEPRECATION")
                    targetChar.value = command
                    @Suppress("DEPRECATION")
                    targetChar.writeType = writeType
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(targetChar)
                }
            }
        }
    }

    private fun queueDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        synchronized(pendingDescriptorWrites) {
            pendingDescriptorWrites.add(descriptor)
        }
        processNextDescriptorWrite(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun processNextDescriptorWrite(gatt: BluetoothGatt) {
        val next = synchronized(pendingDescriptorWrites) {
            if (isWritingDescriptor) return
            val d = pendingDescriptorWrites.pollFirst() ?: return
            isWritingDescriptor = true
            d
        }
        @Suppress("DEPRECATION")
        next.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        val initiated = gatt.writeDescriptor(next)
        if (!initiated) {
            synchronized(pendingDescriptorWrites) {
                isWritingDescriptor = false
            }
            processNextDescriptorWrite(gatt)
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            HEART_RATE_MEASUREMENT_UUID -> {
                val measurement = parser.parse(value)
                if (measurement.hr > 0) {
                    _heartRateFlow.value = measurement.hr
                }
                if (measurement.rrIntervalsMs.size >= 2) {
                    val rmssd = FitcareProtocolEngine.calculateRmssd(measurement.rrIntervalsMs)
                    if (rmssd != null) {
                        _currentHrvRmssd.value = rmssd
                    }
                }
            }
            BATTERY_LEVEL_UUID -> {
                val level = batteryParser.parse(value)
                if (level != null) {
                    _batteryLevelFlow.value = level
                }
            }
            FITCARE_ECG_STREAM_UUID -> {
                val samples = FitcareProtocolEngine.parseEcgPacket(value)
                if (!samples.isNullOrEmpty()) {
                    _ecgSamplesFlow.value = samples
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = BleConnectionState.CONNECTED
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingDescriptorWrites.clear()
                isWritingDescriptor = false
                _connectionState.value = BleConnectionState.DISCONNECTED
                _heartRateFlow.value = null
                _batteryLevelFlow.value = null
                _currentHrvRmssd.value = null
                _ecgSamplesFlow.value = emptyList()
                // Auto-reconnect loop
                if (!isManualDisconnect) {
                    targetMacAddress?.let { mac ->
                        scope.launch {
                            delay(2000L)
                            if (!isManualDisconnect && _connectionState.value == BleConnectionState.DISCONNECTED) {
                                connect(mac)
                            }
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Heart Rate Service
                val service = gatt.getService(HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        queueDescriptorWrite(gatt, descriptor)
                    }
                }

                // Battery Service
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                if (batteryChar != null) {
                    gatt.readCharacteristic(batteryChar)
                    if ((batteryChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(batteryChar, true)
                    }
                }

                // Fitcare ECG Service
                val ecgService = gatt.getService(FITCARE_ECG_SERVICE_UUID)
                val ecgChar = ecgService?.getCharacteristic(FITCARE_ECG_STREAM_UUID)
                if (ecgChar != null) {
                    gatt.setCharacteristicNotification(ecgChar, true)
                    val ecgDescriptor = ecgChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (ecgDescriptor != null) {
                        queueDescriptorWrite(gatt, ecgDescriptor)
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            synchronized(pendingDescriptorWrites) {
                isWritingDescriptor = false
            }
            processNextDescriptorWrite(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                val level = batteryParser.parse(value)
                if (level != null) {
                    _batteryLevelFlow.value = level
                }
            }
        }

        @Deprecated("Deprecated in Java for API 33+")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                @Suppress("DEPRECATION")
                val level = batteryParser.parse(characteristic.value)
                if (level != null) {
                    _batteryLevelFlow.value = level
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        @Deprecated("Deprecated in Java for API 33+")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value
            if (bytes != null) {
                handleCharacteristicChanged(characteristic, bytes)
            }
        }
    }
}
