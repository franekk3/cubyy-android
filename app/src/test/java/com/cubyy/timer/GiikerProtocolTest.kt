package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.protocols.GiikerProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiikerProtocolTest {

    @Test
    fun plaintextPacketsEmitOnlyNewMovesFromMoveHistory() {
        val protocol = GiikerProtocol()
        assertTrue(protocol.decodeMovePacket(plaintextPacket(0x11, 0x22, 0x33, 0x41)).isEmpty())

        val moves = protocol.decodeMovePacket(plaintextPacket(0x22, 0x33, 0x41, 0x52))

        assertEquals(listOf("R2"), moves.map { it.notation })
    }

    @Test
    fun encryptedXiaomiPacketsAreDecodedAndDeduplicated() {
        val protocol = GiikerProtocol()
        assertTrue(protocol.decodeMovePacket(encryptedPacket(0x11, 0x22)).isEmpty())

        val moves = protocol.decodeMovePacket(encryptedPacket(0x22, 0x33))

        assertEquals(listOf("L'"), moves.map { it.notation })
    }

    @Test
    fun resetClearsHistoryBaseline() {
        val protocol = GiikerProtocol()
        protocol.decodeMovePacket(plaintextPacket(0x11, 0x22, 0x33, 0x41))
        protocol.reset()

        assertTrue(protocol.decodeMovePacket(plaintextPacket(0x22, 0x33, 0x41, 0x52)).isEmpty())
    }

    private fun plaintextPacket(vararg moves: Int): ByteArray = ByteArray(20).apply {
        moves.forEachIndexed { index, move -> this[16 + index] = move.toByte() }
    }

    private fun encryptedPacket(vararg moves: Int): ByteArray {
        val key = intArrayOf(
            176, 81, 104, 224, 86, 137, 237, 119, 38, 26, 193, 161,
            210, 126, 150, 81, 93, 13, 236, 249, 89, 235, 88, 24,
            113, 81, 214, 131, 130, 199, 2, 169, 39, 165, 171, 41
        )
        val packet = ByteArray(20)
        moves.forEachIndexed { index, move ->
            val byteIndex = 16 + index
            packet[byteIndex] = (move - key[byteIndex] * 2).toByte()
        }
        packet[18] = 0xA7.toByte()
        packet[19] = 0
        return packet
    }
}
