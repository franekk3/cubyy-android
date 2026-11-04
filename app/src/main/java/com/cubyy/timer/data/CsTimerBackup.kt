package com.cubyy.timer.data

import com.cubyy.timer.scramble.WcaEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CsTimerSessionBackup(
    val name: String,
    val eventId: String,
    val solves: List<Solve>
)

internal data class CsTimerBackupImport(val sessions: List<CsTimerSessionBackup>)

internal data class CsTimerMergePlan(
    val sessions: List<PuzzleSession>,
    val solves: List<Solve>,
    val addedSessions: Int,
    val addedSolves: Int,
    val skippedDuplicates: Int
)

internal object CsTimerBackup {
    fun export(sessions: List<PuzzleSession>, solves: List<Solve>): String {
        require(sessions.isNotEmpty()) { "Select at least one session to export." }
        val root = JSONObject()
        val sessionData = JSONObject()
        sessions.forEachIndexed { index, session ->
            val sessionId = index + 1
            val sessionSolves = solves.filter { it.sessionId == session.id }
            root.put("session$sessionId", JSONArray().apply {
                sessionSolves.forEach { solve ->
                    val penalty = when (solve.penalty) {
                        Penalty.NONE -> 0
                        Penalty.PLUS_TWO -> 2000
                        Penalty.DNF -> -1
                    }
                    put(JSONArray()
                        .put(JSONArray().put(penalty).put(solve.timeMillis))
                        .put(solve.scramble)
                        .put(if (session.eventId == "333mbf" && solve.multiBlindAttempted != null && solve.multiBlindSolved != null) {
                            "${solve.multiBlindSolved}/${solve.multiBlindAttempted}"
                        } else "")
                        .put(solve.timestamp / 1000L))
                }
            })
            val event = session.event
            val minTime = sessionSolves.minOfOrNull { it.timestamp / 1000L }
            val maxTime = sessionSolves.maxOfOrNull { it.timestamp / 1000L }
            val valid = sessionSolves.filter { it.penalty != Penalty.DNF }
            val mean = if (valid.isEmpty()) -1 else valid.sumOf {
                it.timeMillis + if (it.penalty == Penalty.PLUS_TWO) 2_000L else 0L
            } / valid.size
            sessionData.put(sessionId.toString(), JSONObject()
                .put("name", session.name)
                .put("cubyyEventId", event.id)
                .put("opt", JSONObject().put("scrType", event.csTimerType).put("phases", 1))
                .put("rank", sessionId)
                .put("stat", JSONArray().put(sessionSolves.size)
                    .put(sessionSolves.count { it.penalty == Penalty.DNF }).put(mean))
                .put("date", JSONArray()
                    .put(minTime?.let { it } ?: JSONObject.NULL)
                    .put(maxTime?.let { it } ?: JSONObject.NULL)))
        }
        root.put("properties", JSONObject()
            .put("sessionData", sessionData.toString())
            .put("sessionN", sessions.size)
            .put("session", 1))
        return root.toString(2)
    }

    fun parse(json: String): CsTimerBackupImport {
        val root = JSONObject(json.removePrefix("\uFEFF").trim())
        val properties = root.optJSONObject("properties")
            ?: root.optString("properties").takeIf(String::isNotBlank)?.let(::JSONObject)
            ?: JSONObject()
        val sessionData = properties.optJSONObject("sessionData")
            ?: properties.optString("sessionData").takeIf(String::isNotBlank)?.let(::JSONObject)
            ?: JSONObject()
        val result = mutableListOf<CsTimerSessionBackup>()
        val keys = root.keys().asSequence()
            .mapNotNull { key -> Regex("^session(\\d+)$").matchEntire(key)?.let { key to it.groupValues[1].toInt() } }
            .sortedBy { it.second }
            .toList()

        keys.forEach { (key, sessionNumber) ->
            val entries = when (val raw = root.opt(key)) {
                is JSONArray -> raw
                is String -> runCatching { JSONArray(raw) }.getOrNull() ?: return@forEach
                else -> return@forEach
            }
            val metadata = sessionData.optJSONObject(sessionNumber.toString())
            val name = metadata?.optString("name")?.takeIf { it.isNotBlank() } ?: "Session $sessionNumber"
            val eventType = metadata?.optJSONObject("opt")?.optString("scrType")
            val customEventId = metadata?.optString("cubyyEventId")
            val event = customEventId?.takeIf { id -> WcaEvent.all.any { it.id == id } }
                ?.let(WcaEvent::fromId) ?: WcaEvent.fromCsTimerType(eventType)
            val imported = mutableListOf<Solve>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONArray(index) ?: continue
                val time = entry.optJSONArray(0) ?: continue
                if (time.length() < 2) continue
                val penaltyCode = time.optDouble(0, Double.NaN)
                val millis = time.optDouble(1, Double.NaN)
                if (!penaltyCode.isFinite() || !millis.isFinite() || millis < 0) continue
                val timestamp = entry.optDouble(3, Double.NaN)
                if (!timestamp.isFinite()) continue
                val penalty = when (penaltyCode.toInt()) {
                    -1 -> Penalty.DNF
                    2000 -> Penalty.PLUS_TWO
                    else -> Penalty.NONE
                }
                imported += Solve(
                    id = UUID.randomUUID().toString(),
                    timestamp = timestamp.toLong() * 1000L,
                    timeMillis = millis.toLong(),
                    scramble = entry.optString(1, ""),
                    penalty = penalty,
                    sessionName = name,
                    eventId = event.id,
                    multiBlindAttempted = if (event.id == "333mbf") Regex("(\\d+)\\s*/\\s*(\\d+)").find(entry.optString(2, ""))?.groupValues?.get(2)?.toIntOrNull() else null,
                    multiBlindSolved = if (event.id == "333mbf") Regex("(\\d+)\\s*/\\s*(\\d+)").find(entry.optString(2, ""))?.groupValues?.get(1)?.toIntOrNull() else null
                )
            }
            // Keep empty sessions too: csTimer backups can contain session metadata
            // even when no solve has been recorded in that session yet.
            result += CsTimerSessionBackup(name, event.id, imported)
        }
        require(result.isNotEmpty()) { "No csTimer sessions were found in this file." }
        return CsTimerBackupImport(result)
    }

    fun planMerge(
        backup: CsTimerBackupImport,
        existingSessions: List<PuzzleSession>,
        existingSolves: List<Solve>,
        mergeMatchingSessions: Boolean
    ): CsTimerMergePlan {
        val sessions = existingSessions.toMutableList()
        val solves = existingSolves.toMutableList()
        val duplicateKeys = solves.mapTo(mutableSetOf()) { it.duplicateKey() }
        var addedSessions = 0
        var addedSolves = 0
        var skippedDuplicates = 0

        backup.sessions.forEach { incoming ->
            val matching = sessions.firstOrNull { it.eventId == incoming.eventId && it.name == incoming.name }
            val target = when {
                matching != null && mergeMatchingSessions -> matching
                matching != null -> uniqueImportedSession(incoming.name, incoming.eventId, sessions).also { sessions += it; addedSessions++ }
                else -> PuzzleSession.create(incoming.name, incoming.eventId).also { sessions += it; addedSessions++ }
            }
            incoming.solves.forEach { solve ->
                val destination = solve.copy(sessionName = target.name, sessionId = target.id, eventId = target.eventId)
                if (!duplicateKeys.add(destination.duplicateKey())) skippedDuplicates++
                else { solves += destination; addedSolves++ }
            }
        }
        return CsTimerMergePlan(sessions, solves, addedSessions, addedSolves, skippedDuplicates)
    }

    private data class DuplicateKey(val sessionId: String, val timestampSeconds: Long, val timeMillis: Long, val scramble: String)
    private fun Solve.duplicateKey() = DuplicateKey(sessionId, timestamp / 1000L, timeMillis, scramble)

    private fun uniqueImportedSession(name: String, eventId: String, existing: List<PuzzleSession>): PuzzleSession {
        val names = existing.filter { it.eventId == eventId }.mapTo(mutableSetOf()) { it.name }
        var suffix = 1
        var candidate = "$name (imported)"
        while (candidate in names) { suffix++; candidate = "$name (imported $suffix)" }
        return PuzzleSession.create(candidate, eventId)
    }
}
