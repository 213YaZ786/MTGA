package com.mtga.app.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.repository.TimelineRepository
import com.mtga.app.data.settings.Settings
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The Home filters. Each is a narrowing, so none selected means everything. */
data class HomeFilters(
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
    val mediaOnly: Boolean = false
) {
    val active: Boolean get() = hideReplies || hideReposts || mediaOnly

    fun keeps(post: Post): Boolean =
        !(hideReplies && post.kind == PostKind.REPLY) &&
            !(hideReposts && post.kind == PostKind.REPOST) &&
            !(mediaOnly && post.media.isEmpty())
}

data class TimelineUiState(
    /** Everything stored, unfiltered. */
    val allPosts: List<Post> = emptyList(),
    val filters: HomeFilters = HomeFilters(),
    val loading: Boolean = false,
    val errors: Map<String, AppError> = emptyMap(),
    val followedCount: Int = 0,
    val lastUpdatedMillis: Long? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /**
     * Set when a page request fails. Automatic prefetch stops, and the reader
     * gets a button instead. Retrying a rate limited host on a scroll gesture
     * is how a 429 turns into a fifteen minute ban.
     */
    val pagingFailed: Boolean = false,
    /** Posts that arrived above the reader with the last refresh, for the pill. */
    val newPostCount: Int = 0
) {
    val posts: List<Post> = if (filters.active) allPosts.filter(filters::keeps) else allPosts
    val isEmpty: Boolean get() = allPosts.isEmpty() && !loading
}

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val accounts: AccountStore,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState(filters = settings.current.toFilters()))
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    init {
        settings.settings
            .onEach { _state.value = _state.value.copy(filters = it.toFilters()) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            // Paint from disk first so the timeline is readable before any
            // request goes out, then refresh over the top.
            val cached = repository.cached()
            _state.value = _state.value.copy(
                allPosts = cached.posts,
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
            val newestBefore = _state.value.allPosts.maxOfOrNull { it.publishedAtMillis }
            val merged = repository.refresh()
            val arrived = if (newestBefore == null) {
                0
            } else {
                merged.posts.count { it.publishedAtMillis > newestBefore && _state.value.filters.keeps(it) }
            }
            _state.value = _state.value.copy(
                allPosts = merged.posts,
                loading = false,
                errors = merged.errors,
                followedCount = accounts.accounts.value.size,
                lastUpdatedMillis = merged.oldestFetchedAtMillis ?: _state.value.lastUpdatedMillis,
                canLoadMore = merged.canLoadMore,
                loadingMore = false,
                pagingFailed = false,
                newPostCount = _state.value.newPostCount + arrived
            )
        }
    }

    /** The reader has seen the top of the list, or tapped the pill. */
    fun clearNewPosts() {
        if (_state.value.newPostCount != 0) _state.value = _state.value.copy(newPostCount = 0)
    }

    fun setFilters(filters: HomeFilters) = settings.update {
        it.copy(
            homeHideReplies = filters.hideReplies,
            homeHideReposts = filters.hideReposts,
            homeMediaOnly = filters.mediaOnly
        )
    }

    /**
     * Called when the reader nears the bottom, and by the retry button.
     * [manual] bypasses the failure latch, so a person can insist, but a scroll
     * gesture cannot.
     */
    fun loadMore(manual: Boolean = false) {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.canLoadMore) return
        if (current.pagingFailed && !manual) return

        viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, pagingFailed = false)
            val before = current.allPosts.size
            val merged = repository.loadMore()
            _state.value = _state.value.copy(
                allPosts = merged.posts,
                loadingMore = false,
                canLoadMore = merged.canLoadMore,
                errors = merged.errors.ifEmpty { _state.value.errors },
                pagingFailed = merged.errors.isNotEmpty() || merged.posts.size <= before
            )
        }
    }

    private fun Settings.toFilters() = HomeFilters(
        hideReplies = homeHideReplies,
        hideReposts = homeHideReposts,
        mediaOnly = homeMediaOnly
    )
}
