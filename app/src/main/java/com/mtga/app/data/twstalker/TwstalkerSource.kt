package com.mtga.app.data.twstalker

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HostThrottle
import com.mtga.app.core.web.ChallengeGateway
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * A completely independent source, so it fails for different reasons than
 * Nitter does. That is the entire point of adding it.
 *
 * Page one is scraped, purely to obtain the numeric user id and the first
 * cursor. Everything after that comes from the site's own JSON endpoint, which
 * returns structured posts and needs no markup assumptions at all.
 *
 * Trade-off worth stating plainly: this host carries advertising and analytics,
 * so it learns which accounts are being read. MTGA therefore treats it as a
 * fallback rather than a peer of the Nitter pool, and always names it as the
 * server that answered.
 */
class TwstalkerSource(
    private val client: HttpClient,
    private val parser: TwstalkerParser,
    private val log: RequestLog,
    private val throttle: HostThrottle,
    private val gateway: ChallengeGateway
) {

    suspend fun fetch(handle: String, cursor: String?): Outcome<Feed> =
        if (cursor == null) firstPage(handle) else nextPage(handle, cursor)

    // ---- page one, scraped --------------------------------------------------

    private suspend fun firstPage(handle: String): Outcome<Feed> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/$handle"
        if (!throttle.acquire(HOST)) {
            return@withContext Outcome.Failure(AppError.RateLimited(HOST, null))
        }

        val startedAt = System.nanoTime()
        try {
            // Page one is HTML and may sit behind a check, so it goes through
            // the gateway. Later pages are a JSON POST and stay native: they
            // carry the cookie once the host is cleared, and when that is not
            // enough the check is reported rather than guessed around.
            val fetched = gateway.getPage(
                url = url,
                host = HOST,
                kind = RequestLog.Kind.PROFILE,
                requestHeaders = mapOf(
                    "User-Agent" to BROWSER_USER_AGENT,
                    "Accept" to "text/html,application/xhtml+xml"
                )
            )
            val body = fetched.body
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            ErrorMapper.fromStatus(HOST, url, fetched.status, fetched.retryAfterSeconds, body, handle)
                ?.let { error ->
                    log.record(
                        RequestLog.Kind.PROFILE, url, "HTTP error",
                        fetched.status, body.length, elapsed,
                        error::class.java.simpleName + " via " + fetched.via.name
                    )
                    return@withContext Outcome.Failure(error)
                }

            val page = parser.parseProfile(body, handle)
            if (page == null) {
                log.record(
                    RequestLog.Kind.PROFILE, url, "parse found no posts",
                    fetched.status, body.length, elapsed,
                    "twstalker layout changed, or the account has no public posts"
                )
                return@withContext Outcome.Failure(
                    AppError.ParseFailure(HOST, SELECTOR_SET_VERSION, body.take(200))
                )
            }

            val feed = page.feed.copy(nextCursor = encodeCursor(page.userId, INITIAL_PAGE, page.cursor))
            log.record(
                RequestLog.Kind.PROFILE, url, "ok",
                fetched.status, body.length, elapsed,
                "posts: ${feed.posts.size} | user id: ${page.userId ?: "NOT FOUND"} | " +
                    "cursor: ${page.cursor?.take(16)?.plus("...") ?: "NONE"}"
            )
            Outcome.Success(feed)
        } catch (t: Throwable) {
            log.record(
                RequestLog.Kind.PROFILE, url, "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            Outcome.Failure(ErrorMapper.fromThrowable(HOST, t))
        }
    }

    // ---- later pages, JSON --------------------------------------------------

    private suspend fun nextPage(handle: String, cursor: String): Outcome<Feed> =
        withContext(Dispatchers.IO) {
            val (userId, page, token) = decodeCursor(cursor)
                ?: return@withContext Outcome.Failure(
                    AppError.Unknown("malformed twstalker cursor")
                )

            val url = "https://$HOST/service/api"
            if (!throttle.acquire(HOST)) {
                return@withContext Outcome.Failure(AppError.RateLimited(HOST, null))
            }

            val startedAt = System.nanoTime()
            try {
                val form = listOf(
                    "page" to page.toString(),
                    "cursor" to token,
                    "data" to userId,
                    "action" to "profile"
                ).joinToString("&") { (k, v) ->
                    "$k=" + URLEncoder.encode(v, "UTF-8")
                }

                val response = client.post(url) {
                    header("User-Agent", BROWSER_USER_AGENT)
                    header("X-Requested-With", "XMLHttpRequest")
                    header("Referer", "https://$HOST/$handle")
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(form)
                }
                val body = response.bodyAsText()
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000

                ErrorMapper.fromResponse(HOST, response, body, handle)?.let { error ->
                    log.record(
                        RequestLog.Kind.PAGE, url, "HTTP error",
                        response.status.value, body.length, elapsed,
                        error::class.java.simpleName
                    )
                    return@withContext Outcome.Failure(error)
                }

                val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                val posts = root?.get("tweets")?.let(::readPosts).orEmpty()
                val nextToken = root?.get("cursor")?.asStringOrNull()

                if (posts.isEmpty()) {
                    log.record(
                        RequestLog.Kind.PAGE, url, "no posts in response",
                        response.status.value, body.length, elapsed,
                        "page $page returned nothing, treating as end of feed"
                    )
                    return@withContext Outcome.Success(
                        Feed(handle, handle, emptyList(), HOST, System.currentTimeMillis())
                    )
                }

                log.record(
                    RequestLog.Kind.PAGE, url, "ok",
                    response.status.value, body.length, elapsed,
                    "posts: ${posts.size} | next cursor: ${nextToken?.take(16)?.plus("...") ?: "NONE"}"
                )

                Outcome.Success(
                    Feed(
                        handle = handle,
                        displayName = posts.firstOrNull()?.authorName ?: handle,
                        posts = posts,
                        fetchedFromHost = HOST,
                        fetchedAtMillis = System.currentTimeMillis(),
                        nextCursor = nextToken?.let { encodeCursor(userId, page + 1, it) }
                    )
                )
            } catch (t: Throwable) {
                log.record(
                    RequestLog.Kind.PAGE, url, "transport failure",
                    durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                    detail = "${t::class.java.simpleName}: ${t.message}"
                )
                Outcome.Failure(ErrorMapper.fromThrowable(HOST, t))
            }
        }

    // ---- json mapping -------------------------------------------------------

    /** The endpoint returns either an array or an object keyed by id. */
    private fun readPosts(element: JsonElement): List<Post> = when (element) {
        is JsonArray -> element.mapNotNull { toPost(it) }
        is JsonObject -> element.values.mapNotNull { toPost(it) }
        else -> emptyList()
    }

    private fun toPost(element: JsonElement): Post? {
        val obj = element as? JsonObject ?: return null
        val core = obj["core"] as? JsonObject
        val id = obj["conversation_id_str"]?.asStringOrNull() ?: return null
        val handle = core?.get("screen_name")?.asStringOrNull() ?: return null

        return Post(
            id = id,
            authorHandle = handle,
            authorName = core["name"]?.asStringOrNull() ?: handle,
            avatarUrl = core["profile_image_url_https"]?.asStringOrNull(),
            text = obj["full_text"]?.asStringOrNull()?.let(parser::decodeEntities).orEmpty(),
            publishedAtMillis = parser.snowflakeToMillis(id),
            permalink = "https://$HOST/$handle/status/$id",
            kind = if (obj["is_retweet"]?.asBooleanOrNull() == true) {
                PostKind.REPOST
            } else if (obj["quoted_status"] is JsonObject) {
                PostKind.QUOTE
            } else {
                PostKind.ORIGINAL
            },
            media = readMedia(obj["extended_entities"]),
            quoted = (obj["quoted_status"] as? JsonObject)?.let { quote ->
                val quoteCore = quote["core"] as? JsonObject
                val quoteHandle = quoteCore?.get("screen_name")?.asStringOrNull().orEmpty()
                QuotedPost(
                    handle = quoteHandle,
                    name = quoteCore?.get("name")?.asStringOrNull().orEmpty(),
                    text = quote["full_text"]?.asStringOrNull()?.let(parser::decodeEntities).orEmpty(),
                    permalink = "https://$HOST/$quoteHandle/status/" +
                        quote["conversation_id_str"]?.asStringOrNull().orEmpty()
                )
            },
            stats = PostStats(
                replies = obj["reply_count"]?.asIntOrNull(),
                reposts = obj["retweet_count"]?.asIntOrNull(),
                likes = obj["favorite_count"]?.asIntOrNull(),
                views = obj["view_count"]?.asIntOrNull()
            )
        )
    }

    private fun readMedia(element: JsonElement?): List<MediaItem> {
        val media = (element as? JsonObject)?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val thumb = obj["media_url_https"]?.asStringOrNull() ?: return@mapNotNull null
            when (obj["type"]?.asStringOrNull()) {
                "photo" -> MediaItem(thumb, thumb, MediaType.PHOTO)
                "video", "animated_gif" -> {
                    val variants = (obj["video_info"] as? JsonObject)?.get("variants") as? JsonArray
                    val mp4 = variants?.firstOrNull {
                        (it as? JsonObject)?.get("content_type")?.asStringOrNull() == "video/mp4"
                    }?.let { (it as JsonObject)["url"]?.asStringOrNull() }
                    if (mp4 == null) {
                        MediaItem(thumb, thumb, MediaType.PHOTO)
                    } else {
                        MediaItem(
                            previewUrl = thumb,
                            downloadUrl = parser.downloadHostFor(mp4),
                            type = if (obj["type"]?.asStringOrNull() == "animated_gif") {
                                MediaType.GIF
                            } else {
                                MediaType.VIDEO
                            }
                        )
                    }
                }
                else -> null
            }
        }
    }

    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() }?.content
            ?.takeIf { it != "null" && it != "false" }

    private fun JsonElement.asIntOrNull(): Int? = (this as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonElement.asBooleanOrNull(): Boolean? =
        (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    // ---- cursor packing -----------------------------------------------------

    /**
     * The repository only knows about opaque cursor strings, so the user id and
     * page counter this source needs travel inside one.
     */
    private fun encodeCursor(userId: String?, page: Int, token: String?): String? {
        if (userId.isNullOrBlank() || token.isNullOrBlank()) return null
        return "$PREFIX$userId$SEPARATOR$page$SEPARATOR$token"
    }

    private fun decodeCursor(cursor: String): Triple<String, Int, String>? {
        if (!cursor.startsWith(PREFIX)) return null
        val parts = cursor.removePrefix(PREFIX).split(SEPARATOR, limit = 3)
        if (parts.size < 3) return null
        val page = parts[1].toIntOrNull() ?: return null
        return Triple(parts[0], page, parts[2])
    }

    companion object {
        const val HOST = TwstalkerParser.HOST
        const val PREFIX = "tws|"
        private const val SEPARATOR = "|"
        private const val INITIAL_PAGE = 2
        private const val SELECTOR_SET_VERSION = 1

        fun handles(cursor: String?): Boolean = cursor?.startsWith(PREFIX) == true

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
}
