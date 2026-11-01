package com.cubyy.timer.smartcube.protocols

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeOrientation
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Protocol driver for original GAN Smart Cubes:
 * GAN 356 i, GAN 356 i Play, GAN 356 i 2.
 */
class GanGen1Protocol : SmartCubeProtocol {

    override val name: String = "GAN Gen 1 (356 i)"

    companion object {
        private const val TAG = "GanGen1Protocol"

        val SERVICE_DATA: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHRCT_STATE: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
        val CHRCT_BATTERY: UUID = UUID.fromString("0000fff7-0000-1000-8000-00805f9b34fb")
        val CHRCT_FACELETS: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        val SERVICE_INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val CHRCT_SYSTEM_ID: UUID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb")
        val CHRCT_VERSION: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val KEY_10 = intArrayOf(
            198, 202, 21, 223, 79, 110, 19, 182, 119, 13, 230, 89, 58, 175, 186, 162
        )
        private val KEY_11 = intArrayOf(
            67, 226, 91, 214, 125, 220, 120, 216, 7, 96, 163, 218, 130, 60, 1, 241
        )

        private val MOVE_MAP = mapOf(
            0 to CubeMove(CubeFace.U, 1),
            2 to CubeMove(CubeFace.U, -1),
            3 to CubeMove(CubeFace.R, 1),
            5 to CubeMove(CubeFace.R, -1),
            6 to CubeMove(CubeFace.F, 1),
            8 to CubeMove(CubeFace.F, -1),
            9 to CubeMove(CubeFace.D, 1),
            11 to CubeMove(CubeFace.D, -1),
            12 to CubeMove(CubeFace.L, 1),
            14 to CubeMove(CubeFace.L, -1),
            15 to CubeMove(CubeFace.B, 1),
            17 to CubeMove(CubeFace.B, -1)
        )
    }

    private var aesKey: ByteArray? = null
    private var lastMoveCounter: Int = -1
    private var batteryPercent: Int = -1
    private var systemId: ByteArray? = null
    private var orientation: CubeOrientation? = null

    override fun handlesService(services: List<UUID>): Boolean {
        return services.contains(SERVICE_DATA)
    }

    override fun getRequiredServiceUuids(): List<UUID> {
        return listOf(SERVICE_DATA, SERVICE_INFO)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt): Boolean {
        reset()
        // First read System ID to construct the AES decryption key
        val infoService = gatt.getService(SERVICE_INFO)
        val sysIdChar = infoService?.getCharacteristic(CHRCT_SYSTEM_ID)
        if (sysIdChar != null) {
            gatt.readCharacteristic(sysIdChar)
        } else {
            // Fallback: derive key directly from device Bluetooth MAC address
            val mac = gatt.device.address
            initKeyFromMac(mac)
            subscribeToState(gatt)
        }
        return true
    }

    private fun initKeyFromMac(mac: String) {
        try {
            val parts = mac.split(":").map { it.toInt(16) }
            val reversed = parts.reversed().toIntArray()
            val derived = ByteArray(16)
            for (i in 0 until 16) {
                val salt = if (i < reversed.size) reversed[i] else 0
                derived[i] = ((KEY_11[i] + salt) % 256).toByte()
            }
            aesKey = derived
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive key from MAC", e)
        }
    }

    private fun subscribeToState(gatt: BluetoothGatt) {
        val dataService = gatt.getService(SERVICE_DATA) ?: return
        val stateChar = dataService.getCharacteristic(CHRCT_STATE) ?: return
        gatt.setCharacteristicNotification(stateChar, true)

        val descriptor = stateChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
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
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == CHRCT_SYSTEM_ID) {
            @Suppress("DEPRECATION")
            systemId = characteristic.value
            systemId?.let { idBytes ->
                val reversed = idBytes.reversedArray()
                val key = ByteArray(16)
                for (i in 0 until 16) {
                    val sysVal = if (i < reversed.size) (reversed[i].toInt() and 0xFF) else 0
                    key[i] = ((KEY_11[i] + sysVal) % 256).toByte()
                }
                aesKey = key
                subscribeToState(gatt)
            }
        } else if (characteristic.uuid == CHRCT_BATTERY) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value
            if (bytes != null && bytes.isNotEmpty()) {
                batteryPercent = bytes[bytes.size - 1].toInt() and 0xFF
            }
        }
    }

    override fun refreshBattery(gatt: BluetoothGatt) {
        val batteryCharacteristic = gatt.getService(SERVICE_DATA)?.getCharacteristic(CHRCT_BATTERY) ?: return
        @Suppress("MissingPermission")
        gatt.readCharacteristic(batteryCharacteristic)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): List<CubeMove> {
        if (characteristic.uuid != CHRCT_STATE) return emptyList()

        val raw = value
        val key = aesKey ?: run {
            initKeyFromMac(gatt.device.address)
            aesKey ?: return emptyList()
        }

        val decrypted = decryptBlock(raw, key) ?: return emptyList()
        if (decrypted.size < 19) return emptyList()

        orientation = decodeOrientation(decrypted)

        val moveCounter = decrypted[12].toInt() and 0xFF
        if (lastMoveCounter == -1) {
            lastMoveCounter = moveCounter
            return emptyList()
        }

        val delta = (moveCounter - lastMoveCounter) and 0xFF
        if (delta == 0) return emptyList()

        val count = minOf(delta, 6)
        val moves = mutableListOf<CubeMove>()
        for (i in (19 - count) until 19) {
            val moveCode = decrypted[i].toInt() and 0xFF
            MOVE_MAP[moveCode]?.let { moves.add(it) }
        }

        lastMoveCounter = moveCounter
        return moves
    }

    private fun decryptBlock(data: ByteArray, key: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            null
        }
    }

    private fun decodeOrientation(data: ByteArray): CubeOrientation? {
        if (data.size < 6) return null
        val view = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var x = view.getShort(0).toFloat() / 16384f
        var y = view.getShort(2).toFloat() / 16384f
        val z = view.getShort(4).toFloat() / 16384f
        val adjusted = floatArrayOf(-y, z, -x)
        x = adjusted[0]
        y = adjusted[1]
        val adjustedZ = adjusted[2]
        val w = kotlin.math.sqrt((1f - x * x - y * y - adjustedZ * adjustedZ).coerceAtLeast(0f))
        return CubeOrientation(x, y, adjustedZ, w).normalized()
    }

    override fun getBatteryLevel(): Int = batteryPercent
    override fun supportsGyroscope(): Boolean = orientation != null
    override fun getOrientation(): CubeOrientation? = orientation

    override fun reset() {
        lastMoveCounter = -1
        batteryPercent = -1
        orientation = null
    }
}

