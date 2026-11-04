package com.cubyy.timer.data

import android.content.Context
import android.content.SharedPreferences
import com.cubyy.timer.scramble.WcaEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SolveRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "cubyy_solves_pref"
        private const val KEY_SOLVES = "solves_json"
        private const val KEY_SESSIONS = "sessions_list"
        private const val KEY_CURRENT_SESSION = "current_session"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _solves = MutableStateFlow<List<Solve>>(emptyList())
    val solves: StateFlow<List<Solve>> = _solves.asStateFlow()
    private val _sessions = MutableStateFlow<List<PuzzleSession>>(emptyList())
    val sessions: StateFlow<List<PuzzleSession>> = _sessions.asStateFlow()
    private val _currentSession = MutableStateFlow(PuzzleSession.default("333"))
    val currentSession: StateFlow<PuzzleSession> = _currentSession.asStateFlow()

    init {
        loadSessions()
        loadSolves()
        loadCurrentSession()
    }

    private fun loadSessions() {
        val loaded = mutableListOf<PuzzleSession>()
        val raw = prefs.getString(KEY_SESSIONS, null)
        if (raw != null) {
            val array = runCatching { JSONArray(raw) }.getOrNull()
            if (array != null) for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is JSONObject -> {
                        val name = item.optString("name").trim()
                        val eventId = WcaEvent.fromId(item.optString("eventId")).id
                        if (name.isNotEmpty()) loaded += PuzzleSession(
                            item.optString("id").takeIf(String::isNotBlank) ?: PuzzleSession.create(name, eventId).id,
                            name,
                            eventId
                        )
                    }
                    is String -> {
                        val rawName = item.trim()
                        val name = if (rawName in setOf("3x3", "Smart Cube")) "Default" else rawName
                        if (name.isNotEmpty()) loaded += PuzzleSession.legacy(name)
                    }
                }
            }
        }
        val canonicalized = loaded.map { session ->
            if (session.name == "Default") PuzzleSession.default(session.eventId) else session
        }
        val unique = canonicalized.distinctBy { "${it.eventId}\u0000${it.name}" }.toMutableList()
        WcaEvent.all.forEach { event ->
            if (unique.none { it.eventId == event.id && it.name == "Default" }) {
                unique += PuzzleSession.default(event.id)
            }
        }
        _sessions.value = unique
        persistSessions()
    }

    private fun loadSolves() {
        val raw = prefs.getString(KEY_SOLVES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<Solve>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val eventId = WcaEvent.fromId(obj.optString("eventId", "333")).id
                val storedName = obj.optString("sessionName", "Default").ifBlank { "Default" }
                val sessionName = if (storedName in setOf("3x3", "Smart Cube")) "Default" else storedName
                val session = _sessions.value.firstOrNull { it.eventId == eventId && it.name == sessionName }
                    ?: _sessions.value.firstOrNull { it.id == obj.optString("sessionId") }
                    ?: PuzzleSession.create(sessionName, eventId).also { _sessions.value = _sessions.value + it }
                list += Solve(
                    id = obj.getString("id"),
                    timestamp = obj.getLong("timestamp"),
                    timeMillis = obj.getLong("timeMillis"),
                    scramble = obj.optString("scramble", ""),
                    penalty = Penalty.valueOf(obj.optString("penalty", "NONE")),
                    moveCount = if (obj.has("moveCount")) obj.optInt("moveCount") else null,
                    reconstruction = if (obj.has("reconstruction") && !obj.isNull("reconstruction")) obj.getString("reconstruction") else null,
                    cubeName = if (obj.has("cubeName") && !obj.isNull("cubeName")) obj.getString("cubeName") else null,
                    sessionName = session.name,
                    sessionId = session.id,
                    eventId = session.eventId,
                    multiBlindAttempted = if (obj.has("multiBlindAttempted")) obj.optInt("multiBlindAttempted") else null,
                    multiBlindSolved = if (obj.has("multiBlindSolved")) obj.optInt("multiBlindSolved") else null
                )
            }
            _solves.value = list
            persistSessions()
            persistSolves()
        }
    }

    private fun loadCurrentSession() {
        val saved = prefs.getString(KEY_CURRENT_SESSION, "Default") ?: "Default"
        _currentSession.value = _sessions.value.firstOrNull { it.id == saved }
            ?: _sessions.value.firstOrNull { it.eventId == "333" && it.name == saved }
            ?: _sessions.value.first { it.id == "default:333" }
        prefs.edit().putString(KEY_CURRENT_SESSION, _currentSession.value.id).apply()
    }

    private fun persistSessions() {
        val array = JSONArray()
        _sessions.value.forEach { session ->
            array.put(JSONObject().put("id", session.id).put("name", session.name).put("eventId", session.eventId))
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun persistSolves() {
        val arr = JSONArray()
        for (solve in _solves.value) {
            val obj = JSONObject()
                .put("id", solve.id)
                .put("timestamp", solve.timestamp)
                .put("timeMillis", solve.timeMillis)
                .put("scramble", solve.scramble)
                .put("penalty", solve.penalty.name)
                .put("sessionName", solve.sessionName)
                .put("sessionId", solve.sessionId)
                .put("eventId", solve.eventId)
            solve.multiBlindAttempted?.let { obj.put("multiBlindAttempted", it) }
            solve.multiBlindSolved?.let { obj.put("multiBlindSolved", it) }
            solve.moveCount?.let { obj.put("moveCount", it) }
            solve.reconstruction?.let { obj.put("reconstruction", it) }
            solve.cubeName?.let { obj.put("cubeName", it) }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SOLVES, arr.toString()).apply()
    }

    fun addSolve(solve: Solve) {
        val session = _currentSession.value
        val updated = solve.copy(sessionName = session.name, sessionId = session.id, eventId = session.eventId)
        _solves.value = _solves.value + updated
        persistSolves()
    }

    fun updatePenalty(solveId: String, penalty: Penalty) {
        _solves.value = _solves.value.map { if (it.id == solveId) it.copy(penalty = penalty) else it }
        persistSolves()
    }

    fun deleteSolve(solveId: String) {
        _solves.value = _solves.value.filterNot { it.id == solveId }
        persistSolves()
    }

    fun clearCurrentSession() {
        val id = _currentSession.value.id
        _solves.value = _solves.value.filterNot { it.sessionId == id }
        persistSolves()
    }

    fun switchSession(session: PuzzleSession) {
        val match = _sessions.value.firstOrNull { it.id == session.id } ?: return
        _currentSession.value = match
        prefs.edit().putString(KEY_CURRENT_SESSION, match.id).apply()
    }

    fun switchToDefaultSession(eventId: String) {
        _sessions.value.firstOrNull { it.eventId == eventId && it.name == "Default" }?.let(::switchSession)
    }

    fun addSession(name: String, eventId: String = _currentSession.value.eventId): PuzzleSession {
        val normalizedName = name.trim().ifBlank { "Default" }
        val event = WcaEvent.fromId(eventId)
        val existing = _sessions.value.firstOrNull { it.eventId == event.id && it.name == normalizedName }
        val session = existing ?: PuzzleSession.create(normalizedName, event.id).also {
            _sessions.value = _sessions.value + it
            persistSessions()
        }
        switchSession(session)
        return session
    }

    fun deleteSession(sessionId: String): Boolean {
        val target = _sessions.value.firstOrNull { it.id == sessionId } ?: return false
        if (target.name == "Default") return false
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        _solves.value = _solves.value.filterNot { it.sessionId == sessionId }
        persistSessions()
        persistSolves()
        if (_currentSession.value.id == sessionId) switchToDefaultSession(target.eventId)
        return true
    }

    fun exportCsTimerBackup(sessionIds: Set<String>): String = CsTimerBackup.export(
        _sessions.value.filter { it.id in sessionIds },
        _solves.value
    )

    fun importCsTimerBackup(json: String, mergeMatchingSessions: Boolean): CsTimerImportSummary {
        val backup = CsTimerBackup.parse(json)
        val plan = CsTimerBackup.planMerge(backup, _sessions.value, _solves.value, mergeMatchingSessions)
        _sessions.value = plan.sessions
        _solves.value = plan.solves
        persistSessions()
        persistSolves()
        return CsTimerImportSummary(plan.addedSessions, plan.addedSolves, plan.skippedDuplicates)
    }
}

data class CsTimerImportSummary(
    val addedSessions: Int,
    val addedSolves: Int,
    val skippedDuplicates: Int
)
