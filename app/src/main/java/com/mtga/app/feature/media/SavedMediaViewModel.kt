package com.mtga.app.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.media.ManualSaves
import com.mtga.app.core.media.OfflineMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the screen's grid: an account's heading or one saved media. */
sealed interface SavedRow {
    val key: String
}

/** An account's heading. [appFiles] are the copies in the app's own folder, the only ones this screen deletes. */
data class SavedHeader(
    val handle: String,
    val files: Int,
    val appFiles: Int,
    val appBytes: Long
) : SavedRow {
    override val key: String get() = "h/$handle"
}

/**
 * A post's line above its cells: how many files, to open it or delete it. The
 * per post line of this screen's previous version,
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaScreen.kt
 */
data class SavedPostBar(
    val postId: String,
    val files: Int,
    val appBytes: Long
) : SavedRow {
    override val key: String get() = "p/$postId"
}

/**
 * One saved media. [thumb] is a picture to draw at the cell's size: the file of
 * a photo, the still of a video, null when a video has no still (the cell then
 * draws a plain play mark, no video frame is ever decoded for it).
 */
data class SavedCell(
    val postId: String,
    val position: Int,
    val thumb: String?,
    val video: Boolean
) : SavedRow {
    override val key: String get() = "c/$postId/$position"
}

data class SavedMediaUiState(
    val loading: Boolean = true,
    val rows: List<SavedRow> = emptyList(),
    val fileCount: Int = 0,
    val appBytes: Long = 0
)

/**
 * Reads the index and the folder and builds the rows of a lazy grid: thousands
 * of saved videos are one list of small rows, built off the main thread, and
 * only the cells in view ever draw anything.
 *
 * The automatic copies are the folder (the file names are the index), the saves
 * made by hand come from [ManualSaves]. A saved media in both shows once, as
 * the copy in the app's folder.
 */
class SavedMediaViewModel(private val offline: OfflineMedia) : ViewModel() {

    private val _state = MutableStateFlow(SavedMediaUiState())
    val state: StateFlow<SavedMediaUiState> = _state.asStateFlow()

    init {
        reload()
    }

    /**
     * The post's copies in the app's folder (stills too) and its manual-save
     * index entries. Download/MTGA is never touched. The old deletePost of this
     * screen, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaViewModel.kt
     */
    fun deletePost(postId: String) {
        viewModelScope.launch {
            offline.deleteWhere { it.postId == postId }
            withContext(Dispatchers.IO) { offline.manual.removePost(postId) }
            reload()
        }
    }

    fun deleteAccount(handle: String) {
        viewModelScope.launch {
            offline.deleteWhere { it.handle == handle }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val auto = offline.entries()
            // Reads the folder again too, which also catches the manual saves.
            val stills = offline.stillPaths()
            val built = withContext(Dispatchers.Default) {
                val sized = auto.map { Auto(it.handle, it.postId, it.index, it.file.path, it.file.length()) }
                build(sized, stills, offline.manual.all())
            }
            _state.value = built.copy(loading = false)
        }
    }

    /** One file of the app's folder with its size, read once. */
    internal data class Auto(val handle: String, val postId: String, val position: Int, val path: String, val bytes: Long)

    internal companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv")

        /**
         * The rows of the grid, pure. Accounts with the most files first, then
         * each account's posts newest first (post ids are snowflakes, so a
         * bigger id is a later post), the media of a post in order. [stills] are
         * keyed "postId/position" like the store's own index. Taken from this
         * screen's own grouping, which listed the same folder by account:
         * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaViewModel.kt
         */
        fun build(auto: List<Auto>, stills: Map<String, String>, manual: List<ManualSaves.Save>): SavedMediaUiState {
            class Item(val handle: String, val cell: SavedCell, val appBytes: Long?)

            val items = LinkedHashMap<String, Item>()
            for (file in auto) {
                val video = file.path.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS
                val thumb = if (video) stills["${file.postId}/${file.position}"] else file.path
                val cell = SavedCell(file.postId, file.position, thumb, video)
                items[cell.key] = Item(file.handle, cell, file.bytes)
            }
            for (save in manual) {
                val cell = SavedCell(save.post, save.position, if (save.video) null else save.uri, save.video)
                items.putIfAbsent(cell.key, Item(save.handle, cell, null))
            }

            val rows = ArrayList<SavedRow>(items.size + 16)
            items.values.groupBy { it.handle }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<Item>>> { it.value.size }.thenBy { it.key })
                .forEach { (handle, ofAccount) ->
                    val copies = ofAccount.filter { it.appBytes != null }
                    rows += SavedHeader(handle, ofAccount.size, copies.size, copies.sumOf { it.appBytes ?: 0L })
                    ofAccount
                        .sortedWith(
                            compareByDescending<Item> { it.cell.postId.length }
                                .thenByDescending { it.cell.postId }
                                .thenBy { it.cell.position }
                        )
                        .groupBy { it.cell.postId }
                        .forEach { (postId, ofPost) ->
                            rows += SavedPostBar(postId, ofPost.size, ofPost.sumOf { it.appBytes ?: 0L })
                            ofPost.forEach { rows += it.cell }
                        }
                }
            return SavedMediaUiState(
                loading = false,
                rows = rows,
                fileCount = items.size,
                appBytes = items.values.sumOf { it.appBytes ?: 0L }
            )
        }
    }
}
