package com.mtga.app.data.cache

import android.content.Context
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostId
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
class FeedCache(
    context: Context,
    /** Days to keep saved posts, 0 for no limit. Read at each write. */
    private val retentionDays: () -> Int = { 0 }
) {

    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(handle: String): Feed? = withContext(Dispatchers.IO) {
        val file = fileFor(handle)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull()?.let(::canonical)
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
    suspend fun append(page: Feed, isPagedFetch: Boolean = false): Feed = withContext(Dispatchers.IO) {
        val incoming = canonical(page)
        val existing = read(incoming.handle)
        if (existing == null) {
            write(incoming)
            return@withContext incoming
        }

        val known = existing.posts.map { it.id }.toSet()
        val newPosts = incoming.posts.filterNot { it.id in known }

        // A paged fetch that brings back nothing new means the cursor did not
        // advance. Continuing would loop forever on the same page, so stop
        // offering to load more rather than spinning against the instance.
        val exhausted = isPagedFetch && newPosts.isEmpty()

        val combined = incoming.copy(
            posts = (existing.posts + newPosts).sortedByDescending { it.publishedAtMillis },
            displayName = incoming.displayName.ifBlank { existing.displayName },
            avatarUrl = incoming.avatarUrl ?: existing.avatarUrl,
            bio = incoming.bio ?: existing.bio,
            bannerUrl = incoming.bannerUrl ?: existing.bannerUrl,
            location = incoming.location ?: existing.location,
            website = incoming.website ?: existing.website,
            joined = incoming.joined ?: existing.joined,
            stats = incoming.stats ?: existing.stats,
            nextCursor = if (exhausted) null else incoming.nextCursor ?: existing.nextCursor
        )
        write(combined)
        combined
    }

    /**
     * Finds one stored post. [hint] is the account whose file most likely holds
     * it. Otherwise every file is scanned, which is a few local reads and only
     * happens when a post is opened from a place that could not tell.
     */
    suspend fun find(id: String, hint: String?): Post? = withContext(Dispatchers.IO) {
        val wanted = PostId.normalize(id)
        hint?.let { read(it) }?.posts?.firstOrNull { it.id == wanted }?.let { return@withContext it }
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .map(::canonical)
            .firstNotNullOfOrNull { feed -> feed.posts.firstOrNull { it.id == wanted } }
    }

    /** Every saved post across all accounts, each once, newest first. */
    suspend fun allPosts(): List<Post> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .flatMap { canonical(it).posts.asSequence() }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .toList()
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

    /**
     * Rewrites ids to their canonical form and drops duplicates. Also repairs
     * caches written before 1.1.3, where the same post could be stored twice
     * under two id formats. The cleaned version is persisted on the next write.
     */
    private fun canonical(feed: Feed): Feed {
        val posts = feed.posts
            .map { post -> PostId.normalize(post.id).let { if (it == post.id) post else post.copy(id = it) } }
            .distinctBy { it.id }
        return if (posts == feed.posts) feed else feed.copy(posts = posts)
    }

    /**
     * Caps what a single account can occupy, so the cache cannot grow without
     * bound, and drops posts older than the reader's retention. Older posts
     * can still be read by scrolling back, they are simply not kept.
     */
    private fun trim(feed: Feed): Feed {
        val days = retentionDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * DAY_MS else Long.MIN_VALUE
        val kept = feed.posts
            .filter { it.publishedAtMillis <= 0L || it.publishedAtMillis >= cutoff }
            .take(MAX_POSTS_PER_ACCOUNT)
        return if (kept.size == feed.posts.size) feed else feed.copy(posts = kept)
    }

    /**
     * Applies the retention to every saved account now, rather than at each
     * account's next fetch. Run at launch and when the setting changes.
     */
    suspend fun applyRetention() = withContext(Dispatchers.IO) {
        if (retentionDays() <= 0) return@withContext
        directory.listFiles().orEmpty().forEach { file ->
            runCatching {
                val feed = json.decodeFromString<Feed>(file.readText())
                val trimmed = trim(feed)
                if (trimmed !== feed) file.writeText(json.encodeToString(trimmed))
            }
        }
    }

    private fun fileFor(handle: String) = File(directory, "${handle.lowercase()}.json")

    private companion object {
        const val MAX_POSTS_PER_ACCOUNT = 300
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
