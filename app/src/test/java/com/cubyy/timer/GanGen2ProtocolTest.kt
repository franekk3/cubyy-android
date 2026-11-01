package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.protocols.GanGen2Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GanGen2ProtocolTest {

    @Test
    fun movePacketsAreIgnoredUntilFaceletsInitializeSerial() {
        val protocol = GanGen2Protocol()

        assertTrue(protocol.decodeStatePacket(movePacket(serial = 1, moves = listOf(2 to 0))).isEmpty())
        protocol.decodeStatePacket(faceletsPacket(serial = 0))

        val moves = protocol.decodeStatePacket(movePacket(serial = 1, moves = listOf(2 to 0)))

        assertEquals(listOf(CubeFace.F), moves.map { it.face })
        assertEquals(listOf(1), moves.map { it.amount })
    }

    @Test
    fun moveHistoryIsReturnedOldestFirstWithDirectionPreserved() {
        val protocol = GanGen2Protocol()
        protocol.decodeStatePacket(faceletsPacket(serial = 40))

        val moves = protocol.decodeStatePacket(
            movePacket(serial = 42, moves = listOf(5 to 1, 1 to 0))
        )

        assertEquals(listOf(CubeFace.R, CubeFace.B), moves.map { it.face })
        assertEquals(listOf(1, -1), moves.map { it.amount })
    }

    @Test
    fun serialCounterWrapsFrom255ToZero() {
        val protocol = GanGen2Protocol()
        protocol.decodeStatePacket(faceletsPacket(serial = 255))

        val moves = protocol.decodeStatePacket(movePacket(serial = 0, moves = listOf(0 to 1)))

        assertEquals(CubeFace.U, moves.single().face)
        assertEquals(-1, moves.single().amount)
    }

    @Test
    fun batteryEventUpdatesPercentage() {
        val protocol = GanGen2Protocol()

        protocol.decodeStatePacket(ByteArray(20).apply {
            writeBits(0, 4, 0x09)
            writeBits(8, 8, 73)
        })

        assertEquals(73, protocol.getBatteryLevel())
    }

    @Test
    fun resetMarksBatteryAsUnknownUntilCubeReportsIt() {
        val protocol = GanGen2Protocol()
        protocol.reset()

        assertEquals(-1, protocol.getBatteryLevel())
    }

    @Test
    fun gyroEventDecodesQuaternionAndMarksSupport() {
        val protocol = GanGen2Protocol()
        val packet = ByteArray(20).apply {
            writeBits(0, 4, 0x01)
            writeBits(4, 16, 0x7FFF)
            writeBits(20, 16, 0)
            writeBits(36, 16, 0)
            writeBits(52, 16, 0)
        }

        protocol.decodeStatePacket(packet)

        assertEquals(true, protocol.supportsGyroscope())
        assertEquals(1f, protocol.getOrientation()?.w ?: 0f, 0.0001f)
        assertEquals(0f, protocol.getOrientation()?.x ?: 1f, 0.0001f)
    }

    @Test
    fun gyroEventReadsEachQuaternionComponentFromDocumentedOffsets() {
        val protocol = GanGen2Protocol()
        val packet = ByteArray(20).apply {
            writeBits(0, 4, 0x01)
            writeBits(4, 16, 0x4000)
            writeBits(20, 16, 0x2000)
            writeBits(36, 16, 0x1000)
            writeBits(52, 16, 0x0800)
        }

        protocol.decodeStatePacket(packet)
        val orientation = protocol.getOrientation()!!

        assertEquals(0.5f, orientation.x / orientation.w, 0.0002f)
        assertEquals(0.25f, orientation.y / orientation.w, 0.0002f)
        assertEquals(0.125f, orientation.z / orientation.w, 0.0002f)
    }

    private fun faceletsPacket(serial: Int): ByteArray = ByteArray(20).apply {
        writeBits(0, 4, 0x04)
        writeBits(4, 8, serial)
    }

    private fun movePacket(serial: Int, moves: List<Pair<Int, Int>>): ByteArray = ByteArray(20).apply {
        writeBits(0, 4, 0x02)
        writeBits(4, 8, serial)
        moves.forEachIndexed { index, (face, direction) ->
            writeBits(12 + index * 5, 4, face)
            writeBits(16 + index * 5, 1, direction)
        }
    }

    private fun ByteArray.writeBits(startBit: Int, bitLength: Int, value: Int) {
        for (offset in 0 until bitLength) {
            val sourceBit = (value shr (bitLength - offset - 1)) and 1
            val bitIndex = startBit + offset
            val byteIndex = bitIndex / 8
            val mask = 1 shl (7 - bitIndex % 8)
            this[byteIndex] = if (sourceBit == 1) {
                (this[byteIndex].toInt() or mask).toByte()
            } else {
                (this[byteIndex].toInt() and mask.inv()).toByte()
            }
        }
    }
}
