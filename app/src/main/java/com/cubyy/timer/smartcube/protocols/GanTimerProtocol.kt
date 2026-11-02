package com.cubyy.timer.smartcube.protocols

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.cubyy.timer.smartcube.model.CubeMove
import java.util.UUID

/** BLE protocol for the GAN Smart Timer / GAN Halo Smart Timer. */
class GanTimerProtocol : SmartCubeProtocol {
    override val name: String = "GAN Halo Timer"
    private var displayedTimeMillis = 0L

    companion object {
        val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val TIME_CHARACTERISTIC: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val STATE_CHARACTERISTIC: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val STATE_GET_SET = 1
        const val STATE_HANDS_OFF = 2
        const val STATE_RUNNING = 3
        const val STATE_STOPPED = 4
        const val STATE_IDLE = 5
        const val STATE_HANDS_ON = 6
        const val STATE_FINISHED = 7
    }

    override fun handlesService(services: List<UUID>): Boolean = services.contains(SERVICE)
    override fun getRequiredServiceUuids(): List<UUID> = listOf(SERVICE)

    override fun onServicesDiscovered(gatt: BluetoothGatt): Boolean {
        val state = gatt.getService(SERVICE)?.getCharacteristic(STATE_CHARACTERISTIC) ?: return false
        gatt.setCharacteristicNotification(state, true)
        val descriptor = state.getDescriptor(CLIENT_CONFIG_DESCRIPTOR) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): List<CubeMove> = emptyList()

    override fun onTimerCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): SmartTimerEvent? {
        if (characteristic.uuid != STATE_CHARACTERISTIC || value.size < 6 || (value[0].toInt() and 0xFF) != 0xFE) return null
        if (!hasValidCrc(value)) return null
        val state = value[3].toInt() and 0xFF
        val time = if ((state == STATE_STOPPED || state == STATE_IDLE) && value.size >= 8) {
            val minutes = value[4].toInt() and 0xFF
            val seconds = value[5].toInt() and 0xFF
            val millis = (value[6].toInt() and 0xFF) or ((value[7].toInt() and 0xFF) shl 8)
            (minutes * 60_000L) + (seconds * 1_000L) + millis
        } else null
        if (state == STATE_STOPPED && time != null) displayedTimeMillis = time
        if (state == STATE_IDLE && time != null) displayedTimeMillis = time
        return SmartTimerEvent(
            state = state,
            recordedTimeMillis = if (state == STATE_STOPPED) time else null,
            handsBlockedByPreviousTime = state == STATE_HANDS_ON && displayedTimeMillis > 0L
        )
    }

    private fun hasValidCrc(data: ByteArray): Boolean {
        if (data.size < 4) return false
        var crc = 0xFFFF
        for (index in 2 until data.size - 2) {
            crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
        }
        val expected = (data[data.size - 2].toInt() and 0xFF) or ((data.last().toInt() and 0xFF) shl 8)
        return (crc and 0xFFFF) == expected
    }

    override fun reset() {
        displayedTimeMillis = 0L
    }

}
