package com.cubyy.timer

import com.cubyy.timer.data.Penalty
import com.cubyy.timer.data.Solve
import com.cubyy.timer.data.StatisticsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsCalculatorTest {

    @Test
    fun testAo5Calculation() {
        val solves = listOf(
            Solve(timeMillis = 10000, scramble = "test"),
            Solve(timeMillis = 12000, scramble = "test"),
            Solve(timeMillis = 11000, scramble = "test"),
            Solve(timeMillis = 9000, scramble = "test"),  // Best: 9s (discarded)
            Solve(timeMillis = 15000, scramble = "test")  // Worst: 15s (discarded)
        )
        // Remaining 3: 10s, 11s, 12s -> Average = 11s (11000ms)
        val ao5 = StatisticsCalculator.calculateAo5(solves)
        assertNotNull(ao5)
        assertEquals(11000L, ao5)
    }

    @Test
    fun testAo5WithSingleDnf() {
        val solves = listOf(
            Solve(timeMillis = 10000, scramble = "test"),
            Solve(timeMillis = 12000, scramble = "test"),
            Solve(timeMillis = 11000, scramble = "test"),
            Solve(timeMillis = 8000, scramble = "test"),  // Best: 8s (discarded)
            Solve(timeMillis = 14000, scramble = "test", penalty = Penalty.DNF) // DNF (discarded as worst)
        )
        // Remaining 3: 10s, 11s, 12s -> Average = 11s (11000ms)
        val ao5 = StatisticsCalculator.calculateAo5(solves)
        assertEquals(11000L, ao5)
    }

    @Test
    fun testAo5WithTwoDnfsIsDnf() {
        val solves = listOf(
            Solve(timeMillis = 10000, scramble = "test"),
            Solve(timeMillis = 12000, scramble = "test"),
            Solve(timeMillis = 11000, scramble = "test", penalty = Penalty.DNF),
            Solve(timeMillis = 8000, scramble = "test"),
            Solve(timeMillis = 14000, scramble = "test", penalty = Penalty.DNF)
        )
        val ao5 = StatisticsCalculator.calculateAo5(solves)
        assertEquals(Long.MAX_VALUE, ao5)
    }

    @Test
    fun testTpsCalculation() {
        val solve = Solve(
            timeMillis = 10000, // 10.0 seconds
            scramble = "test",
            moveCount = 60      // 60 moves
        )
        assertEquals(6.0, solve.turnsPerSecond!!, 0.001)
    }
}

