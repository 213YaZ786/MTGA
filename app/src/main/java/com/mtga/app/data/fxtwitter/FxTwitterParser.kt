package com.mtga.app.data.fxtwitter

import com.mtga.app.core.model.CommunityNote
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.LinkCard
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Poll
import com.mtga.app.core.model.PollOption
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.data.xcom.SyndicationSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * FxTwitter's JSON (api.fxtwitter.com, version 2) as MTGA's posts. Pure,
 * tested against answers saved in October 2026.
 *
 * A timeline item is a post as X shows it on the profile: a repost is the
 * original post, its author the original author, with "reposted_by" naming
 * the account; Nitter draws reposts the same way. "text" is already whole
 * (long posts included) with links expanded and the post's own media links
 * removed.
 */
object FxTwitterParser {

    data class Page(val posts: List<Post>, val bottomCursor: String?)

    data class Profile(val name: String?, val avatarUrl: String?, val bio: String?, val bannerUrl: String?)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A page of an account's timeline, or null when the answer is not one. */
    fun timeline(body: String): Page? {
        val root = parse(body) ?: return null
        val results = root["results"] as? JsonArray ?: return null
        val posts = results.mapNotNull { (it as? JsonObject)?.let(::post) }
        val bottom = (root["cursor"] as? JsonObject)?.string("bottom")
        return Page(posts, bottom)
    }

    /** The profile card from the author block of [body]'s first own post. */
    fun profile(body: String, handle: String): Profile? {
        val root = parse(body) ?: return null
        val author = (root["results"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.obj("author") }
            ?.firstOrNull { it.string("screen_name").equals(handle, ignoreCase = true) }
            ?: root.obj("user")
            ?: return null
        return Profile(
            name = author.string("name"),
            avatarUrl = author.string("avatar_url"),
            bio = author.string("description"),
            bannerUrl = author.string("banner_url")
        )
    }

    /**
     * A post's surroundings: "thread" holds the posts above it and the
     * author's own continuation below, "replies" the answers, each shown as
     * its own group.
     */
    fun conversation(body: String, host: String): Conversation? {
        val root = parse(body) ?: return null
        val main = root.obj("status")?.let(::post) ?: return null
        val thread = (root["thread"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::post) }
        val at = thread.indexOfFirst { it.id == main.id }
        val replies = (root["replies"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::post) }
            .filter { it.id != main.id }
        return Conversation(
            ancestors = if (at >= 0) thread.take(at) else thread.filter { it.publishedAtMillis < main.publishedAtMillis },
            main = main,
            continuation = if (at >= 0) thread.drop(at + 1) else thread.filter { it.publishedAtMillis > main.publishedAtMillis },
            replies = replies.map { listOf(it) },
            host = host
        )
    }

    fun post(obj: JsonObject): Post? {
        val id = obj.string("id")?.takeIf { it.all(Char::isDigit) } ?: return null
        val author = obj.obj("author") ?: return null
        val handle = author.string("screen_name") ?: return null
        val repostedBy = obj.obj("reposted_by")?.string("screen_name")
        val replyTo = obj.obj("replying_to")?.string("screen_name")
        val quote = obj.obj("quote")
        return Post(
            id = id,
            authorHandle = handle,
            authorName = author.string("name") ?: handle,
            avatarUrl = author.string("avatar_url"),
            text = obj.string("text").orEmpty(),
            links = links(obj),
            publishedAtMillis = obj.long("created_timestamp")?.times(1000) ?: SyndicationSource.snowflakeToMillis(id),
            permalink = obj.string("url") ?: "https://x.com/$handle/status/$id",
            kind = when {
                repostedBy != null -> PostKind.REPOST
                replyTo != null -> PostKind.REPLY
                quote != null -> PostKind.QUOTE
                else -> PostKind.ORIGINAL
            },
            relatedHandle = repostedBy ?: replyTo,
            media = media(obj.obj("media")),
            quoted = quote?.let(::quoted),
            card = card(obj),
            poll = poll(obj.obj("poll")),
            note = note(obj["community_note"]),
            stats = PostStats(
                replies = obj.int("replies"),
                reposts = obj.int("reposts"),
                likes = obj.int("likes"),
                views = obj.int("views")
            )
        )
    }

    private fun quoted(quote: JsonObject): QuotedPost? {
        val author = quote.obj("author") ?: return null
        val handle = author.string("screen_name") ?: return null
        val id = quote.string("id").orEmpty()
        return QuotedPost(
            handle = handle,
            name = author.string("name") ?: handle,
            text = quote.string("text").orEmpty(),
            permalink = quote.string("url") ?: "https://x.com/$handle/status/$id",
            note = note(quote["community_note"])
        )
    }

    /** The links the author typed, already expanded, in order. */
    private fun links(obj: JsonObject): List<String> =
        (obj.obj("raw_text")?.get("facets") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("type") == "url" }
            .mapNotNull { it.string("replacement") ?: it.string("original") }
            .distinct()

    private fun media(media: JsonObject?): List<MediaItem> =
        (media?.get("all") as? JsonArray).orEmpty().mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val url = item.string("url") ?: return@mapNotNull null
            when (item.string("type")) {
                // The original is asked by name; the plain address is the
                // lighter version a list shows.
                "photo" -> MediaItem(previewUrl = url.substringBefore('?'), downloadUrl = url, type = MediaType.PHOTO)
                "video", "gif" -> {
                    val gif = item.string("type") == "gif"
                    val best = (item["formats"] as? JsonArray).orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .filter { it.string("container") == "mp4" }
                        .maxByOrNull { it.long("bitrate") ?: 0L }
                        ?.string("url")
                    MediaItem(
                        previewUrl = item.string("thumbnail_url") ?: url,
                        downloadUrl = best ?: url,
                        type = if (gif) MediaType.GIF else MediaType.VIDEO,
                        durationLabel = if (gif) null else item.double("duration")?.let(::duration)
                    )
                }
                else -> null
            }
        }

    private fun card(obj: JsonObject): LinkCard? {
        val card = obj.obj("card") ?: return null
        val title = card.string("title") ?: return null
        return LinkCard(
            title = title,
            description = card.string("description"),
            destination = card.string("domain"),
            imageUrl = card.obj("image")?.string("url"),
            url = card.string("url"),
            large = obj.string("embed_card") != "summary"
        )
    }

    private fun poll(poll: JsonObject?): Poll? {
        val choices = (poll?.get("choices") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (choices.isEmpty()) return null
        val percents = choices.map { it.double("percentage")?.toInt() ?: 0 }
        val top = percents.maxOrNull() ?: 0
        return Poll(
            options = choices.mapIndexed { i, choice ->
                PollOption(label = choice.string("label").orEmpty(), percent = percents[i], leader = top > 0 && percents[i] == top)
            },
            votes = poll?.long("total_votes"),
            status = poll?.string("time_left_en")
        )
    }

    private fun note(element: JsonElement?): CommunityNote? {
        val note = element as? JsonObject ?: return null
        val text = note.string("text") ?: return null
        return CommunityNote(text = text)
    }

    private fun duration(seconds: Double): String {
        val whole = seconds.toLong()
        return String.format(Locale.US, "%d:%02d", whole / 60, whole % 60)
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
