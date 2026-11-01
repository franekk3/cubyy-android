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
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Protocol driver for GAN Gen 2 / Gen 3 / Gen 4 smart cubes:
 * GAN 356 i Carry, GAN 356 i Carry S, GAN 356 i Carry 2,
 * GAN 12 ui, GAN 12 ui FreeDoc, Monster Go 3Ai, AiCube.
 */
class GanGen2Protocol : SmartCubeProtocol {

    override val name: String = "GAN Gen 2/3/4 (i Carry, 12 ui)"

    companion object {
        private const val TAG = "GanGen2Protocol"

        val SERVICE_V2DATA: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dc4179")
        val CHRCT_V2READ: UUID = UUID.fromString("28be4cb6-cd67-11e9-a32f-2a2ae2dbcce4")
        val CHRCT_V2WRITE: UUID = UUID.fromString("28be4a4a-cd67-11e9-a32f-2a2ae2dbcce4")

        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val GEN2_KEY = intArrayOf(
            0x01, 0x02, 0x42, 0x28, 0x31, 0x91, 0x16, 0x07,
            0x20, 0x05, 0x18, 0x54, 0x42, 0x11, 0x12, 0x53
        )
        private val GEN2_IV = intArrayOf(
            0x11, 0x03, 0x32, 0x28, 0x21, 0x01, 0x76, 0x27,
            0x20, 0x95, 0x78, 0x14, 0x32, 0x12, 0x02, 0x43
        )

    }

    private var aesKey: ByteArray? = null
    private var iv: ByteArray? = null
    private var lastMoveCounter: Int = -1
    private var batteryPercent: Int = -1
    private var batteryRequestSent = false
    private var gyroSupported = false
    private var orientation: CubeOrientation? = null

    override fun handlesService(services: List<UUID>): Boolean {
        return services.contains(SERVICE_V2DATA)
    }

    override fun getRequiredServiceUuids(): List<UUID> {
        return listOf(SERVICE_V2DATA)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt): Boolean {
        reset()
        if (!initKeysFromMac(gatt.device.address)) return false

        val service = gatt.getService(SERVICE_V2DATA) ?: return false
        val readChar = service.getCharacteristic(CHRCT_V2READ) ?: return false

        if (!gatt.setCharacteristicNotification(readChar, true)) return false
        val descriptor = readChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
        if (descriptor == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun initKeysFromMac(mac: String): Boolean {
        try {
            val salt = mac.split(":").map { it.toInt(16) }.reversed().toIntArray()
            if (salt.size != 6) throw IllegalArgumentException("Invalid Bluetooth MAC address")
            val derivedKey = ByteArray(16)
            val derivedIv = ByteArray(16)
            for (i in 0 until 16) {
                val s = if (i < salt.size) salt[i] else 0
                derivedKey[i] = ((GEN2_KEY[i] + s) % 0xFF).toByte()
                derivedIv[i] = ((GEN2_IV[i] + s) % 0xFF).toByte()
            }
            aesKey = derivedKey
            iv = derivedIv
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive Gen2 key from MAC", e)
            return false
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (descriptor.characteristic.uuid != CHRCT_V2READ || descriptor.uuid != CLIENT_CONFIG_DESCRIPTOR) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Failed to enable GAN notifications: $status")
            return
        }

        writeCommand(gatt, 0x04)
    }

    override fun refreshBattery(gatt: BluetoothGatt) {
        writeCommand(gatt, 0x09)
    }

    private fun writeCommand(gatt: BluetoothGatt, commandType: Int): Boolean {
        val command = ByteArray(20).apply { this[0] = commandType.toByte() }
        val encrypted = transformChunks(command, Cipher.ENCRYPT_MODE) ?: return false
        val writeChar = gatt.getService(SERVICE_V2DATA)?.getCharacteristic(CHRCT_V2WRITE) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeCharacteristic(writeChar, encrypted, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            writeChar.value = encrypted
            @Suppress("DEPRECATION")
            return gatt.writeCharacteristic(writeChar)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): List<CubeMove> {
        if (characteristic.uuid != CHRCT_V2READ) return emptyList()

        if (aesKey == null && !initKeysFromMac(gatt.device.address)) return emptyList()
        val decrypted = transformChunks(value, Cipher.DECRYPT_MODE) ?: return emptyList()
        if (readBits(decrypted, 0, 4) == 0x04 && !batteryRequestSent) {
            batteryRequestSent = writeCommand(gatt, 0x09)
        }
        return decodeStatePacket(decrypted)
    }

    internal fun decodeStatePacket(data: ByteArray): List<CubeMove> {
        if (data.size < 16) return emptyList()
        return when (readBits(data, 0, 4)) {
            0x01 -> {
                orientation = CubeOrientation(
                    x = decodeSignedNormalized(readBits(data, 20, 16)),
                    y = decodeSignedNormalized(readBits(data, 36, 16)),
                    z = decodeSignedNormalized(readBits(data, 52, 16)),
                    w = decodeSignedNormalized(readBits(data, 4, 16))
                ).normalized()
                gyroSupported = true
                emptyList()
            }
            0x02 -> {
                val serial = readBits(data, 4, 8)
                if (lastMoveCounter == -1) return emptyList()
                val delta = minOf((serial - lastMoveCounter) and 0xFF, 7)
                lastMoveCounter = serial
                buildList {
                    for (index in delta - 1 downTo 0) {
                        val faceIndex = readBits(data, 12 + 5 * index, 4)
                        val direction = readBits(data, 16 + 5 * index, 1)
                        val face = "URFDLB".getOrNull(faceIndex)?.toString()?.let(CubeFace::fromSymbol)
                        if (face != null) add(CubeMove(face, if (direction == 0) 1 else -1))
                    }
                }
            }
            0x04 -> {
                if (lastMoveCounter == -1) lastMoveCounter = readBits(data, 4, 8)
                emptyList()
            }
            0x09 -> {
                batteryPercent = readBits(data, 8, 8).coerceAtMost(100)
                emptyList()
            }
            0x05 -> {
                gyroSupported = readBits(data, 104, 1) == 1
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun transformChunks(data: ByteArray, mode: Int): ByteArray? {
        return try {
            val key = aesKey ?: return null
            val ivBytes = iv ?: return null
            if (data.size < 16) return null
            val ret = data.copyOf()
            if (mode == Cipher.DECRYPT_MODE && ret.size > 16) transformChunk(ret, ret.size - 16, key, ivBytes, mode)
            transformChunk(ret, 0, key, ivBytes, mode)
            if (mode == Cipher.ENCRYPT_MODE && ret.size > 16) transformChunk(ret, ret.size - 16, key, ivBytes, mode)
            ret
        } catch (e: Exception) {
            Log.e(TAG, "GAN packet cryptography failed", e)
            null
        }
    }

    private fun transformChunk(data: ByteArray, offset: Int, key: ByteArray, ivBytes: ByteArray, mode: Int) {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))
        cipher.doFinal(data.copyOfRange(offset, offset + 16)).copyInto(data, offset)
    }

    private fun readBits(data: ByteArray, startBit: Int, bitLength: Int): Int {
        var result = 0
        for (bit in startBit until startBit + bitLength) {
            val byte = data[bit / 8].toInt() and 0xFF
            result = (result shl 1) or ((byte shr (7 - bit % 8)) and 1)
        }
        return result
    }

    private fun decodeSignedNormalized(value: Int): Float {
        val sign = if (value and 0x8000 != 0) -1f else 1f
        return sign * (value and 0x7FFF) / 0x7FFF.toFloat()
    }

    override fun getBatteryLevel(): Int = batteryPercent
    override fun supportsGyroscope(): Boolean = gyroSupported
    override fun getOrientation(): CubeOrientation? = orientation

    override fun reset() {
        lastMoveCounter = -1
        batteryPercent = -1
        batteryRequestSent = false
        gyroSupported = false
        orientation = null
    }
}

