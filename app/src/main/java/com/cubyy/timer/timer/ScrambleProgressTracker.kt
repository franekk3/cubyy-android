package com.cubyy.timer.timer

import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeFace

enum class ScrambleMoveProgress {
    PENDING,
    PARTIAL,
    CORRECT,
    WRONG
}

internal class ScrambleProgressTracker(scramble: String) {
    private val tokens = scramble.trim().split("\\s+".toRegex()).filter(String::isNotBlank)
    private val progress = MutableList(tokens.size) { ScrambleMoveProgress.PENDING }
    private var halfTurnDirection: Int? = null
    private val mistakeStack = mutableListOf<CubeMove>()

    fun isComplete(): Boolean = progress.all { it == ScrambleMoveProgress.CORRECT }

    fun onMove(move: CubeMove): List<ScrambleMoveProgress> {
        val index = progress.indexOfFirst { it != ScrambleMoveProgress.CORRECT }
        if (index == -1) return progress.toList()

        if (mistakeStack.isNotEmpty()) {
            val lastMistake = mistakeStack.last()
            if (move.face == lastMistake.face && move.amount == inverseAmount(lastMistake.amount)) {
                mistakeStack.removeAt(mistakeStack.lastIndex)
                if (mistakeStack.isEmpty()) progress[index] = ScrambleMoveProgress.PENDING
            } else {
                mistakeStack.add(move)
            }
            return progress.toList()
        }

        val token = tokens[index]
        val face = CubeFace.fromSymbol(token.take(1))
        if (face != move.face) {
            registerMistake(index, move)
            return progress.toList()
        }

        if (token.endsWith("2")) {
            if (move.amount == 2) {
                progress[index] = ScrambleMoveProgress.CORRECT
                halfTurnDirection = null
            } else if (progress[index] == ScrambleMoveProgress.PARTIAL) {
                if (halfTurnDirection == move.amount) {
                    progress[index] = ScrambleMoveProgress.CORRECT
                } else {
                    progress[index] = ScrambleMoveProgress.PENDING
                }
                halfTurnDirection = null
            } else if (move.amount == 1 || move.amount == -1) {
                progress[index] = ScrambleMoveProgress.PARTIAL
                halfTurnDirection = move.amount
            } else {
                progress[index] = ScrambleMoveProgress.WRONG
                halfTurnDirection = null
            }
        } else {
            val expectedAmount = if (token.endsWith("'")) -1 else 1
            progress[index] = if (move.amount == expectedAmount) {
                ScrambleMoveProgress.CORRECT
            } else {
                registerMistake(index, move)
                ScrambleMoveProgress.WRONG
            }
            halfTurnDirection = null
        }

        return progress.toList()
    }

    fun currentProgress(): List<ScrambleMoveProgress> = progress.toList()

    fun restoreProgress(snapshot: List<ScrambleMoveProgress>) {
        require(snapshot.size == progress.size)
        snapshot.forEachIndexed { index, state -> progress[index] = state }
        mistakeStack.clear()
        halfTurnDirection = null
    }

    private fun registerMistake(index: Int, move: CubeMove) {
        mistakeStack.add(move)
        progress[index] = ScrambleMoveProgress.WRONG
        halfTurnDirection = null
    }

    private fun inverseAmount(amount: Int): Int = when (amount) {
        1 -> -1
        -1 -> 1
        else -> 2
    }
}
