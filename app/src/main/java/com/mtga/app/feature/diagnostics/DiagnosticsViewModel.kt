package com.mtga.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.data.instances.InstanceHealth
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.NitterInstance
import kotlinx.coroutines.flow.MutableStateFlow
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
    val probing: Boolean = false,
    /** When the running check started, in ms; a row counts as checked once its last check is at or after it. */
    val probeStartedAt: Long = 0L
) {
    /** The servers a check reads: the enabled ones. */
    val enabledCount: Int
        get() = rows.count { it.instance.enabled }

    /** How many of them this check has reached so far. */
    val checkedCount: Int
        get() = rows.count { it.instance.enabled && (it.health?.lastCheckedAt ?: 0L) >= probeStartedAt }

    val anyHealthy: Boolean
        get() = rows.any { it.instance.enabled && it.health?.lastError == null && it.health?.lastCheckedAt != null }

    val allChecked: Boolean
        get() = rows.filter { it.instance.enabled }.all { it.health?.lastCheckedAt != null }
}

class DiagnosticsViewModel(
    private val pool: InstancePool
) : ViewModel() {

    // The start of the check, kept each time the pool begins one (here or from anywhere else).
    private val probeStartedAt = MutableStateFlow(0L)

    val state: StateFlow<DiagnosticsUiState> =
        combine(pool.instances, pool.health, pool.probing, probeStartedAt) { instances, health, probing, startedAt ->
            DiagnosticsUiState(
                rows = instances.map { InstanceRow(it, health[it.id]) },
                probing = probing,
                probeStartedAt = startedAt
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DiagnosticsUiState()
        )

    init {
        viewModelScope.launch {
            pool.probing.collect { probing -> if (probing) probeStartedAt.value = System.currentTimeMillis() }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { pool.probeAll() }
    }

    fun setEnabled(id: String, enabled: Boolean) = pool.setEnabled(id, enabled)
    fun move(id: String, delta: Int) = pool.move(id, delta)
    fun remove(id: String) = pool.remove(id)
    fun addCustom(url: String, rssUrl: String?): Boolean = pool.addCustom(url, rssUrl)
    fun resetFromList() = pool.resetFromList()

    /** The Nitter wiki list, fetched now rather than at the next launch. */
    fun updateList() = pool.updateListAsync(force = true)

    val listStatus: StateFlow<InstancePool.ListStatus> = pool.listStatus
    fun report(): String = pool.diagnosticReport()
}
