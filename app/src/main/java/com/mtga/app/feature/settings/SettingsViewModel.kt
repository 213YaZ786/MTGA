package com.mtga.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.mtga.app.data.settings.Settings
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.sync.SyncWorker
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val store: SettingsStore,
    private val context: Context
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

    fun setXcomDirect(enabled: Boolean) = store.update { it.copy(useXcomDirect = enabled) }

    fun setBackgroundSync(enabled: Boolean) {
        store.update { it.copy(backgroundSync = enabled) }
        applySchedule()
    }

    fun setInterval(minutes: Int) {
        store.update { it.copy(syncIntervalMinutes = minutes) }
        applySchedule()
    }

    fun setWifiOnly(enabled: Boolean) {
        store.update { it.copy(syncOnWifiOnly = enabled) }
        applySchedule()
    }

    private fun applySchedule() {
        val current = store.current
        if (current.backgroundSync) {
            SyncWorker.schedule(context, current.syncIntervalMinutes, current.syncOnWifiOnly)
        } else {
            SyncWorker.cancel(context)
        }
    }
}
