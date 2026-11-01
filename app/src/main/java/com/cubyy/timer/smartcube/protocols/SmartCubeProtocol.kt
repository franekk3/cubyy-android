package com.cubyy.timer.smartcube.protocols

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeOrientation
import java.util.UUID

interface SmartCubeProtocol {
    val name: String
    fun handlesService(services: List<UUID>): Boolean
    fun getRequiredServiceUuids(): List<UUID>
    fun onServicesDiscovered(gatt: BluetoothGatt): Boolean
    fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): List<CubeMove>
    fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {}
    fun refreshBattery(gatt: BluetoothGatt) {}
    fun supportsGyroscope(): Boolean = false
    fun getOrientation(): CubeOrientation? = null
    fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {}
    fun getBatteryLevel(): Int = -1
    fun reset() {}
}

