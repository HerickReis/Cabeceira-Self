package com.cabeceira.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReadingSettings(
    /** Ritmo usado enquanto o app ainda não tem histórico suficiente de sessões. */
    val defaultPagesPerHour: Int = 30,
    /** Quanto tempo por dia você pretende ler. */
    val dailyMinutes: Int = 30,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reading_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        ReadingSettings(
            defaultPagesPerHour = prefs.getInt(KEY_PPH, 30),
            dailyMinutes = prefs.getInt(KEY_DAILY, 30),
        )
    )
    val settings: StateFlow<ReadingSettings> = _settings.asStateFlow()

    fun update(settings: ReadingSettings) {
        val sanitized = settings.copy(
            defaultPagesPerHour = settings.defaultPagesPerHour.coerceIn(1, 500),
            dailyMinutes = settings.dailyMinutes.coerceIn(1, 24 * 60),
        )
        prefs.edit()
            .putInt(KEY_PPH, sanitized.defaultPagesPerHour)
            .putInt(KEY_DAILY, sanitized.dailyMinutes)
            .apply()
        _settings.value = sanitized
    }

    private companion object {
        const val KEY_PPH = "default_pages_per_hour"
        const val KEY_DAILY = "daily_minutes"
    }
}
