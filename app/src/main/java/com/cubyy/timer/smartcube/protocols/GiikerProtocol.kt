package com.cubyy.timer.smartcube.protocols

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import java.util.UUID

/**
 * Protocol driver for GiiKER Super Cube (i3s, i2, etc.)
 */
class GiikerProtocol : SmartCubeProtocol {

    override val name: String = "GiiKER Super Cube"

    companion object {
        val SERVICE_DATA: UUID = UUID.fromString("0000aadb-0000-1000-8000-00805f9b34fb")
        val CHRCT_DATA: UUID = UUID.fromString("0000aadc-0000-1000-8000-00805f9b34fb")

        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // GiiKER face mapping: 1=B, 2=D, 3=L, 4=U, 5=R, 6=F
        private val FACE_MAP = mapOf(
            1 to CubeFace.B,
            2 to CubeFace.D,
            3 to CubeFace.L,
            4 to CubeFace.U,
            5 to CubeFace.R,
            6 to CubeFace.F
        )
    }

    private var batteryPercent: Int = -1

    override fun handlesService(services: List<UUID>): Boolean {
        return services.contains(SERVICE_DATA)
    }

    override fun getRequiredServiceUuids(): List<UUID> {
        return listOf(SERVICE_DATA)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(SERVICE_DATA) ?: return false
        val dataChar = service.getCharacteristic(CHRCT_DATA) ?: return false

        gatt.setCharacteristicNotification(dataChar, true)
        val descriptor = dataChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
        return true
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): List<CubeMove> {
        if (characteristic.uuid != CHRCT_DATA) return emptyList()

        val data = value
        if (data.size < 17) return emptyList()

        // Byte 16 contains last move
        val moveByte = data[16].toInt() and 0xFF
        val faceCode = (moveByte shr 4) and 0x0F
        val dirCode = moveByte and 0x0F

        val face = FACE_MAP[faceCode] ?: return emptyList()
        val amount = when (dirCode) {
            1 -> 1
            2 -> 2
            3 -> -1
            else -> 1
        }

        return listOf(CubeMove(face, amount))
    }

    override fun getBatteryLevel(): Int = batteryPercent

    override fun reset() {
        batteryPercent = -1
    }
}

