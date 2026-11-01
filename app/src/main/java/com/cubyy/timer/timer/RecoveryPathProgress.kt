package com.cubyy.timer.timer

import com.cubyy.timer.smartcube.model.CubeMove

internal class RecoveryPathProgress(private val path: List<CubeMove>) {
    private var index = 0

    val isComplete: Boolean
        get() = index >= path.size

    fun consume(move: CubeMove): RecoveryMoveResult {
        val expected = path.getOrNull(index) ?: return RecoveryMoveResult.DEVIATED
        if (expected.face != move.face || normalize(expected.amount) != normalize(move.amount)) {
            return RecoveryMoveResult.DEVIATED
        }
        index++
        return if (index == path.size) RecoveryMoveResult.COMPLETE else RecoveryMoveResult.ADVANCED
    }

    private fun normalize(amount: Int): Int = when (amount) {
        -1, 3 -> 3
        2 -> 2
        else -> 1
    }
}

internal enum class RecoveryMoveResult {
    ADVANCED,
    COMPLETE,
    DEVIATED
}
