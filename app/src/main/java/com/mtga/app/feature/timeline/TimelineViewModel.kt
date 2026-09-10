package com.mtga.app.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.model.Post
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.repository.TimelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimelineUiState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val errors: Map<String, AppError> = emptyMap(),
    val followedCount: Int = 0,
    val lastUpdatedMillis: Long? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false
) {
    val isEmpty: Boolean get() = posts.isEmpty() && !loading
}

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val accounts: AccountStore
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Paint from disk first so the timeline is readable before any
            // request goes out, then refresh over the top.
            val cached = repository.cached()
            _state.value = _state.value.copy(
                posts = cached.posts,
                followedCount = accounts.accounts.value.size,
                lastUpdatedMillis = cached.oldestFetchedAtMillis,
                canLoadMore = cached.canLoadMore
            )
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val merged = repository.refresh()
            _state.value = TimelineUiState(
                posts = merged.posts,
                loading = false,
                errors = merged.errors,
                followedCount = accounts.accounts.value.size,
                lastUpdatedMillis = merged.oldestFetchedAtMillis ?: _state.value.lastUpdatedMillis,
                canLoadMore = merged.canLoadMore
            )
        }
    }

    /** Called when the reader nears the bottom. Idempotent while in flight. */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.canLoadMore) return

        viewModelScope.launch {
            _state.value = current.copy(loadingMore = true)
            val merged = repository.loadMore()
            _state.value = _state.value.copy(
                posts = merged.posts,
                loadingMore = false,
                canLoadMore = merged.canLoadMore,
                errors = merged.errors.ifEmpty { _state.value.errors }
            )
        }
    }
}
