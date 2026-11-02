package com.cubyy.timer.smartcube

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeOrientation
import com.cubyy.timer.smartcube.model.SmartCubeBrand
import com.cubyy.timer.smartcube.model.SmartCubeDevice
import com.cubyy.timer.smartcube.protocols.GanTimerProtocol
import com.cubyy.timer.smartcube.protocols.GanGen1Protocol
import com.cubyy.timer.smartcube.protocols.GanGen2Protocol
import com.cubyy.timer.smartcube.protocols.GiikerProtocol
import com.cubyy.timer.smartcube.protocols.MoyuProtocol
import com.cubyy.timer.smartcube.protocols.SmartCubeProtocol
import com.cubyy.timer.smartcube.protocols.SmartTimerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartCubeManager(private val context: Context) {

    companion object {
        private const val TAG = "SmartCubeManager"
        private const val SCAN_PERIOD = 12000L // 12 seconds auto-stop scan
        private const val BATTERY_REFRESH_PERIOD = 60_000L
        private const val PREFS_NAME = "smart_cube_connection"
        private const val KEY_LAST_CUBE_ADDRESS = "last_cube_address"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<SmartCubeDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<SmartCubeDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<SmartCubeDevice?>(null)
    val connectedDevice: StateFlow<SmartCubeDevice?> = _connectedDevice.asStateFlow()

    private val _cubeOrientation = MutableStateFlow<CubeOrientation?>(null)
    val cubeOrientation: StateFlow<CubeOrientation?> = _cubeOrientation.asStateFlow()

    private val _hasGyroscope = MutableStateFlow(false)
    val hasGyroscope: StateFlow<Boolean> = _hasGyroscope.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _cubeMoves = MutableSharedFlow<CubeMove>(extraBufferCapacity = 64)
    val cubeMoves: SharedFlow<CubeMove> = _cubeMoves.asSharedFlow()
    private val _timerEvents = MutableSharedFlow<SmartTimerEvent>(extraBufferCapacity = 16)
    val timerEvents: SharedFlow<SmartTimerEvent> = _timerEvents.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var activeProtocol: SmartCubeProtocol? = null
    private val batteryRefreshRunnable = object : Runnable {
        override fun run() {
            val gatt = activeGatt
            val protocol = activeProtocol
            if (gatt != null && protocol != null && _connectedDevice.value != null) {
                try {
                    protocol.refreshBattery(gatt)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Missing permission while refreshing battery", error)
                }
                mainHandler.postDelayed(this, BATTERY_REFRESH_PERIOD)
            }
        }
    }

    private val protocols = listOf(
        GanGen2Protocol(),
        GanGen1Protocol(),
        MoyuProtocol(),
        GiikerProtocol(),
        GanTimerProtocol()
    )

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: result.scanRecord?.deviceName
                val brand = SmartCubeDevice.identifyBrand(name)
                val scanRecord = result.scanRecord
                val advertisesGiikerService = scanRecord?.serviceUuids.orEmpty().any {
                    it.uuid == GiikerProtocol.SERVICE_DATA
                } || scanRecord?.serviceData?.keys.orEmpty().any {
                    it.uuid == GiikerProtocol.SERVICE_DATA
                }
                val isGiiker = advertisesGiikerService || brand == SmartCubeBrand.GIIKER
                // Some Xiaomi/GiiKER firmware advertises a generic device name. Match its
                // service UUID as well so discovery does not depend on the advertised name.
                val isTimer = SmartCubeDevice.isGanTimer(name)
                if (brand != SmartCubeBrand.UNKNOWN || (name != null && isPossibleSmartCube(name)) || isGiiker || isTimer) {
                    val smartDevice = SmartCubeDevice(
                        address = device.address,
                        name = if (isTimer) "GAN Halo Timer" else name ?: when {
                            isGiiker -> "Xiaomi GiiKER Cube"
                            else -> "Smart Cube"
                        },
                        brand = if (isGiiker) SmartCubeBrand.GIIKER else brand,
                        rssi = result.rssi,
                        isTimer = isTimer
                    )
                    updateDiscoveredDevice(smartDevice)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    private fun isPossibleSmartCube(name: String): Boolean {
        val u = name.uppercase()
        return u.contains("CUBE") || u.contains("GAN") || u.contains("MOYU") ||
                u.contains("GIIKER") || u.contains("RUBIK") || u.contains("SMART")
    }

    private fun updateDiscoveredDevice(device: SmartCubeDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address == device.address }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!isBluetoothEnabled) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        mainHandler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)

        try {
            scanner.startScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: SmartCubeDevice) {
        stopScan()
        disconnect()
        _cubeOrientation.value = null
        _hasGyroscope.value = false

        val bleDevice = try {
            bluetoothAdapter?.getRemoteDevice(device.address)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Invalid smart cube address", error)
            null
        } ?: return
        _isConnecting.value = true
        connectionPreferences.edit().putString(KEY_LAST_CUBE_ADDRESS, device.address).apply()
        try {
            activeGatt = bleDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (error: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied while connecting", error)
            _isConnecting.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun reconnectToLastCubeIfPermitted(): Boolean {
        val address = connectionPreferences.getString(KEY_LAST_CUBE_ADDRESS, null) ?: return false
        if (!isBluetoothEnabled || !hasBluetoothConnectPermission()) return false
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Saved smart cube address is invalid", error)
            null
        } ?: return false
        connect(
            SmartCubeDevice(
                address = address,
                name = device.name ?: "Smart Cube",
                brand = SmartCubeDevice.identifyBrand(device.name)
            )
        )
        return true
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun disconnect() {
        mainHandler.removeCallbacks(batteryRefreshRunnable)
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        activeProtocol?.reset()
        activeProtocol = null
        _connectedDevice.value = null
        _cubeOrientation.value = null
        _hasGyroscope.value = false
        _isConnecting.value = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server. Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
                _connectedDevice.value = null
                _isConnecting.value = false
                activeProtocol?.reset()
                activeProtocol = null
                _cubeOrientation.value = null
                _hasGyroscope.value = false
                mainHandler.removeCallbacks(batteryRefreshRunnable)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                _isConnecting.value = false
                return
            }

            val services = gatt.services.map { it.uuid }
            val advertisedDevice = _discoveredDevices.value.firstOrNull { it.address == gatt.device.address }
            val connectedName = gatt.device.name ?: advertisedDevice?.name
            val isTimer = advertisedDevice?.isTimer == true || SmartCubeDevice.isGanTimer(connectedName)
            val matchingProtocol = if (isTimer) {
                protocols.filterIsInstance<GanTimerProtocol>().firstOrNull { it.handlesService(services) }
            } else {
                protocols.filterNot { it is GanTimerProtocol }.firstOrNull { it.handlesService(services) }
            }

            if (matchingProtocol != null) {
                activeProtocol = matchingProtocol
                Log.d(TAG, "Matched protocol: ${matchingProtocol.name}")
                if (!matchingProtocol.onServicesDiscovered(gatt)) {
                    Log.e(TAG, "Failed to initialize ${matchingProtocol.name}")
                    activeProtocol = null
                    _isConnecting.value = false
                    gatt.disconnect()
                    return
                }

                val resolvedName = if (isTimer) "GAN Halo Timer" else connectedName ?: matchingProtocol.name
                val connectedBrand = when {
                    services.contains(GiikerProtocol.SERVICE_DATA) -> SmartCubeBrand.GIIKER
                    services.contains(GanGen1Protocol.SERVICE_DATA) || services.contains(GanGen2Protocol.SERVICE_V2DATA) -> SmartCubeBrand.GAN
                    services.contains(MoyuProtocol.SERVICE_UUID) -> SmartCubeBrand.MOYU
                    else -> advertisedDevice?.brand ?: SmartCubeBrand.UNKNOWN
                }
                val dev = SmartCubeDevice(
                    address = gatt.device.address,
                    name = resolvedName,
                    brand = connectedBrand,
                    batteryPercent = matchingProtocol.getBatteryLevel(),
                    isConnected = true,
                    isTimer = isTimer
                )
                _connectedDevice.value = dev
                _hasGyroscope.value = matchingProtocol.supportsGyroscope()
                _isConnecting.value = false
                mainHandler.removeCallbacks(batteryRefreshRunnable)
                mainHandler.postDelayed(batteryRefreshRunnable, BATTERY_REFRESH_PERIOD)
            } else {
                Log.w(TAG, "No matching smart cube protocol found for services: $services")
                _isConnecting.value = false
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            dispatchCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            dispatchCharacteristicChanged(gatt, characteristic, value)
        }

        private fun dispatchCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val protocol = activeProtocol ?: return
            protocol.onTimerCharacteristicChanged(characteristic, value)?.let { _timerEvents.tryEmit(it) }
            val moves = protocol.onCharacteristicChanged(gatt, characteristic, value)
            if (protocol.supportsGyroscope()) _hasGyroscope.value = true
            protocol.getOrientation()?.let { _cubeOrientation.value = it }
            for (move in moves) {
                _cubeMoves.tryEmit(move)
            }
            val battery = protocol.getBatteryLevel()
            if (battery >= 0 && _connectedDevice.value?.batteryPercent != battery) {
                _connectedDevice.value = _connectedDevice.value?.copy(batteryPercent = battery)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: android.bluetooth.BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed with status $status")
                gatt.disconnect()
                return
            }
            activeProtocol?.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                activeProtocol?.onCharacteristicRead(gatt, characteristic)
            }
        }
    }
}

