package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.timer.RecoveryMoveResult
import com.cubyy.timer.timer.RecoveryPathProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPathProgressTest {

    @Test
    fun routeCompletesOnlyAfterEveryMoveInOrder() {
        val progress = RecoveryPathProgress(listOf(CubeMove(CubeFace.R, 1), CubeMove(CubeFace.U, -1)))

        assertEquals(RecoveryMoveResult.ADVANCED, progress.consume(CubeMove(CubeFace.R, 1)))
        assertEquals(RecoveryMoveResult.COMPLETE, progress.consume(CubeMove(CubeFace.U, -1)))
        assertEquals(true, progress.isComplete)
    }

    @Test
    fun deviatedRouteDoesNotClaimCheckpointRestored() {
        val progress = RecoveryPathProgress(listOf(CubeMove(CubeFace.R, 1), CubeMove(CubeFace.U, -1)))

        assertEquals(RecoveryMoveResult.DEVIATED, progress.consume(CubeMove(CubeFace.R, -1)))
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun doubleTurnIsEquivalentRegardlessOfDirectionEncoding() {
        val progress = RecoveryPathProgress(listOf(CubeMove(CubeFace.F, 2)))

        assertEquals(RecoveryMoveResult.COMPLETE, progress.consume(CubeMove(CubeFace.F, 2)))
    }
}
