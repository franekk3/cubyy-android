package com.cubyy.timer.data

import java.util.UUID

enum class Penalty {
    NONE,
    PLUS_TWO,
    DNF
}

data class Solve(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val timeMillis: Long,
    val scramble: String,
    val penalty: Penalty = Penalty.NONE,
    val moveCount: Int? = null,
    val reconstruction: String? = null,
    val cubeName: String? = null,
    val sessionName: String = "Default"
) {
    val effectiveTimeMillis: Long
        get() = when (penalty) {
            Penalty.NONE -> timeMillis
            Penalty.PLUS_TWO -> timeMillis + 2000
            Penalty.DNF -> Long.MAX_VALUE
        }

    val turnsPerSecond: Double?
        get() {
            if (moveCount == null || moveCount <= 0 || timeMillis <= 0) return null
            return (moveCount.toDouble() / (timeMillis.toDouble() / 1000.0))
        }

    fun formattedTime(includePenalty: Boolean = true): String {
        return when (penalty) {
            Penalty.DNF -> if (includePenalty) "DNF (${formatMillis(timeMillis)})" else "DNF"
            Penalty.PLUS_TWO -> if (includePenalty) "${formatMillis(timeMillis + 2000)}+" else formatMillis(timeMillis + 2000)
            Penalty.NONE -> formatMillis(timeMillis)
        }
    }

    companion object {
        fun formatMillis(millis: Long): String {
            if (millis == Long.MAX_VALUE) return "DNF"
            val totalSeconds = millis / 1000
            val fractionHundredths = (millis % 1000) / 10
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            return if (minutes > 0) {
                String.format("%d:%02d.%02d", minutes, seconds, fractionHundredths)
            } else {
                String.format("%d.%02d", seconds, fractionHundredths)
            }
        }
    }
}

data class SessionStats(
    val solveCount: Int = 0,
    val bestSingle: Long? = null,
    val worstSingle: Long? = null,
    val currentAo5: Long? = null,
    val bestAo5: Long? = null,
    val currentAo12: Long? = null,
    val bestAo12: Long? = null,
    val sessionMean: Long? = null,
    val averageTps: Double? = null
)

object StatisticsCalculator {

    fun calculateAo3(solves: List<Solve>): Long? = calculateRollingAverage(solves, 3, 1)

    fun calculateAo100(solves: List<Solve>): Long? = calculateRollingAverage(solves, 100, 5)

    fun calculateAo5(solves: List<Solve>): Long? {
        return calculateRollingAverage(solves, 5, 1)
    }

    fun calculateAo12(solves: List<Solve>): Long? {
        return calculateRollingAverage(solves, 12, 1)
    }

    private fun calculateRollingAverage(solves: List<Solve>, count: Int, trimCount: Int): Long? {
        if (solves.size < count) return null
        return calculateTrimmedAverage(solves.takeLast(count), trimCount)
    }

    private fun calculateTrimmedAverage(solves: List<Solve>, trimCount: Int): Long? {
        val dnfCount = solves.count { it.penalty == Penalty.DNF }
        if (dnfCount > trimCount) return Long.MAX_VALUE // DNF

        val times = solves.map { it.effectiveTimeMillis }.sorted()
        // Trim best and worst
        val trimmed = times.subList(trimCount, times.size - trimCount)
        if (trimmed.any { it == Long.MAX_VALUE }) return Long.MAX_VALUE

        return trimmed.average().toLong()
    }

    fun calculateSessionStats(solves: List<Solve>): SessionStats {
        if (solves.isEmpty()) return SessionStats()

        val validSolves = solves.filter { it.penalty != Penalty.DNF }
        val bestSingle = validSolves.minOfOrNull { it.effectiveTimeMillis }
        val worstSingle = validSolves.maxOfOrNull { it.effectiveTimeMillis }
        val sessionMean = if (validSolves.isNotEmpty()) {
            validSolves.map { it.effectiveTimeMillis }.average().toLong()
        } else null

        val currentAo5 = calculateAo5(solves)
        val currentAo12 = calculateAo12(solves)

        // Calculate all rolling Ao5s to find best Ao5
        var bestAo5: Long? = null
        if (solves.size >= 5) {
            for (i in 5..solves.size) {
                val sub = solves.subList(0, i)
                val ao5 = calculateAo5(sub)
                if (ao5 != null && ao5 != Long.MAX_VALUE) {
                    if (bestAo5 == null || ao5 < bestAo5) {
                        bestAo5 = ao5
                    }
                }
            }
        }

        // Calculate all rolling Ao12s to find best Ao12
        var bestAo12: Long? = null
        if (solves.size >= 12) {
            for (i in 12..solves.size) {
                val sub = solves.subList(0, i)
                val ao12 = calculateAo12(sub)
                if (ao12 != null && ao12 != Long.MAX_VALUE) {
                    if (bestAo12 == null || ao12 < bestAo12) {
                        bestAo12 = ao12
                    }
                }
            }
        }

        val solvesWithTps = solves.mapNotNull { it.turnsPerSecond }
        val avgTps = if (solvesWithTps.isNotEmpty()) solvesWithTps.average() else null

        return SessionStats(
            solveCount = solves.size,
            bestSingle = bestSingle,
            worstSingle = worstSingle,
            currentAo5 = currentAo5,
            bestAo5 = bestAo5,
            currentAo12 = currentAo12,
            bestAo12 = bestAo12,
            sessionMean = sessionMean,
            averageTps = avgTps
        )
    }
}

