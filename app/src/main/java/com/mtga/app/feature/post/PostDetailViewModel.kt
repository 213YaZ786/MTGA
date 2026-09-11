package com.mtga.app.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Post
import com.mtga.app.core.web.ChallengeSolver
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.repository.FeedRepository
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.core.link.ShareLink
import com.mtga.app.data.xcom.SyndicationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ThreadState {
    data object Loading : ThreadState
    data class Ready(val conversation: Conversation) : ThreadState
    data class Failed(val error: AppError) : ThreadState
}

data class PostDetailUiState(
    val post: Post? = null,
    val lookingUp: Boolean = true,
    val thread: ThreadState = ThreadState.Loading
) {
    /** Neither the phone nor the server could produce the post. */
    val missing: Boolean get() = post == null && !lookingUp && thread !is ThreadState.Loading
}

/**
 * Shows the post at once from what the phone already has, then fetches its
 * conversation: what it answers, the author's thread, and the replies.
 * Replies are never saved, they are only worth reading fresh.
 */
class PostDetailViewModel(
    private val cache: FeedCache,
    private val repository: FeedRepository,
    private val solver: ChallengeSolver,
    private val syndication: SyndicationSource,
    private val settings: SettingsStore,
    private val pool: InstancePool
) : ViewModel() {

    /**
     * What Share and Copy link hand out. The first enabled server in the
     * reader's own order, not the fastest one right now, so the same post
     * always shares the same link. x.com when the setting is off or the list
     * is empty.
     */
    fun shareLink(post: Post): String {
        val host = if (settings.current.shareAsNitter) {
            pool.instances.value.firstOrNull { it.enabled }?.host
        } else {
            null
        }
        return ShareLink.forPost(post.authorHandle, post.id, post.permalink, host)
    }

    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var hint: String? = null

    fun load(id: String, from: String?) {
        if (loadedId == id) return
        loadedId = id
        hint = from
        viewModelScope.launch {
            // A post opened from a link is usually not on the phone. X's embed
            // endpoint returns it in one quick request, and it also names the
            // author, which a /i/web/status link does not. Only when reading
            // from X is switched on, since X sees the request.
            val known = cache.find(id, from) ?: RecentPosts.get(id)
                ?: if (settings.current.useXcomDirect) syndication.fetchPost(id) else null
            _state.value = PostDetailUiState(post = known, lookingUp = false, thread = ThreadState.Loading)
            fetchThread()
        }
    }

    fun retryThread() {
        if (_state.value.thread is ThreadState.Loading) return
        _state.value = _state.value.copy(thread = ThreadState.Loading)
        viewModelScope.launch { fetchThread() }
    }

    fun verify(error: AppError.ChallengeRequired) {
        viewModelScope.launch {
            val result = solver.solve(error.url, error.host, interactive = true)
            if (result is ChallengeSolver.Result.Cleared) retryThread()
        }
    }

    private suspend fun fetchThread() {
        val id = loadedId ?: return
        val handle = _state.value.post?.authorHandle ?: hint?.takeIf { it.isNotBlank() }
        if (handle == null) {
            // Nitter needs the author in the address, and nothing named one.
            _state.value = _state.value.copy(
                thread = ThreadState.Failed(AppError.Unknown("this link does not name the author"))
            )
            return
        }
        when (val outcome = repository.loadConversation(handle, id)) {
            is Outcome.Success -> {
                val conversation = outcome.value
                RecentPosts.remember(conversation)
                _state.value = _state.value.copy(
                    post = _state.value.post ?: conversation.main,
                    thread = ThreadState.Ready(conversation)
                )
            }
            is Outcome.Failure -> _state.value = _state.value.copy(thread = ThreadState.Failed(outcome.error))
        }
    }
}

/**
 * Posts seen in conversations during this session, so tapping a reply opens
 * it at once instead of waiting for the network. Memory only, never saved.
 */
internal object RecentPosts {
    private const val CAPACITY = 300
    private val posts = object : LinkedHashMap<String, Post>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Post>?) = size > CAPACITY
    }

    @Synchronized
    fun get(id: String): Post? = posts[id]

    @Synchronized
    fun remember(conversation: Conversation) {
        (conversation.ancestors + listOfNotNull(conversation.main) + conversation.continuation +
            conversation.replies.flatten()).forEach { posts[it.id] = it }
    }
}
