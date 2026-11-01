package com.cubyy.timer.timer

import com.cubyy.timer.smartcube.model.CubeMove
import cs.min2phase.Search
import cs.min2phase.Tools

internal class CubeRecoverySolver {
    fun findRecoveryPath(targetScramble: String, appliedMoves: List<CubeMove>): List<CubeMove>? {
        val targetMoves = targetScramble.trim().split("\\s+".toRegex())
            .mapNotNull(CubeMove::fromNotation)
        return findPathToMoveState(targetMoves, appliedMoves)
    }

    fun findPathToMoveState(checkpointMoves: List<CubeMove>, appliedMoves: List<CubeMove>): List<CubeMove>? {
        val inverseCheckpoint = checkpointMoves.asReversed().map { it.inverse().notation }
        val relativeStateScramble = (inverseCheckpoint + appliedMoves.map(CubeMove::notation)).joinToString(" ")
        val facelets = Tools.fromScramble(relativeStateScramble)
        val result = Search().solution(
            facelets,
            21,
            100_000,
            0,
            0
        )
        if (result.startsWith("Error")) return null

        return result.trim().split("\\s+".toRegex()).filter(String::isNotBlank)
            .mapNotNull(CubeMove::fromNotation)
    }
}
