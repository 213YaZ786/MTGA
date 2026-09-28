package com.mtga.app.data.xcom

import com.mtga.app.core.model.LinkCard
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Reads the posts of a logged out x.com profile page straight out of the page.
 *
 * Since September 2026 the page carries its own timeline. The server runs the
 * UserOriginalsTimeline query while rendering and streams the answer inline,
 * in the same GraphQL shape the logged in app gets: text, long text, media
 * with every video bitrate, counts, cards. Reading it here replaces one
 * syndication request per post with nothing at all.
 *
 * The stream is written by Seroval, which serialises to JavaScript rather than
 * JSON: bare keys, `!0` for true, `$R[n]=` in front of every object so later
 * parts can refer back to it. [JsLiteral] turns one streamed record into JSON
 * and kotlinx does the rest.
 *
 * Posts are found by walking for TimelineTweet items rather than by following
 * the path from the query root, so X renaming a wrapper on the way down costs
 * nothing as long as the items themselves keep their type name.
 */
internal object XPagePayload {

    /** Every post of the page's timeline, in page order, pinned one included. */
    fun posts(page: String): List<Post> {
        val found = LinkedHashMap<String, Post>()
        for (record in completedRecords(page)) {
            val root = runCatching { json.parseToJsonElement(JsLiteral.toJson(record)) }.getOrNull()
                ?: continue
            walk(root) { item ->
                val post = timelineItem(item) ?: return@walk
                found.putIfAbsent(post.id, post)
            }
        }
        return found.values.toList()
    }

    /**
     * The source text of every completed GraphQL answer on the page. Each one
     * is an object literal opening right before its kind field.
     */
    private fun completedRecords(page: String): List<String> {
        val records = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = page.indexOf(COMPLETED_MARKER, from)
            if (at < 0) break
            from = at + COMPLETED_MARKER.length
            val open = at - 1
            if (open < 0 || page[open] != '{') continue
            val end = JsLiteral.endOfNested(page, open) ?: continue
            records += page.substring(open, end)
            from = end
        }
        return records
    }

    private fun walk(element: JsonElement, visit: (JsonObject) -> Unit) {
        when (element) {
            is JsonObject -> {
                visit(element)
                element.values.forEach { walk(it, visit) }
            }
            is JsonArray -> element.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun timelineItem(item: JsonObject): Post? {
        if (item.string("__typename") != "TimelineTweet") return null
        val pinned = item.obj("social_context")?.string("context_type") == "Pin"
        val tweet = unwrap(item.obj("tweet_results")?.obj("result")) ?: return null
        return toPost(tweet, pinned)
    }

    /** Posts under a visibility notice arrive wrapped one level deeper. */
    private fun unwrap(result: JsonObject?): JsonObject? = when (result?.string("__typename")) {
        null -> null
        "TweetWithVisibilityResults" -> result.obj("tweet")
        "Tweet" -> result
        else -> null
    }

    private fun toPost(tweet: JsonObject, pinned: Boolean): Post? {
        val id = tweet.string("rest_id") ?: return null
        val user = tweet.obj("core")?.obj("user_results")?.obj("result")
        val handle = user?.obj("core")?.string("screen_name") ?: return null
        val details = tweet.obj("details")
        val media = media(tweet.array("media_entities2"))
        val urls = tweet.array("url_entities").orEmpty()
        val note = tweet.obj("note_tweet")?.obj("note_tweet_results")?.obj("result")
        val noteText = note?.string("text")
        val raw = noteText ?: details?.string("full_text").orEmpty()
        val noteUrls = note?.obj("entity_set")?.array("urls").orEmpty()
        val allUrls = urls + noteUrls
        val quoted = quoted(tweet)

        return Post(
            id = id,
            authorHandle = handle,
            authorName = user.obj("core")?.string("name") ?: handle,
            avatarUrl = user.obj("avatar")?.string("image_url"),
            text = readableText(raw, allUrls, hasAttachment = media.isNotEmpty() || quoted != null),
            links = allUrls.mapNotNull { (it as? JsonObject)?.string("expanded_url") }.distinct(),
            publishedAtMillis = details?.long("created_at_ms")
                ?: SyndicationSource.snowflakeToMillis(id),
            permalink = "https://x.com/$handle/status/$id",
            kind = if (quoted != null) PostKind.QUOTE else PostKind.ORIGINAL,
            isPinned = pinned,
            media = media,
            quoted = quoted,
            card = card(tweet.obj("card"), urls),
            stats = tweet.obj("counts")?.let { counts ->
                PostStats(
                    replies = counts.int("reply_count"),
                    reposts = counts.int("retweet_count"),
                    likes = counts.int("favorite_count"),
                    views = tweet.obj("views")?.int("count")
                )
            }
        )
    }

    /**
     * The post text as a reader sees it. t.co links the author typed become
     * their display form, which [com.mtga.app.feature.post.linkify] matches
     * back to the real address in [Post.links]. The last t.co link, when it
     * only points at the post's own media or quote, is dropped, as x.com
     * itself does.
     */
    internal fun readableText(raw: String, urls: List<JsonElement>, hasAttachment: Boolean): String {
        var text = raw.trimEnd()
        val shown = urls.mapNotNull { it as? JsonObject }
        val trailing = TRAILING_TCO.find(text)
        if (trailing != null && hasAttachment) {
            val link = trailing.value.trim()
            val typed = shown.firstOrNull { it.string("url") == link }
            // A link to another post's media counts as the attachment too:
            // it is what the video under the text shows.
            val pointsAtMedia = typed?.string("expanded_url")
                ?.let { MEDIA_PATH.containsMatchIn(it) } ?: true
            if (pointsAtMedia) text = text.substring(0, trailing.range.first).trimEnd()
        }
        for (entity in shown) {
            val short = entity.string("url") ?: continue
            val display = entity.string("display_url") ?: continue
            text = text.replace(short, display)
        }
        return text
    }

    private fun media(entities: JsonArray?): List<MediaItem> = entities.orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val still = obj.string("media_url_https") ?: return@mapNotNull null
        when (obj.string("type")) {
            "photo" -> MediaItem(still, "$still?name=orig", MediaType.PHOTO)
            "video", "animated_gif" -> {
                val best = obj.obj("video_info")?.array("variants").orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { it.string("content_type") == "video/mp4" }
                    .maxByOrNull { it.long("bitrate") ?: 0L }
                    ?.string("url")
                val gif = obj.string("type") == "animated_gif"
                MediaItem(
                    previewUrl = still,
                    downloadUrl = best ?: still,
                    type = if (gif) MediaType.GIF else MediaType.VIDEO,
                    durationLabel = if (gif) null else obj.obj("video_info")?.long("duration_millis")?.let(::duration)
                )
            }
            else -> null
        }
    }

    /**
     * No quote was in the pages this was written from, so the key is not
     * known for certain. Any field named like a quote that holds a post is
     * taken, which covers the spellings X has used elsewhere.
     */
    private fun quoted(tweet: JsonObject): QuotedPost? {
        val holder = tweet.entries.firstOrNull { (key, value) ->
            key.startsWith("quoted") && value is JsonObject
        }?.value as? JsonObject ?: return null
        val result = unwrap(holder.obj("result") ?: holder) ?: return null
        val handle = result.obj("core")?.obj("user_results")?.obj("result")
            ?.obj("core")?.string("screen_name") ?: return null
        val id = result.string("rest_id") ?: return null
        val text = result.obj("note_tweet")?.obj("note_tweet_results")?.obj("result")?.string("text")
            ?: result.obj("details")?.string("full_text").orEmpty()
        return QuotedPost(
            handle = handle,
            name = result.obj("core")?.obj("user_results")?.obj("result")
                ?.obj("core")?.string("name") ?: handle,
            text = text,
            permalink = "https://x.com/$handle/status/$id"
        )
    }

    /** Only cards the page describes. Most arrive as a bare id and stay null. */
    private fun card(card: JsonObject?, urls: List<JsonElement>): LinkCard? {
        val legacy = card?.obj("legacy") ?: return null
        val values = legacy.array("binding_values").orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy({ it.string("key").orEmpty() }, { it.obj("value") })
        fun text(key: String) = values[key]?.string("string_value")
        fun image(key: String) = values[key]?.obj("image_value")?.string("url")
        val title = text("title") ?: return null
        val short = text("card_url") ?: legacy.string("url")
        val expanded = urls.mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("url") == short }?.string("expanded_url")
        return LinkCard(
            title = title,
            description = text("description"),
            destination = text("vanity_url") ?: text("domain"),
            imageUrl = image("thumbnail_image_large") ?: image("thumbnail_image_original")
                ?: image("summary_photo_image_large") ?: image("photo_image_full_size_large"),
            url = expanded ?: short,
            large = legacy.string("name").orEmpty().contains("large")
        )
    }

    private fun duration(millis: Long): String {
        val seconds = millis / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
    private fun JsonObject.int(key: String): Int? =
        long(key)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()

    private const val COMPLETED_MARKER = "kind:\"GraphQLRequestStream.Completed\""

    private val TRAILING_TCO = Regex("\\s*https://t\\.co/\\w+\\s*$")
    private val MEDIA_PATH = Regex("/(photo|video)/\\d+$")

    private val json = Json { ignoreUnknownKeys = true }
}

/**
 * Turns a JavaScript object literal as Seroval writes it into JSON.
 *
 * Only what Seroval emits for plain data is handled: bare keys, `!0` and
 * `!1`, `void 0`, `$R[n]=` in front of a value, and `$R[n]` alone for a value
 * already written elsewhere, which becomes null. Anything else, a Map or a
 * Promise for instance, makes the result invalid JSON, and the caller treats
 * that record as unreadable rather than guessing.
 */
internal object JsLiteral {

    fun toJson(source: String): String {
        val out = StringBuilder(source.length + source.length / 8)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '"' -> i = copyString(source, i, out)
                source.startsWith("\$R[", i) -> {
                    val close = source.indexOf(']', i)
                    if (close < 0) return out.toString()
                    if (source.getOrNull(close + 1) == '=') {
                        i = close + 2
                    } else {
                        out.append("null")
                        i = close + 1
                    }
                }
                source.startsWith("!0", i) -> { out.append("true"); i += 2 }
                source.startsWith("!1", i) -> { out.append("false"); i += 2 }
                source.startsWith("void 0", i) -> { out.append("null"); i += 6 }
                c.isLetter() || c == '_' || c == '$' -> {
                    var end = i + 1
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_' || source[end] == '$')) end++
                    val word = source.substring(i, end)
                    var next = end
                    while (next < source.length && source[next].isWhitespace()) next++
                    when {
                        source.getOrNull(next) == ':' -> out.append('"').append(word).append('"')
                        word == "true" || word == "false" || word == "null" -> out.append(word)
                        else -> out.append("null")
                    }
                    i = end
                }
                c.isDigit() || c == '-' -> {
                    var end = i + 1
                    while (end < source.length && (source[end].isDigit() || source[end] in ".eE+-")) end++
                    out.append(source, i, end)
                    // A BigInt literal ends in n, JSON has no such thing.
                    i = if (source.getOrNull(end) == 'n') end + 1 else end
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /**
     * Copies the string literal at [quote] and returns the index after it.
     * JavaScript allows \x and \' escapes that JSON does not, so those two
     * are rewritten on the way.
     */
    private fun copyString(source: String, quote: Int, out: StringBuilder): Int {
        out.append('"')
        var i = quote + 1
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' && source.getOrNull(i + 1) == 'x' -> {
                    out.append("\\u00").append(source, i + 2, minOf(i + 4, source.length))
                    i += 4
                }
                c == '\\' && source.getOrNull(i + 1) == '\'' -> { out.append('\''); i += 2 }
                c == '\\' -> {
                    out.append(c)
                    source.getOrNull(i + 1)?.let(out::append)
                    i += 2
                }
                c == '"' -> { out.append('"'); return i + 1 }
                c == '\n' -> { out.append("\\n"); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return i
    }

    /**
     * Index just past the object or array opened at [open], or null when it
     * never closes. Strings are skipped whole so braces inside them do not
     * count.
     */
    fun endOfNested(source: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '"' -> {
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                }
                '{', '[' -> depth++
                '}', ']' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return null
    }
}
