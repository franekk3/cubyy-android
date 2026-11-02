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
    val cubeAppearance: String = "CLASSIC",
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val colorTheme: String = "DYNAMIC",
    val hideTimerDuringSolve: Boolean = false
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
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_HIDE_TIMER = "hide_timer_during_solve"
        private const val KEY_CUBE_SCALE = "cube_scale"
        private const val KEY_CUBE_APPEARANCE = "cube_appearance"
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
            holdToStartMillis = prefs.getLong(KEY_HOLD_MILLIS, 300L).coerceIn(150L, 750L),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
            smartCubeAutoStart = prefs.getBoolean(KEY_SMART_START, true),
            smartCubeAutoStop = prefs.getBoolean(KEY_SMART_STOP, true),
            cubeScale = prefs.getFloat(KEY_CUBE_SCALE, 1f).coerceIn(0.7f, 1.3f),
            cubeAppearance = prefs.getString(KEY_CUBE_APPEARANCE, "CLASSIC")
                ?.takeIf { it == "CLASSIC" || it == "STICKERLESS" } ?: "CLASSIC",
            scrambleFontScale = prefs.getFloat(KEY_SCRAMBLE_FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            timerFontScale = prefs.getFloat(KEY_TIMER_FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            themeMode = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM",
            colorTheme = prefs.getString(KEY_COLOR_THEME, "DYNAMIC") ?: "DYNAMIC",
            hideTimerDuringSolve = prefs.getBoolean(KEY_HIDE_TIMER, false)
        )
    }

    fun updateInspection(enabled: Boolean) {
        _settings.value = _settings.value.copy(inspectionEnabled = enabled)
        prefs.edit().putBoolean(KEY_INSPECTION_ENABLED, enabled).apply()
    }

    fun updateHoldMillis(millis: Long) {
        val value = millis.coerceIn(150L, 750L)
        _settings.value = _settings.value.copy(holdToStartMillis = value)
        prefs.edit().putLong(KEY_HOLD_MILLIS, value).apply()
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

    fun updateColorTheme(theme: String) {
        val value = theme.takeIf { it in setOf("DYNAMIC", "BLUE", "GREEN", "YELLOW", "RED") } ?: "DYNAMIC"
        _settings.value = _settings.value.copy(colorTheme = value)
        prefs.edit().putString(KEY_COLOR_THEME, value).apply()
    }

    fun updateHideTimerDuringSolve(hidden: Boolean) {
        _settings.value = _settings.value.copy(hideTimerDuringSolve = hidden)
        prefs.edit().putBoolean(KEY_HIDE_TIMER, hidden).apply()
    }

    fun updateCubeScale(scale: Float) {
        val value = scale.coerceIn(0.7f, 1.3f)
        _settings.value = _settings.value.copy(cubeScale = value)
        prefs.edit().putFloat(KEY_CUBE_SCALE, value).apply()
    }

    fun updateCubeAppearance(appearance: String) {
        val value = appearance.takeIf { it == "CLASSIC" || it == "STICKERLESS" } ?: "CLASSIC"
        _settings.value = _settings.value.copy(cubeAppearance = value)
        prefs.edit().putString(KEY_CUBE_APPEARANCE, value).apply()
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

