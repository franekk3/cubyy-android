package com.cubyy.timer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val inspectionEnabled: Boolean = true,
    val inspectionSeconds: Int = 15,
    val holdToStartMillis: Long = 300L,
    val hapticFeedback: Boolean = true,
    val smartCubeAutoStart: Boolean = true,
    val smartCubeAutoStop: Boolean = true,
    val cubeScale: Float = 1f,
    val scrambleFontScale: Float = 1f,
    val timerFontScale: Float = 1f,
    val themeMode: String = "SYSTEM" // "SYSTEM", "LIGHT", "DARK"
)

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "cubyy_settings"
        private const val KEY_INSPECTION_ENABLED = "inspection_enabled"
        private const val KEY_HOLD_MILLIS = "hold_millis"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_SMART_START = "smart_start"
        private const val KEY_SMART_STOP = "smart_stop"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CUBE_SCALE = "cube_scale"
        private const val KEY_SCRAMBLE_FONT_SCALE = "scramble_font_scale"
        private const val KEY_TIMER_FONT_SCALE = "timer_font_scale"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            inspectionEnabled = prefs.getBoolean(KEY_INSPECTION_ENABLED, true),
            holdToStartMillis = prefs.getLong(KEY_HOLD_MILLIS, 300L),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
            smartCubeAutoStart = prefs.getBoolean(KEY_SMART_START, true),
            smartCubeAutoStop = prefs.getBoolean(KEY_SMART_STOP, true),
            cubeScale = prefs.getFloat(KEY_CUBE_SCALE, 1f).coerceIn(0.7f, 1.3f),
            scrambleFontScale = prefs.getFloat(KEY_SCRAMBLE_FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            timerFontScale = prefs.getFloat(KEY_TIMER_FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            themeMode = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        )
    }

    fun updateInspection(enabled: Boolean) {
        _settings.value = _settings.value.copy(inspectionEnabled = enabled)
        prefs.edit().putBoolean(KEY_INSPECTION_ENABLED, enabled).apply()
    }

    fun updateHoldMillis(millis: Long) {
        _settings.value = _settings.value.copy(holdToStartMillis = millis)
        prefs.edit().putLong(KEY_HOLD_MILLIS, millis).apply()
    }

    fun updateHaptic(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticFeedback = enabled)
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    fun updateSmartAutoStart(enabled: Boolean) {
        _settings.value = _settings.value.copy(smartCubeAutoStart = enabled)
        prefs.edit().putBoolean(KEY_SMART_START, enabled).apply()
    }

    fun updateSmartAutoStop(enabled: Boolean) {
        _settings.value = _settings.value.copy(smartCubeAutoStop = enabled)
        prefs.edit().putBoolean(KEY_SMART_STOP, enabled).apply()
    }

    fun updateTheme(theme: String) {
        _settings.value = _settings.value.copy(themeMode = theme)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun updateCubeScale(scale: Float) {
        val value = scale.coerceIn(0.7f, 1.3f)
        _settings.value = _settings.value.copy(cubeScale = value)
        prefs.edit().putFloat(KEY_CUBE_SCALE, value).apply()
    }

    fun updateScrambleFontScale(scale: Float) {
        val value = scale.coerceIn(0.75f, 1.5f)
        _settings.value = _settings.value.copy(scrambleFontScale = value)
        prefs.edit().putFloat(KEY_SCRAMBLE_FONT_SCALE, value).apply()
    }

    fun updateTimerFontScale(scale: Float) {
        val value = scale.coerceIn(0.75f, 1.5f)
        _settings.value = _settings.value.copy(timerFontScale = value)
        prefs.edit().putFloat(KEY_TIMER_FONT_SCALE, value).apply()
    }
}

