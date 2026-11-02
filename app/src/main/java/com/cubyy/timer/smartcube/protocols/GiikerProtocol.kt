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

        private val DECRYPTION_KEY = intArrayOf(
            176, 81, 104, 224, 86, 137, 237, 119, 38, 26, 193, 161,
            210, 126, 150, 81, 93, 13, 236, 249, 89, 235, 88, 24,
            113, 81, 214, 131, 130, 199, 2, 169, 39, 165, 171, 41
        )
        private val FACE_MAP = listOf(
            CubeFace.B, CubeFace.D, CubeFace.L, CubeFace.U, CubeFace.R, CubeFace.F
        )
    }

    private var batteryPercent: Int = -1
    private var previousMoveHistory: List<CubeMove>? = null

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

        return decodeMovePacket(value)
    }

    /** Decodes old plaintext packets and newer Xiaomi/GiiKER rotating-key packets. */
    internal fun decodeMovePacket(packet: ByteArray): List<CubeMove> {
        if (packet.size < 20) return emptyList()

        val data = packet.copyOf()
        val encrypted = (data[18].toInt() and 0xFF) == 0xA7
        if (encrypted) {
            val firstOffset = (data[19].toInt() ushr 4) and 0x0F
            val secondOffset = data[19].toInt() and 0x0F
            for (index in 0 until 18) {
                data[index] = (
                    (data[index].toInt() and 0xFF) +
                        DECRYPTION_KEY[firstOffset + index] + DECRYPTION_KEY[secondOffset + index]
                    ).toByte()
            }
        }

        val historyByteCount = if (encrypted) 2 else 4
        val history = buildList {
            for (index in 0 until historyByteCount) {
                val packedMove = data[16 + index].toInt() and 0xFF
                val faceCode = (packedMove ushr 4) and 0x0F
                val directionCode = packedMove and 0x0F
                val face = FACE_MAP.getOrNull(faceCode - 1) ?: continue
                val amount = when (directionCode) {
                    1 -> 1
                    2 -> 2
                    3 -> -1
                    else -> continue
                }
                add(CubeMove(face, amount))
            }
        }

        val previous = previousMoveHistory
        previousMoveHistory = history
        if (previous == null || history.isEmpty()) return emptyList()

        val overlap = (minOf(previous.size, history.size) downTo 1).firstOrNull { count ->
            previous.takeLast(count).zip(history.take(count)).all { (oldMove, newMove) ->
                oldMove.face == newMove.face && oldMove.amount == newMove.amount
            }
        } ?: 0
        return history.drop(overlap)
    }

    override fun getBatteryLevel(): Int = batteryPercent

    override fun reset() {
        batteryPercent = -1
        previousMoveHistory = null
    }
}

