package com.mtga.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val handle: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val feed: Feed? = null,
    val error: AppError? = null
) {
    val canLoadMore: Boolean get() = feed?.nextCursor != null
}

class FeedViewModel(
    private val repository: FeedRepository,
    private val accounts: AccountStore,
    private val cache: FeedCache
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    fun load(handle: String) {
        if (_state.value.handle == handle && _state.value.feed != null) return
        _state.value = FeedUiState(handle = handle, loading = true)

        viewModelScope.launch {
            // Show what is on disk first. Opening an account you have read
            // before should be instant, even with no network.
            cache.read(handle)?.let { cached ->
                _state.value = _state.value.copy(feed = cached)
            }
            refresh()
        }
    }

    fun refresh() {
        val handle = _state.value.handle
        if (handle.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val outcome = repository.loadFeed(handle)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value)
                    accounts.updateDisplayName(handle, outcome.value.displayName)
                    _state.value = FeedUiState(handle = handle, feed = merged)
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = outcome.error
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.feed?.nextCursor ?: return
        if (current.loadingMore || current.loading) return

        viewModelScope.launch {
            _state.value = current.copy(loadingMore = true)
            when (val outcome = repository.loadFeed(current.handle, cursor)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value, isPagedFetch = true)
                    _state.value = _state.value.copy(feed = merged, loadingMore = false)
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loadingMore = false,
                    error = outcome.error
                )
            }
        }
    }
}
