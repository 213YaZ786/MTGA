package com.mtga.app.data.xcom

import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads one post from X's own embed CDN.
 *
 * This is the endpoint behind Twitter's embed widget, so it is public,
 * unauthenticated, and returns a complete copy of a post including media
 * variants. Nothing here is scraped: the response is structured JSON that X
 * publishes deliberately for third party sites.
 *
 * Known limits: age restricted posts answer 404, and there is no timeline
 * equivalent, one post per call.
 */
class SyndicationSource(
    private val client: HttpClient,
    private val throttle: HostThrottle
) {

    /**
     * [throttled] false skips the host spacing, for the archive's batches:
     * this is a CDN made for pages that embed many posts at once, and the
     * caller bounds how many run together.
     */
    suspend fun fetchPost(id: String, throttled: Boolean = true, completeLongText: Boolean = false): Post? =
        (read(id, throttled, completeLongText) as? Read.Found)?.post

    /** What reading a post by its id gave: the post, a post X no longer serves, or a failure worth retrying. */
    sealed interface Read {
        class Found(val post: Post) : Read
        /** Deleted, withheld, age restricted: this endpoint will not give it, now or later. */
        data object Gone : Read
        data object Failed : Read
    }

    /** As [fetchPost], telling a post that is gone from a read that failed. */
    suspend fun read(id: String, throttled: Boolean = true, completeLongText: Boolean = false): Read = withContext(Dispatchers.IO) {
        if (throttled && !throttle.acquire(HOST)) return@withContext Read.Failed

        val url = "https://$HOST/tweet-result?id=$id&token=${tokenFor(id)}&lang=en"
        val body = runCatching {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value == 404) return@withContext Read.Gone
            if (response.status.value !in 200..299) return@withContext Read.Failed
            response.bodyAsText()
        }.getOrNull() ?: return@withContext Read.Failed

        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return@withContext Read.Failed
        // A deleted or withheld post answers 200 with a tombstone in its place.
        if ((root["__typename"] as? JsonPrimitive)?.content == "TweetTombstone") return@withContext Read.Gone

        val post = toPost(root) ?: return@withContext Read.Failed
        // A long post comes cut at 280 characters: this endpoint names its
        // whole text (note_tweet) without giving it.
        if (completeLongText && isCut(root)) {
            longText(post.authorHandle, id)?.takeIf { it.length > post.text.length }?.let { return@withContext Read.Found(post.copy(text = it)) }
        }
        Read.Found(post)
    }

    private fun isCut(obj: JsonObject): Boolean {
        val note = obj["note_tweet"] as? JsonObject ?: return false
        return note["text"] == null && note["note_tweet_results"] == null
    }

    /**
     * The whole text of a long post, from FxTwitter's public API (the service
     * behind post previews in chat apps), which reads it from X. Used only
     * for posts X's embed gives cut, and only with the archives allowed.
     */
    suspend fun longText(handle: String, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get("https://$FX_HOST/$handle/status/$id") {
                header("User-Agent", "MTGA")
                header("Accept", "application/json")
            }
            if (response.status.value != 200) return@runCatching null
            val tweet = (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.get("tweet") as? JsonObject
            tweet?.get("text")?.string()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun toPost(obj: JsonObject): Post? {
        val id = obj["id_str"]?.string() ?: return null
        val user = obj["user"] as? JsonObject
        val handle = user?.get("screen_name")?.string() ?: return null

        return Post(
            id = id,
            authorHandle = handle,
            authorName = user["name"]?.string() ?: handle,
            avatarUrl = user["profile_image_url_https"]?.string(),
            text = fullText(obj),
            publishedAtMillis = snowflakeToMillis(id),
            permalink = "https://x.com/$handle/status/$id",
            kind = if (obj["quoted_tweet"] is JsonObject) PostKind.QUOTE else PostKind.ORIGINAL,
            media = readMedia(obj["mediaDetails"]),
            quoted = (obj["quoted_tweet"] as? JsonObject)?.let { quote ->
                val quoteUser = quote["user"] as? JsonObject
                val quoteHandle = quoteUser?.get("screen_name")?.string().orEmpty()
                QuotedPost(
                    handle = quoteHandle,
                    name = quoteUser?.get("name")?.string().orEmpty(),
                    text = fullText(quote),
                    permalink = "https://x.com/$quoteHandle/status/" +
                        quote["id_str"]?.string().orEmpty()
                )
            },
            stats = PostStats(
                replies = obj["conversation_count"]?.int(),
                likes = obj["favorite_count"]?.int()
            )
        )
    }

    /**
     * X keeps only the first 280 characters of a long post in "text" and ends
     * it with the t.co link of the post's own media, which is why a post could
     * stop mid sentence. The whole text of a long post lives under note_tweet.
     * The exact shape of that field on this endpoint could not be checked from
     * the sandbox, so both spellings seen on x.com are tried and the short text
     * stays the fallback.
     */
    private fun fullText(obj: JsonObject): String {
        val note = obj["note_tweet"] as? JsonObject
        val noteText = note?.get("text")?.string()
            ?: ((note?.get("note_tweet_results") as? JsonObject)?.get("result") as? JsonObject)
                ?.get("text")?.string()
        return trimTrailingLink(noteText ?: obj["text"]?.string().orEmpty(), obj)
    }

    /**
     * Drops the t.co link X appends for the post's own media or quote, which
     * points back at the post and is already shown as media. A link the author
     * typed is listed in entities.urls and is kept.
     */
    private fun trimTrailingLink(text: String, obj: JsonObject): String {
        val trimmed = text.trimEnd()
        val at = trimmed.lastIndexOf("https://t.co/")
        if (at < 0) return trimmed
        val tail = trimmed.substring(at)
        if (tail.any(Char::isWhitespace)) return trimmed
        val typed = ((obj["entities"] as? JsonObject)?.get("urls") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("url")?.string() }
            .orEmpty()
        if (tail in typed) return trimmed
        return trimmed.substring(0, at).trimEnd()
    }

    /**
     * Picks the highest bitrate mp4 for each video, which is the whole reason
     * to come here rather than take whatever a front end re-serves.
     */
    private fun readMedia(element: JsonElement?): List<MediaItem> {
        val details = element as? JsonArray ?: return emptyList()
        return details.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val still = obj["media_url_https"]?.string() ?: return@mapNotNull null
            when (obj["type"]?.string()) {
                "photo" -> MediaItem(still, "$still?name=orig", MediaType.PHOTO)
                "video", "animated_gif" -> {
                    val best = (obj["video_info"] as? JsonObject)
                        ?.let { it["variants"] as? JsonArray }
                        ?.mapNotNull { it as? JsonObject }
                        ?.filter { it["content_type"]?.string() == "video/mp4" }
                        ?.maxByOrNull { it["bitrate"]?.int() ?: 0 }
                        ?.get("url")?.string()
                    MediaItem(
                        previewUrl = still,
                        downloadUrl = best ?: still,
                        type = if (obj["type"]?.string() == "animated_gif") {
                            MediaType.GIF
                        } else {
                            MediaType.VIDEO
                        }
                    )
                }
                else -> null
            }
        }
    }

    private fun JsonElement.string(): String? =
        (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonElement.int(): Int? = (this as? JsonPrimitive)?.content?.toIntOrNull()

    companion object {
        const val HOST = "cdn.syndication.twimg.com"
        private const val FX_HOST = "api.fxtwitter.com"

        fun snowflakeToMillis(id: String): Long {
            val numeric = id.toLongOrNull() ?: return 0L
            return (numeric shr 22) + 1_288_834_974_657L
        }

        /**
         * The widget derives this from the id. Any stable value is accepted, so
         * this is deterministic rather than random, which keeps requests
         * cacheable instead of looking like a new client every time.
         */
        private fun tokenFor(id: String): String {
            val numeric = id.toLongOrNull() ?: return "mtga"
            return java.lang.Long.toString(numeric % 1_000_000_007L, 36)
        }

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
}
