package com.cubyy.timer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SolveRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "cubyy_solves_pref"
        private const val KEY_SOLVES = "solves_json"
        private const val KEY_SESSIONS = "sessions_list"
        private const val KEY_CURRENT_SESSION = "current_session"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _solves = MutableStateFlow<List<Solve>>(emptyList())
    val solves: StateFlow<List<Solve>> = _solves.asStateFlow()

    private val _sessions = MutableStateFlow<List<String>>(listOf("Default"))
    val sessions: StateFlow<List<String>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow("Default")
    val currentSession: StateFlow<String> = _currentSession.asStateFlow()

    init {
        loadSessions()
        loadSolves()
    }

    private fun loadSessions() {
        val raw = prefs.getString(KEY_SESSIONS, null)
        val saved = mutableListOf<String>()
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val name = arr.optString(i).trim()
                if (name.isNotEmpty() && name != "Default" && name !in saved) saved.add(name)
            }
        }
        val normalized = listOf("Default") + saved.filterNot { it == "3x3" || it == "Smart Cube" }
        _sessions.value = normalized
        prefs.edit().putString(KEY_SESSIONS, JSONArray(normalized).toString()).apply()
        val current = prefs.getString(KEY_CURRENT_SESSION, "Default") ?: "Default"
        _currentSession.value = if (current == "3x3" || current == "Smart Cube" || current !in normalized) "Default" else current
        prefs.edit().putString(KEY_CURRENT_SESSION, _currentSession.value).apply()
    }

    private fun loadSolves() {
        val raw = prefs.getString(KEY_SOLVES, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Solve>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Solve(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        timeMillis = obj.getLong("timeMillis"),
                        scramble = obj.getString("scramble"),
                        penalty = Penalty.valueOf(obj.optString("penalty", "NONE")),
                        moveCount = if (obj.has("moveCount")) obj.getInt("moveCount") else null,
                        reconstruction = if (obj.has("reconstruction") && !obj.isNull("reconstruction")) obj.getString("reconstruction") else null,
                        cubeName = if (obj.has("cubeName") && !obj.isNull("cubeName")) obj.getString("cubeName") else null,
                        sessionName = when (val name = obj.optString("sessionName", "Default")) {
                            "3x3", "Smart Cube", "" -> "Default"
                            else -> name
                        }
                    )
                )
            }
            _solves.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistSolves() {
        val arr = JSONArray()
        for (s in _solves.value) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("timestamp", s.timestamp)
            obj.put("timeMillis", s.timeMillis)
            obj.put("scramble", s.scramble)
            obj.put("penalty", s.penalty.name)
            s.moveCount?.let { obj.put("moveCount", it) }
            s.reconstruction?.let { obj.put("reconstruction", it) }
            s.cubeName?.let { obj.put("cubeName", it) }
            obj.put("sessionName", s.sessionName)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SOLVES, arr.toString()).apply()
    }

    fun addSolve(solve: Solve) {
        val updated = _solves.value.toMutableList()
        updated.add(solve)
        _solves.value = updated
        persistSolves()
    }

    fun updatePenalty(solveId: String, penalty: Penalty) {
        val updated = _solves.value.map {
            if (it.id == solveId) it.copy(penalty = penalty) else it
        }
        _solves.value = updated
        persistSolves()
    }

    fun deleteSolve(solveId: String) {
        val updated = _solves.value.filter { it.id != solveId }
        _solves.value = updated
        persistSolves()
    }

    fun clearCurrentSession() {
        val session = _currentSession.value
        val updated = _solves.value.filter { it.sessionName != session }
        _solves.value = updated
        persistSolves()
    }

    fun switchSession(session: String) {
        if (session !in _sessions.value) return
        _currentSession.value = session
        prefs.edit().putString(KEY_CURRENT_SESSION, session).apply()
    }

    fun addSession(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || normalizedName == "Default") {
            switchSession("Default")
            return
        }
        if (!_sessions.value.contains(normalizedName)) {
            val updated = _sessions.value + normalizedName
            _sessions.value = updated
            val arr = JSONArray(updated)
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
        }
        switchSession(normalizedName)
    }

    fun deleteSession(name: String): Boolean {
        if (name == "Default" || name !in _sessions.value) return false
        _sessions.value = _sessions.value.filterNot { it == name }.ifEmpty { listOf("Default") }
        _solves.value = _solves.value.filterNot { it.sessionName == name }
        prefs.edit()
            .putString(KEY_SESSIONS, JSONArray(_sessions.value).toString())
            .apply()
        if (_currentSession.value == name) switchSession("Default")
        persistSolves()
        return true
    }

    fun exportCsTimerBackup(): String = CsTimerBackup.export(_sessions.value, _solves.value)

    fun importCsTimerBackup(json: String, mergeMatchingSessions: Boolean): CsTimerImportSummary {
        val backup = CsTimerBackup.parse(json)
        val plan = CsTimerBackup.planMerge(backup, _sessions.value, _solves.value, mergeMatchingSessions)
        _sessions.value = plan.sessionNames
        _solves.value = plan.solves
        prefs.edit()
            .putString(KEY_SESSIONS, JSONArray(plan.sessionNames).toString())
            .apply()
        persistSolves()
        return CsTimerImportSummary(plan.addedSessions, plan.addedSolves, plan.skippedDuplicates)
    }
}

data class CsTimerImportSummary(
    val addedSessions: Int,
    val addedSolves: Int,
    val skippedDuplicates: Int
)

