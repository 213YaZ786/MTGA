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
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.core.model.parseVerifiedType
import com.mtga.app.core.text.Entities
import com.mtga.app.data.xcom.SyndicationSource
import com.mtga.app.data.xcom.XPagePayload
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

    /**
     * The text of the post `/2/status/<id>` answers with, or null when the
     * answer holds none. Shape from FxEmbed's API specification, the status
     * object under "status":
     * https://github.com/FxEmbed/FxEmbed/blob/main/docs/specs/fxtwitter-openapi.json
     */
    fun statusText(body: String): String? =
        parse(body)?.obj("status")?.string("text")?.let(Entities::unescape)?.takeIf { it.isNotBlank() }

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
        val decoded = Entities.unescape(obj.string("text").orEmpty())
        // raw_text.display_text_range is counted over raw_text.text, the post as X wrote it, while
        // "text" has its links expanded. The mentions a reply starts with come before any link, so
        // the range's start is the length of the prefix X hides, cut from "text" as Nitter's
        // expandTextEntities cuts its textSlice. Without a range, or when "text" does not start with
        // that prefix, the leading mentions of the account replied to are taken out instead.
        // https://github.com/FxEmbed/FxEmbed/blob/main/docs/specs/fxtwitter-openapi.json
        val rawText = obj.obj("raw_text")
        val hiddenStart = ((rawText?.get("display_text_range") as? JsonArray)?.getOrNull(0) as? JsonPrimitive)
            ?.content?.toIntOrNull()
        val hidden = rawText?.string("text")?.let(Entities::unescape)?.let { raw ->
            raw.substring(0, raw.offsetByCodePoints(0, minOf(hiddenStart ?: 0, raw.codePointCount(0, raw.length))))
        }?.takeIf { it.isNotEmpty() && decoded.startsWith(it) }
        val textSlice = hidden?.let { it.codePointCount(0, it.length)..Int.MAX_VALUE }
        // FxEmbed's author.verification: verified, and its type (organization, government,
        // individual), which Nitter's three marks are named after: business is an organization.
        // https://github.com/FxEmbed/FxEmbed/blob/main/docs/specs/fxtwitter-openapi.json
        val verification = author.obj("verification")
        val kind = verification?.string("type")
        val verified = if ((verification?.get("verified") as? JsonPrimitive)?.content != "true") {
            VerifiedType.NONE
        } else {
            parseVerifiedType(if (kind == "organization") "business" else kind.orEmpty(), VerifiedType.BLUE)
        }
        return Post(
            id = id,
            authorHandle = handle,
            authorName = author.string("name") ?: handle,
            avatarUrl = author.string("avatar_url"),
            text = XPagePayload.expandTextEntities(
                decoded, JsonArray(emptyList()), textSlice,
                if (textSlice == null && hiddenStart == null) listOfNotNull(replyTo) else emptyList(),
                hasRedundantLink = false
            ),
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
            verified = verified,
            isProtected = (author["protected"] as? JsonPrimitive)?.content == "true",
            // FxEmbed's edited_at: a time once the post was edited, null otherwise.
            // https://github.com/FxEmbed/FxEmbed/blob/main/src/types/types.d.ts
            edited = obj.string("edited_at").isNullOrEmpty().not(),
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
        // A quote that cannot be read arrives as a tombstone and used to be dropped.
        if (isTombstone(quote)) return twitterTombstone(quote)
        val author = quote.obj("author") ?: return null
        val handle = author.string("screen_name") ?: return null
        val id = quote.string("id").orEmpty()
        return QuotedPost(
            handle = handle,
            name = author.string("name") ?: handle,
            text = Entities.unescape(quote.string("text").orEmpty()),
            permalink = quote.string("url") ?: "https://x.com/$handle/status/$id",
            note = note(quote["community_note"]),
            media = media(quote.obj("media"))
        )
    }

    // FxEmbed (MIT): an unavailable post is `{ type: 'tombstone', provider, reason, message,
    // ...partial }` (id, url, author), twitterTombstone builds it and the quote slot holds it.
    // Read here as a QuotedPost whose `unavailable` is the message.
    // https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts#L198-L205
    // not ported: the builder side (provider and the spread of partial: MTGA reads the object,
    // it never makes one).
    private fun twitterTombstone(partial: JsonObject): QuotedPost {
        val reason = partial.string("reason")
        val message = partial.string("message") ?: tombstoneMessageForReason(reason)
        val author = partial.obj("author")
        val id = partial.string("id")
        return QuotedPost(
            handle = author?.string("screen_name").orEmpty(),
            name = author?.string("name").orEmpty(),
            text = "",
            permalink = partial.string("url") ?: id?.let { "https://x.com/i/status/$it" }.orEmpty(),
            unavailable = message
        )
    }

    // isTombstone, fallbackMessageEn and tombstoneMessageForReason (English only).
    // https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/helpers/tombstone.ts#L12-L47
    // not ported: tombstoneKey and the `t` translator (the app's texts are its own),
    // stripTombstones and stripQuotesDeep (they remove tombstones, MTGA shows them).
    private fun isTombstone(x: JsonObject): Boolean = x.string("type") == "tombstone"

    private fun tombstoneMessageForReason(reason: String?): String = when (reason) {
        "deleted" -> "This post is deleted"
        "suspended" -> "This post is from a suspended account"
        "private" -> "This post is from a private account"
        "blocked" -> "This post has been blocked from view by its author"
        else -> "This post is unavailable"
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
                // FxTwitter gives each photo and video its width and height (its API spec
                // requires them), which PostCard draws the picture at.
                "photo" -> MediaItem(
                    previewUrl = url.substringBefore('?'),
                    downloadUrl = url,
                    type = MediaType.PHOTO,
                    width = item.int("width"),
                    height = item.int("height"),
                    // APIPhoto.altText, https://github.com/FxEmbed/FxEmbed/blob/main/src/types/types.d.ts
                    altText = item.string("altText")?.takeIf { it.isNotBlank() }
                )
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
                        durationLabel = if (gif) null else item.double("duration")?.let(::duration),
                        width = item.int("width"),
                        height = item.int("height"),
                        altText = item.string("altText")?.takeIf { it.isNotBlank() }
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
        return CommunityNote(text = Entities.unescape(text))
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
