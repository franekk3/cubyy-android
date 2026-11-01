package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.VirtualCube
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCubeTest {

    @Test
    fun initialCubeIsSolved() {
        val cube = VirtualCube()
        assertTrue("Fresh virtual cube should be solved", cube.isSolved())
    }

    @Test
    fun singleMoveMakesCubeUnsolved() {
        val cube = VirtualCube()
        cube.applyMove(CubeMove(CubeFace.U, 1))
        assertFalse("Cube after 1 move should not be solved", cube.isSolved())
    }

    @Test
    fun fourIdenticalMovesRestoreCube() {
        val cube = VirtualCube()
        repeat(4) {
            cube.applyMove(CubeMove(CubeFace.R, 1))
        }
        assertTrue("4x R moves should restore cube to solved", cube.isSolved())
    }

    @Test
    fun sexyMoveSixTimesRestoresCube() {
        val cube = VirtualCube()
        // (R U R' U') * 6
        repeat(6) {
            cube.applyMove(CubeMove(CubeFace.R, 1))
            cube.applyMove(CubeMove(CubeFace.U, 1))
            cube.applyMove(CubeMove(CubeFace.R, -1))
            cube.applyMove(CubeMove(CubeFace.U, -1))
        }
        assertTrue("(R U R' U') x 6 should restore cube to solved", cube.isSolved())
    }

    @Test
    fun applyScrambleChangesState() {
        val cube = VirtualCube()
        val scramble = "R U R' U' F' U F"
        cube.applyScramble(scramble)
        assertFalse(cube.isSolved())

        cube.resetToSolved()
        assertTrue(cube.isSolved())
    }
}

