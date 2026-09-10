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

    suspend fun fetchPost(id: String): Post? = withContext(Dispatchers.IO) {
        if (!throttle.acquire(HOST)) return@withContext null

        val url = "https://$HOST/tweet-result?id=$id&token=${tokenFor(id)}&lang=en"
        val body = runCatching {
            val response = client.get(url) {
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value !in 200..299) return@withContext null
            response.bodyAsText()
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return@withContext null

        toPost(root)
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
            text = obj["text"]?.string().orEmpty(),
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
                    text = quote["text"]?.string().orEmpty(),
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
