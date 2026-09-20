package com.mtga.app.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.media.OfflineMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One post's saved files. */
data class SavedMediaPost(val postId: String, val files: Int, val bytes: Long)

/** One account's saved files, newest post first. */
data class SavedMediaGroup(
    val handle: String,
    val files: Int,
    val bytes: Long,
    val posts: List<SavedMediaPost>
)

data class SavedMediaUiState(
    val loading: Boolean = true,
    val groups: List<SavedMediaGroup> = emptyList()
) {
    val fileCount: Int get() = groups.sumOf { it.files }
    val totalBytes: Long get() = groups.sumOf { it.bytes }
}

/**
 * Reads the folder and nothing else.
 *
 * There is no index to keep in step because the file names are the index, so
 * a file the reader deleted by hand simply stops appearing. Grouping happens
 * here rather than in [OfflineMedia] so the store stays a folder and this
 * stays a screen.
 */
class SavedMediaViewModel(private val offline: OfflineMedia) : ViewModel() {

    private val _state = MutableStateFlow(SavedMediaUiState())
    val state: StateFlow<SavedMediaUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            offline.entries().filter { it.postId == postId }.forEach { offline.delete(it.file) }
            reload()
        }
    }

    fun deleteAccount(handle: String) {
        viewModelScope.launch {
            offline.entries().filter { it.handle == handle }.forEach { offline.delete(it.file) }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val entries = offline.entries()
            val groups = entries
                .groupBy { it.handle }
                .map { (handle, ofAccount) ->
                    SavedMediaGroup(
                        handle = handle,
                        files = ofAccount.size,
                        bytes = ofAccount.sumOf { it.file.length() },
                        // Post ids are snowflakes, so descending id is
                        // descending time without reading any post.
                        posts = ofAccount
                            .groupBy { it.postId }
                            .map { (postId, ofPost) ->
                                SavedMediaPost(
                                    postId = postId,
                                    files = ofPost.size,
                                    bytes = ofPost.sumOf { it.file.length() }
                                )
                            }
                            .sortedByDescending { it.postId }
                    )
                }
                .sortedByDescending { it.bytes }
            _state.value = SavedMediaUiState(loading = false, groups = groups)
        }
    }
}
