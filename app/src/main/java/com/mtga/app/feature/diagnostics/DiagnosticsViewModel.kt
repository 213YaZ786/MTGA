package com.mtga.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.common.present
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.instances.InstanceHealth
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.NitterInstance
import com.mtga.app.data.twstalker.TwstalkerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * The result of reading one account straight from twstalker. [passed] is null
 * until the test ends. [lines] are plain sentences, one per step.
 */
data class TwstalkerTestState(
    val running: Boolean = false,
    val handle: String? = null,
    val lines: List<String> = emptyList(),
    val passed: Boolean? = null
)

class DiagnosticsViewModel(
    private val pool: InstancePool,
    private val twstalker: TwstalkerSource,
    private val accounts: AccountStore
) : ViewModel() {

    private val _twstalkerTest = MutableStateFlow(TwstalkerTestState())
    val twstalkerTest: StateFlow<TwstalkerTestState> = _twstalkerTest.asStateFlow()

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

    /**
     * Reads page one of an account from twstalker, then page two if a cursor
     * came back. Page one is HTML through the challenge gateway, page two is
     * the JSON endpoint on the native client, so both paths get exercised.
     *
     * twstalker is the last fallback and is never reached while xcancel
     * answers. Without this test the first time it runs would be the day it is
     * needed. Nothing is cached, and the normal reading order is untouched.
     * Every request lands in the request log with its usual detail.
     */
    fun testTwstalker() {
        if (_twstalkerTest.value.running) return
        val handle = accounts.accounts.value.firstOrNull()?.handle ?: FALLBACK_HANDLE
        _twstalkerTest.value = TwstalkerTestState(running = true, handle = handle)

        viewModelScope.launch {
            val lines = mutableListOf<String>()
            fun publish(passed: Boolean? = null) {
                _twstalkerTest.value = TwstalkerTestState(
                    running = passed == null,
                    handle = handle,
                    lines = lines.toList(),
                    passed = passed
                )
            }

            val feed = when (val first = twstalker.fetch(handle, null)) {
                is Outcome.Success -> first.value
                is Outcome.Failure -> {
                    lines += "Page one failed: " + first.error.present().headline
                    publish(passed = false)
                    return@launch
                }
            }
            lines += "Page one: ${feed.posts.size} posts" +
                if (feed.nextCursor == null) ", no next page" else ", next page offered"
            if (feed.nextCursor == null) {
                publish(passed = feed.posts.isNotEmpty())
                return@launch
            }
            publish()

            when (val second = twstalker.fetch(handle, feed.nextCursor)) {
                is Outcome.Success -> {
                    lines += "Page two: ${second.value.posts.size} posts"
                    publish(passed = feed.posts.isNotEmpty() && second.value.posts.isNotEmpty())
                }
                is Outcome.Failure -> {
                    lines += "Page two failed: " + second.error.present().headline
                    publish(passed = false)
                }
            }
        }
    }

    private companion object {
        /** Used when nothing is followed. A large account that always has posts. */
        const val FALLBACK_HANDLE = "nytimes"
    }
}
