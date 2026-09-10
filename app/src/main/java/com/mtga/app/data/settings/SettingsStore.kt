package com.mtga.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    /** Read the newest posts straight from x.com. Accurate, but X sees you. */
    val useXcomDirect: Boolean = true,
    /** Poll followed accounts in the background so history accumulates. */
    val backgroundSync: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val syncOnWifiOnly: Boolean = true
)

/**
 * Small preference file, same plain JSON approach as the rest of MTGA's
 * storage. Nothing here is a secret, and none of it leaves the device.
 */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching { file.writeText(json.encodeToString(updated)) }
    }
}
