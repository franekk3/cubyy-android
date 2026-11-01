package com.cubyy.timer

import com.cubyy.timer.data.Penalty
import com.cubyy.timer.data.Solve
import com.cubyy.timer.data.CsTimerBackup
import com.cubyy.timer.data.CsTimerBackupImport
import com.cubyy.timer.data.CsTimerSessionBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsTimerBackupTest {

    @Test
    fun roundTripUsesCsTimerSessionAndSolveArrays() {
        val solves = listOf(
            Solve(timestamp = 1_700_000_123_000L, timeMillis = 12_345, scramble = "R U R'", sessionName = "3x3"),
            Solve(timestamp = 1_700_000_456_000L, timeMillis = 23_456, scramble = "F2 D", penalty = Penalty.PLUS_TWO, sessionName = "3x3"),
            Solve(timestamp = 1_700_000_789_000L, timeMillis = 30_000, scramble = "L B", penalty = Penalty.DNF, sessionName = "Evening")
        )

        val json = CsTimerBackup.export(listOf("3x3", "Evening"), solves)
        val imported = CsTimerBackup.parse(json)

        assertEquals(listOf("3x3", "Evening"), imported.sessions.map { it.name })
        assertEquals(2, imported.sessions[0].solves.size)
        assertEquals(Penalty.PLUS_TWO, imported.sessions[0].solves[1].penalty)
        assertEquals(Penalty.DNF, imported.sessions[1].solves.single().penalty)
        assertEquals(solves.map { it.timeMillis }, imported.sessions.flatMap { it.solves }.map { it.timeMillis })
    }

    @Test
    fun parsesCsTimerNativeBackupWithMetadataAsJsonStrings() {
        val backup = """{"session1":"[[[0,12345],\"R U\",\"\",1700000000],[[2000,23456],\"F2\",\"note\",1700000001]]","properties":"{\"sessionData\":\"{\\\"1\\\":{\\\"name\\\":\\\"Practice\\\"}}\"}"}"""

        val imported = CsTimerBackup.parse(backup)

        assertEquals("Practice", imported.sessions.single().name)
        assertEquals(Penalty.NONE, imported.sessions.single().solves[0].penalty)
        assertEquals(12_345L, imported.sessions.single().solves[0].timeMillis)
        assertEquals(Penalty.PLUS_TWO, imported.sessions.single().solves[1].penalty)
        assertEquals(1_700_000_001_000L, imported.sessions.single().solves[1].timestamp)
    }

    @Test
    fun exportsSessionDataAndJsonSerializedSessionArrays() {
        val json = CsTimerBackup.export(listOf("Default"), emptyList())

        assertTrue(json.contains("session1"))
        assertTrue(json.contains("sessionData"))
    }

    @Test
    fun mergeSkipsOnlyDuplicateSolveInMatchingDestinationSession() {
        val solve = Solve(timestamp = 1_700_000_123_000L, timeMillis = 12_345, scramble = "R U", sessionName = "3x3")
        val sameFingerprintDifferentSession = solve.copy(sessionName = "Other")
        val backup = CsTimerBackupImport(listOf(CsTimerSessionBackup("3x3", listOf(solve))))

        val merged = CsTimerBackup.planMerge(
            backup,
            existingSessions = listOf("3x3", "Other"),
            existingSolves = listOf(solve, sameFingerprintDifferentSession),
            mergeMatchingSessions = true
        )

        assertEquals(0, merged.addedSessions)
        assertEquals(0, merged.addedSolves)
        assertEquals(1, merged.skippedDuplicates)
        assertEquals(2, merged.solves.size)
    }

    @Test
    fun declinedSessionMergeCreatesSeparateImportedSession() {
        val solve = Solve(timestamp = 1_700_000_123_000L, timeMillis = 12_345, scramble = "R U", sessionName = "3x3")
        val backup = CsTimerBackupImport(listOf(CsTimerSessionBackup("3x3", listOf(solve))))

        val plan = CsTimerBackup.planMerge(backup, listOf("3x3"), emptyList(), mergeMatchingSessions = false)

        assertEquals(listOf("3x3", "3x3 (imported)"), plan.sessionNames)
        assertEquals("3x3 (imported)", plan.solves.single().sessionName)
    }
}
