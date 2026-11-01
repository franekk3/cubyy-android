package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.VirtualCube
import com.cubyy.timer.timer.CubeRecoverySolver
import cs.min2phase.Tools
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class CubeRecoverySolverTest {

    @Test
    fun alreadyReachedTargetReturnsEmptyRecoveryPath() {
        val target = "R U F2"
        val appliedMoves = target.split(" ").mapNotNull(CubeMove::fromNotation)

        assertEquals(emptyList<CubeMove>(), CubeRecoverySolver().findRecoveryPath(target, appliedMoves))
    }

    @Test
    fun recoveryPathReturnsFromCurrentStateToOriginalTarget() {
        val target = "R U F2 L'"
        val appliedMoves = listOf("F", "B2", "U'").mapNotNull(CubeMove::fromNotation)
        val recovery = CubeRecoverySolver().findRecoveryPath(target, appliedMoves)

        assertNotNull(recovery)
        assertEquals(
            Tools.fromScramble(target),
            Tools.fromScramble((appliedMoves.map(CubeMove::notation) + recovery.orEmpty().map(CubeMove::notation)).joinToString(" "))
        )
        val recoveredCube = VirtualCube().apply {
            appliedMoves.forEach(::applyMove)
            recovery.orEmpty().forEach(::applyMove)
        }
        val targetCube = VirtualCube().apply { applyScramble(target) }

        CubeFace.entries.forEach { face ->
            assertEquals(
                "Mismatch on $face with recovery ${recovery?.joinToString { it.notation }}",
                targetCube.getFace(face).toList(),
                recoveredCube.getFace(face).toList()
            )
        }
    }

    @Test
    fun recoveryPathCanReturnToPreMistakeCheckpoint() {
        val checkpoint = listOf("R", "U'").mapNotNull(CubeMove::fromNotation)
        val current = checkpoint + listOf("F2", "L", "D'").mapNotNull(CubeMove::fromNotation)
        val recovery = CubeRecoverySolver().findPathToMoveState(checkpoint, current)

        assertNotNull(recovery)
        val restored = VirtualCube().apply {
            current.forEach(::applyMove)
            recovery.orEmpty().forEach(::applyMove)
        }
        val checkpointCube = VirtualCube().apply {
            checkpoint.forEach(::applyMove)
        }
        CubeFace.entries.forEach { face ->
            assertEquals(checkpointCube.getFace(face).toList(), restored.getFace(face).toList())
        }
    }
}
