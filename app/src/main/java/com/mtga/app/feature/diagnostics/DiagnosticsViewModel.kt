package com.mtga.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.data.instances.InstanceHealth
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.NitterInstance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstanceRow(
    val instance: NitterInstance,
    val health: InstanceHealth?
)

data class DiagnosticsUiState(
    val rows: List<InstanceRow> = emptyList(),
    val probing: Boolean = false
) {
    val anyHealthy: Boolean
        get() = rows.any { it.instance.enabled && it.health?.lastError == null && it.health?.lastCheckedAt != null }

    val allChecked: Boolean
        get() = rows.filter { it.instance.enabled }.all { it.health?.lastCheckedAt != null }
}

class DiagnosticsViewModel(private val pool: InstancePool) : ViewModel() {

    val state: StateFlow<DiagnosticsUiState> =
        combine(pool.instances, pool.health, pool.probing) { instances, health, probing ->
            DiagnosticsUiState(
                rows = instances.map { InstanceRow(it, health[it.id]) },
                probing = probing
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DiagnosticsUiState()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { pool.probeAll() }
    }

    fun setEnabled(id: String, enabled: Boolean) = pool.setEnabled(id, enabled)
    fun move(id: String, delta: Int) = pool.move(id, delta)
    fun remove(id: String) = pool.remove(id)
    fun addCustom(url: String, rssUrl: String?): Boolean = pool.addCustom(url, rssUrl)
    fun restoreDefaults() = pool.restoreDefaults()
    fun report(): String = pool.diagnosticReport()
}
