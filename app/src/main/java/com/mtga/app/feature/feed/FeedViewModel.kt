package com.mtga.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.rss.RssSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val handle: String = "",
    val loading: Boolean = false,
    val feed: Feed? = null,
    val error: AppError? = null
)

class FeedViewModel(
    private val source: RssSource,
    private val accounts: AccountStore
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    fun load(handle: String) {
        if (_state.value.handle == handle && _state.value.feed != null) return
        _state.value = FeedUiState(handle = handle, loading = true)
        refresh()
    }

    fun refresh() {
        val handle = _state.value.handle
        if (handle.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val outcome = source.fetchFeed(handle)) {
                is Outcome.Success -> {
                    // Cache the display name so the accounts list stops showing
                    // bare handles once a feed has been read at least once.
                    accounts.updateDisplayName(handle, outcome.value.displayName)
                    _state.value = FeedUiState(handle = handle, feed = outcome.value)
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = outcome.error
                )
            }
        }
    }
}
