package com.cubyy.timer

import com.cubyy.timer.scramble.ScrambleGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrambleGeneratorTest {

    @Test
    fun testGenerateLength() {
        val scramble = ScrambleGenerator.generate3x3(21)
        val moves = scramble.split(" ")
        assertEquals(21, moves.size)
    }

    @Test
    fun testNoConsecutiveSameFaceMoves() {
        repeat(10) {
            val scramble = ScrambleGenerator.generate3x3(25)
            val moves = scramble.split(" ")
            for (i in 0 until moves.size - 1) {
                val face1 = moves[i].take(1)
                val face2 = moves[i + 1].take(1)
                assertNotEquals("Consecutive moves should not be on same face: ${moves[i]} and ${moves[i+1]}", face1, face2)
            }
        }
    }

    @Test
    fun testValidMoveCharacters() {
        val validFaces = setOf('U', 'D', 'L', 'R', 'F', 'B')
        val validSuffixes = setOf('\'', '2')

        val scramble = ScrambleGenerator.generate3x3(21)
        val moves = scramble.split(" ")
        for (m in moves) {
            assertTrue("Move starts with valid face: $m", validFaces.contains(m[0]))
            if (m.length > 1) {
                assertTrue("Move has valid suffix: $m", validSuffixes.contains(m[1]))
            }
        }
    }
}

