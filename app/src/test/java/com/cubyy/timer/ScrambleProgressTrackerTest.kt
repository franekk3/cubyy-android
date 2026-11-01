package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.timer.ScrambleMoveProgress
import com.cubyy.timer.timer.ScrambleProgressTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrambleProgressTrackerTest {

    @Test
    fun correctAndIncorrectMovesAreMarkedPerScrambleToken() {
        val tracker = ScrambleProgressTracker("R U'")

        val afterWrong = tracker.onMove(CubeMove(CubeFace.F, 1))
        assertEquals(ScrambleMoveProgress.WRONG, afterWrong[0])

        val stillWrongUntilUndone = tracker.onMove(CubeMove(CubeFace.R, 1))
        assertEquals(ScrambleMoveProgress.WRONG, stillWrongUntilUndone[0])

        assertEquals(ScrambleMoveProgress.WRONG, tracker.onMove(CubeMove(CubeFace.R, -1))[0])
        val corrected = tracker.onMove(CubeMove(CubeFace.F, -1))
        assertEquals(ScrambleMoveProgress.PENDING, corrected[0])

        val afterRight = tracker.onMove(CubeMove(CubeFace.R, 1))
        assertEquals(ScrambleMoveProgress.CORRECT, afterRight[0])
        assertEquals(ScrambleMoveProgress.PENDING, afterRight[1])

        val finished = tracker.onMove(CubeMove(CubeFace.U, -1))
        assertEquals(listOf(ScrambleMoveProgress.CORRECT, ScrambleMoveProgress.CORRECT), finished)
    }

    @Test
    fun halfTurnShowsPartialThenCompletesOnSecondQuarterTurn() {
        val tracker = ScrambleProgressTracker("R2")

        assertEquals(ScrambleMoveProgress.PARTIAL, tracker.onMove(CubeMove(CubeFace.R, 1)).single())
        assertEquals(ScrambleMoveProgress.CORRECT, tracker.onMove(CubeMove(CubeFace.R, 1)).single())
    }

    @Test
    fun wrongDirectionMustBeUndoneBeforeCorrectTurnCounts() {
        val tracker = ScrambleProgressTracker("L")

        assertEquals(ScrambleMoveProgress.WRONG, tracker.onMove(CubeMove(CubeFace.L, -1)).single())
        assertEquals(ScrambleMoveProgress.PENDING, tracker.onMove(CubeMove(CubeFace.L, 1)).single())
        assertEquals(ScrambleMoveProgress.CORRECT, tracker.onMove(CubeMove(CubeFace.L, 1)).single())
    }

    @Test
    fun halfTurnRejectsOppositeDirectionMidway() {
        val tracker = ScrambleProgressTracker("R2")
        tracker.onMove(CubeMove(CubeFace.R, 1))

        assertEquals(ScrambleMoveProgress.PENDING, tracker.onMove(CubeMove(CubeFace.R, -1)).single())
    }

    @Test
    fun trackerCompletesOnlyAfterEveryScrambleMoveMatches() {
        val tracker = ScrambleProgressTracker("R U")

        assertEquals(false, tracker.isComplete())
        tracker.onMove(CubeMove(CubeFace.R, 1))
        assertEquals(false, tracker.isComplete())
        tracker.onMove(CubeMove(CubeFace.U, 1))
        assertEquals(true, tracker.isComplete())
    }

    @Test
    fun freshlyGeneratedScrambleStartsWithNoCompletedTokens() {
        val tracker = ScrambleProgressTracker("R U F2")

        assertEquals(
            listOf(
                ScrambleMoveProgress.PENDING,
                ScrambleMoveProgress.PENDING,
                ScrambleMoveProgress.PENDING
            ),
            tracker.currentProgress()
        )
        assertEquals(false, tracker.isComplete())
    }

    @Test
    fun restoringPreMistakeProgressKeepsEarlierCorrectTokens() {
        val tracker = ScrambleProgressTracker("R U F")
        val beforeMistake = tracker.onMove(CubeMove(CubeFace.R, 1))
        tracker.onMove(CubeMove(CubeFace.B, 1))

        tracker.restoreProgress(beforeMistake)

        assertEquals(
            listOf(ScrambleMoveProgress.CORRECT, ScrambleMoveProgress.PENDING, ScrambleMoveProgress.PENDING),
            tracker.currentProgress()
        )
    }
}
