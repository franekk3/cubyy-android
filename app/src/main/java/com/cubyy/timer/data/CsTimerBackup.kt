package com.cubyy.timer.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CsTimerSessionBackup(
    val name: String,
    val solves: List<Solve>
)

internal data class CsTimerBackupImport(
    val sessions: List<CsTimerSessionBackup>
)

internal data class CsTimerMergePlan(
    val sessionNames: List<String>,
    val solves: List<Solve>,
    val addedSessions: Int,
    val addedSolves: Int,
    val skippedDuplicates: Int
)

internal object CsTimerBackup {
    fun export(sessions: List<String>, solves: List<Solve>): String {
        val root = JSONObject()
        val sessionData = JSONObject()
        sessions.forEachIndexed { index, name ->
            val sessionId = index + 1
            val sessionSolves = solves.filter { it.sessionName == name }
            root.put("session$sessionId", JSONArray().apply {
                sessionSolves.forEach { solve ->
                    val penalty = when (solve.penalty) {
                        Penalty.NONE -> 0
                        Penalty.PLUS_TWO -> 2000
                        Penalty.DNF -> -1
                    }
                    put(JSONArray().put(JSONArray().put(penalty).put(solve.timeMillis))
                        .put(solve.scramble)
                        .put("")
                        .put(solve.timestamp / 1000L))
                }
            })
            sessionData.put(sessionId.toString(), JSONObject().put("name", name).put("opt", JSONObject().put("scrType", "333").put("phases", 1)))
        }
        root.put("properties", JSONObject().put("sessionData", sessionData.toString()))
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

        keys.forEach { (key, sessionId) ->
            val rawArray = root.opt(key)
            val entries = when (rawArray) {
                is JSONArray -> rawArray
                is String -> JSONArray(rawArray)
                else -> return@forEach
            }
            val metadata = sessionData.optJSONObject(sessionId.toString())
            val sessionName = metadata?.optString("name")?.takeIf { it.isNotBlank() } ?: "Session $sessionId"
            val importedSolves = mutableListOf<Solve>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONArray(index) ?: continue
                val timeArray = entry.optJSONArray(0) ?: continue
                if (timeArray.length() < 2) continue
                val penaltyCode = timeArray.optDouble(0, Double.NaN)
                val timeMillis = timeArray.optDouble(1, Double.NaN)
                if (!penaltyCode.isFinite() || !timeMillis.isFinite()) continue
                val scramble = entry.optString(1, "")
                val timestampSeconds = entry.optDouble(3, Double.NaN)
                if (!timestampSeconds.isFinite()) continue
                val penalty = when (penaltyCode.toInt()) {
                    -1 -> Penalty.DNF
                    2000 -> Penalty.PLUS_TWO
                    else -> Penalty.NONE
                }
                importedSolves += Solve(
                    id = UUID.randomUUID().toString(),
                    timestamp = timestampSeconds.toLong() * 1000L,
                    timeMillis = timeMillis.toLong(),
                    scramble = scramble,
                    penalty = penalty,
                    sessionName = sessionName
                )
            }
            result += CsTimerSessionBackup(sessionName, importedSolves)
        }
        require(result.isNotEmpty()) { "No csTimer sessions were found in this file." }
        return CsTimerBackupImport(result)
    }

    fun planMerge(
        backup: CsTimerBackupImport,
        existingSessions: List<String>,
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
            val name = if (mergeMatchingSessions || incoming.name !in sessions) {
                incoming.name
            } else {
                uniqueImportedSessionName(incoming.name, sessions)
            }
            if (name !in sessions) {
                sessions += name
                addedSessions++
            }
            incoming.solves.forEach { solve ->
                val destination = solve.copy(sessionName = name)
                if (!duplicateKeys.add(destination.duplicateKey())) {
                    skippedDuplicates++
                } else {
                    solves += destination
                    addedSolves++
                }
            }
        }
        return CsTimerMergePlan(sessions, solves, addedSessions, addedSolves, skippedDuplicates)
    }

    private data class DuplicateKey(val session: String, val timestampSeconds: Long, val timeMillis: Long, val scramble: String)

    private fun Solve.duplicateKey() = DuplicateKey(sessionName, timestamp / 1000L, timeMillis, scramble)

    private fun uniqueImportedSessionName(name: String, existing: List<String>): String {
        var suffix = 1
        var candidate = "$name (imported)"
        while (candidate in existing) {
            suffix++
            candidate = "$name (imported $suffix)"
        }
        return candidate
    }
}
