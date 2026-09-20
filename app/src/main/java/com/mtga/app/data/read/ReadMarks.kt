package com.mtga.app.data.read

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Which posts have already gone past the top of the screen.
 *
 * A post is read once the reader has scrolled past it, not after a timer and
 * not when it is tapped: almost every post here is read without ever being
 * opened, and a timer would clear the three cards a refresh puts on screen
 * before the reader had looked at them.
 *
 * A plain list of ids in a file. There is no date and no per post record
 * because the only question ever asked is "has this one gone by", and the
 * list is bounded, so it cannot grow past the cache it describes.
 */
class ReadMarks(context: Context) {

    private val file = File(context.filesDir, "read-posts.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _read = MutableStateFlow<Set<String>>(emptySet())
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    /**
     * False until [load] has run.
     *
     * Everything counts as read while it is false. Without that, Home paints
     * one frame with every card bordered before the file comes back, which is
     * a flash of blue on every launch.
     */
    @Volatile
    var ready: Boolean = false
        private set

    suspend fun load() {
        val stored = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching { json.decodeFromString<List<String>>(file.readText()) }
                .getOrDefault(emptyList())
        }
        _read.value = stored.toSet()
        ready = true
    }

    /**
     * Treats everything given as already read, without touching what is
     * already marked.
     *
     * Run at launch over the posts the cache already held, so a reader coming
     * back after a week sees a border on what arrived since and not on the
     * two hundred posts that were there before.
     */
    suspend fun markAllRead(ids: Set<String>) = add(ids)

    /** One pass of the list, so scrolling costs one write and not one per card. */
    suspend fun markRead(ids: List<String>) {
        val fresh = ids.filterNot { it in _read.value }
        if (fresh.isEmpty()) return
        add(fresh.toSet())
    }

    private suspend fun add(ids: Set<String>) {
        if (ids.isEmpty()) return
        val merged = trim(_read.value + ids)
        if (merged == _read.value) return
        _read.value = merged
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(merged.toList())) }
        }
    }

    companion object {
        /**
         * Comfortably more than any cache, so a post is never forgotten and
         * shown as new a second time, and still small enough to write in one
         * go.
         */
        const val MAX_IDS = 20_000

        /**
         * Drops the lowest ids first when the list is too long.
         *
         * Post ids are snowflakes, so lowest is oldest without reading a
         * single post, and the oldest is what the cache drops first anyway.
         */
        internal fun trim(ids: Set<String>, max: Int = MAX_IDS): Set<String> {
            if (ids.size <= max) return ids
            return ids.sortedDescending().take(max).toSet()
        }
    }
}
