package com.mtga.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.settings.Settings
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.settings.ThemeMode
import com.mtga.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val cache: FeedCache,
    private val context: Context
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

    private val _storageBytes = MutableStateFlow<Long?>(null)
    val storageBytes: StateFlow<Long?> = _storageBytes.asStateFlow()

    init {
        measureStorage()
    }

    fun setTheme(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }

    fun setPureBlack(enabled: Boolean) = store.update { it.copy(pureBlack = enabled) }

    fun setShowCounts(enabled: Boolean) = store.update { it.copy(showCounts = enabled) }

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

    /** Deletes saved posts only. Followed accounts and settings stay. */
    fun clearSavedPosts() {
        viewModelScope.launch {
            cache.clear()
            measureStorage()
        }
    }

    fun measureStorage() {
        viewModelScope.launch { _storageBytes.value = cache.sizeBytes() }
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
