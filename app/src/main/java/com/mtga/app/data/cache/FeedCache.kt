package com.mtga.app.data.cache

import android.content.Context
import com.mtga.app.core.model.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On disk cache of the last fetched feed per account.
 *
 * One JSON file per handle rather than a database. For a few hundred posts this
 * is faster to read than opening a database, adds no code generation to the
 * build, and keeps everything the app stores inspectable. Room earns its place
 * when full text search across the archive arrives, not before.
 *
 * Lives in filesDir, not cacheDir, because the system is free to delete cacheDir
 * under storage pressure and an offline reader that loses its content when the
 * phone gets full is not much of an offline reader.
 */
class FeedCache(context: Context) {

    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(handle: String): Feed? = withContext(Dispatchers.IO) {
        val file = fileFor(handle)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull()
    }

    suspend fun write(feed: Feed) = withContext(Dispatchers.IO) {
        runCatching {
            fileFor(feed.handle).writeText(json.encodeToString(trim(feed)))
        }
        Unit
    }

    /**
     * Appends a newly fetched page to what is already stored, keeping the newer
     * cursor. Deduplicated by post id, because Nitter pages overlap at their
     * boundary and a repeated post breaks LazyColumn's key contract.
     */
    suspend fun append(page: Feed): Feed = withContext(Dispatchers.IO) {
        val existing = read(page.handle)
        val combined = if (existing == null) {
            page
        } else {
            page.copy(
                posts = (existing.posts + page.posts)
                    .distinctBy { it.id }
                    .sortedByDescending { it.publishedAtMillis },
                displayName = page.displayName.ifBlank { existing.displayName },
                avatarUrl = page.avatarUrl ?: existing.avatarUrl
            )
        }
        write(combined)
        combined
    }

    suspend fun forget(handle: String) = withContext(Dispatchers.IO) {
        runCatching { fileFor(handle).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Caps what a single account can occupy, so the cache cannot grow without bound. */
    private fun trim(feed: Feed): Feed =
        if (feed.posts.size <= MAX_POSTS_PER_ACCOUNT) {
            feed
        } else {
            feed.copy(posts = feed.posts.take(MAX_POSTS_PER_ACCOUNT))
        }

    private fun fileFor(handle: String) = File(directory, "${handle.lowercase()}.json")

    private companion object {
        const val MAX_POSTS_PER_ACCOUNT = 300
    }
}
