package com.cubyy.timer.smartcube.protocols

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import java.util.UUID

/**
 * Protocol driver for MoYu Smart Cubes (MoYu WeiLong AI, MoYu AI 2023).
 */
class MoyuProtocol : SmartCubeProtocol {

    override val name: String = "MoYu Smart Cube"

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("00001000-0000-1000-8000-00805f9b34fb")
        val CHRCT_TURN: UUID = UUID.fromString("00001003-0000-1000-8000-00805f9b34fb")
        val CHRCT_READ: UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb")
        val CHRCT_GYRO: UUID = UUID.fromString("00001004-0000-1000-8000-00805f9b34fb")

        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // MoYu face index mapping: [F, B, L, R, D, U] or [U, D, F, B, L, R]
        private val FACE_MAP = arrayOf(
            CubeFace.U,
            CubeFace.D,
            CubeFace.F,
            CubeFace.B,
            CubeFace.L,
            CubeFace.R
        )
    }

    private var batteryPercent: Int = -1

    override fun handlesService(services: List<UUID>): Boolean {
        return services.contains(SERVICE_UUID)
    }

    override fun getRequiredServiceUuids(): List<UUID> {
        return listOf(SERVICE_UUID)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(SERVICE_UUID) ?: return false
        val turnChar = service.getCharacteristic(CHRCT_TURN) ?: return false

        gatt.setCharacteristicNotification(turnChar, true)
        val descriptor = turnChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
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
        if (characteristic.uuid != CHRCT_TURN) return emptyList()

        val data = value
        if (data.isEmpty()) return emptyList()

        val numMoves = data[0].toInt() and 0xFF
        if (data.size < 1 + numMoves * 6) return emptyList()

        val moves = mutableListOf<CubeMove>()
        for (i in 0 until numMoves) {
            val offset = 1 + i * 6
            val faceIndex = data[offset + 4].toInt() and 0xFF
            val dirRaw = data[offset + 5].toInt() and 0xFF

            if (faceIndex in FACE_MAP.indices) {
                val face = FACE_MAP[faceIndex]
                // 1 = Clockwise, 2 = Counter-Clockwise
                val amount = if (dirRaw > 18 || dirRaw == 2) -1 else 1
                moves.add(CubeMove(face, amount))
            }
        }
        return moves
    }

    override fun getBatteryLevel(): Int = batteryPercent

    override fun reset() {
        batteryPercent = -1
    }
}

