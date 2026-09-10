package com.mtga.app.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.model.Post
import com.mtga.app.data.cache.FeedCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PostDetailState {
    data object Loading : PostDetailState
    data class Ready(val post: Post) : PostDetailState
    data object Missing : PostDetailState
}

/**
 * Reads the post from the local cache. Every post shown in a list is already
 * stored, so opening one costs no request at all.
 */
class PostDetailViewModel(private val cache: FeedCache) : ViewModel() {

    private val _state = MutableStateFlow<PostDetailState>(PostDetailState.Loading)
    val state: StateFlow<PostDetailState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(id: String, from: String?) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            val post = cache.find(id, from)
            _state.value = if (post != null) PostDetailState.Ready(post) else PostDetailState.Missing
        }
    }
}
