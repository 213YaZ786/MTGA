package com.mtga.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.common.valueOrNull
import com.mtga.app.core.model.Feed
import com.mtga.app.core.web.ChallengeSolver
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
    val error: AppError? = null,
    val pagingFailed: Boolean = false
) {
    val canLoadMore: Boolean get() = feed?.nextCursor != null
}

class FeedViewModel(
    private val repository: FeedRepository,
    private val accounts: AccountStore,
    private val cache: FeedCache,
    private val solver: ChallengeSolver
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

            // x.com first. It is the quickest and most accurate head of the
            // feed, so it goes on screen while the instances work on depth.
            repository.loadHead(handle)?.valueOrNull()?.let { head ->
                val merged = cache.append(head)
                accounts.updateDisplayName(handle, head.displayName)
                _state.value = _state.value.copy(feed = merged)
            }

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

    fun loadMore(manual: Boolean = false) {
        val current = _state.value
        val cursor = current.feed?.nextCursor ?: return
        if (current.loadingMore || current.loading) return
        if (current.pagingFailed && !manual) return

        viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, pagingFailed = false)
            val before = current.feed?.posts?.size ?: 0
            when (val outcome = repository.loadFeed(current.handle, cursor)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value, isPagedFetch = true)
                    _state.value = _state.value.copy(
                        feed = merged,
                        loadingMore = false,
                        pagingFailed = merged.posts.size <= before
                    )
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loadingMore = false,
                    pagingFailed = true,
                    error = outcome.error
                )
            }
        }
    }

    /**
     * The user asked to complete a bot check by hand. On success the host is
     * cleared for the session and the feed is read again straight away.
     */
    fun verify(error: AppError.ChallengeRequired) {
        viewModelScope.launch {
            val result = solver.solve(error.url, error.host, interactive = true)
            if (result is ChallengeSolver.Result.Cleared) refresh()
        }
    }
}
