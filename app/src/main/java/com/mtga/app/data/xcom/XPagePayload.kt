package com.mtga.app.data.xcom

import com.mtga.app.core.model.LinkCard
import com.mtga.app.core.model.MediaItem
import com.mtga.app.core.model.MediaType
import com.mtga.app.core.model.Post
import com.mtga.app.core.model.Poll
import com.mtga.app.core.model.PollOption
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.model.PostStats
import com.mtga.app.core.model.QuotedPost
import com.mtga.app.core.model.VerifiedType
import com.mtga.app.core.model.parseVerifiedType
import com.mtga.app.core.text.Entities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Nitter's Slice[int]: the first and last index of a display range, in code points. */
internal typealias Slice = IntRange

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
        val roots = completedRecords(page)
            .mapNotNull { record -> runCatching { json.parseToJsonElement(JsLiteral.toJson(record)) }.getOrNull() } +
            jsonTextResults(page)
        for (root in roots) {
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

    /**
     * The answers the page streams as `kind:"Result.Ok",value:"<json text>"`.
     * Since the end of October 2026 x.com writes each GraphQL answer this way,
     * the whole answer as one escaped JSON string and no longer as a Seroval
     * object under a Completed record, so [completedRecords] found nothing and
     * the head fell back to one syndication request per post, which carries
     * no pin. The string is a JavaScript literal as Seroval escapes it
     * (https://github.com/lxsmnsyc/seroval, MIT, packages/seroval/src/core/string.ts):
     * [JsLiteral] turns it into a JSON string, whose text is the answer.
     */
    private fun jsonTextResults(page: String): List<JsonElement> {
        val answers = mutableListOf<JsonElement>()
        var from = 0
        while (true) {
            val at = page.indexOf(RESULT_TEXT_MARKER, from)
            if (at < 0) break
            val quote = at + RESULT_TEXT_MARKER.length - 1
            val end = JsLiteral.endOfString(page, quote) ?: break
            from = end
            val literal = runCatching { json.parseToJsonElement(JsLiteral.toJson(page.substring(quote, end))) }
                .getOrNull() as? JsonPrimitive
            val text = literal?.takeIf { it.isString }?.content ?: continue
            runCatching { json.parseToJsonElement(text) }.getOrNull()?.let(answers::add)
        }
        return answers
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
        if (item.string("__typename") == "TimelinePinEntry" || item.string("type") == "TimelinePinEntry") {
            return pinEntry(item)
        }
        if (item.string("__typename") != "TimelineTweet") return null
        val pinned = item.obj("social_context")?.string("context_type") == "Pin"
        val tweet = unwrap(item.obj("tweet_results")?.obj("result")) ?: return null
        return toPost(tweet, pinned)
    }

    /**
     * The pinned post of a TimelinePinEntry, read the way Squawker does in
     * createProfileUnconversationedChainsGraphql
     * (https://github.com/j-fbriere/squawker/blob/master/lib/client/client.dart#L1010-L1040):
     * the three places where X has put the tweet result, tried in that order,
     * then the `tweet` inside it when the result has no rest_id (a visibility
     * wrapper). A renamed wrapper on the way down costs nothing as long as one
     * of the three paths still holds the result.
     */
    private fun pinEntry(pinEntry: JsonObject): Post? {
        var result: JsonObject? = pinEntry.obj("entry")?.obj("content")?.obj("content")?.obj("tweetResult")?.obj("result")
        result = result ?: pinEntry.obj("entry")?.obj("content")?.obj("itemContent")?.obj("tweet_results")?.obj("result")
        result = result ?: pinEntry.obj("entry")?.obj("content")?.obj("content")?.obj("tweet_results")?.obj("result")
        if (result != null) {
            result = if (result["rest_id"] != null) result else result.obj("tweet")
            if (result != null) return toPost(result, pinned = true)
        }
        return null
    }

    /**
     * True when the post carries a note_tweet (a long post) without the note's
     * text. Taken from FxEmbed (MIT), where a long post is the one whose
     * note_tweet.note_tweet_results.result exists and its text is that result's
     * text: https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts
     */
    private fun isCutNote(tweet: JsonObject): Boolean {
        val note = tweet["note_tweet"] as? JsonObject ?: return false
        return note.obj("note_tweet_results")?.obj("result")?.string("text") == null
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
        val note = tweet.obj("note_tweet")?.obj("note_tweet_results")?.obj("result")
        val noteText = note?.string("text")
        // X escapes &, < and > in the legacy text and numbers its entity indices over the decoded
        // text, so the text is decoded first, once, by the shared decoder ported from jsoup.
        val raw = Entities.unescape(noteText ?: details?.string("full_text").orEmpty())
        // A note tweet whose text is missing is a long post given cut (FxEmbed's
        // processor.ts takes the text of note_tweet_results.result, else only
        // the cut legacy text): marked so the completion does not guess by length.
        val cut = isCutNote(tweet)
        // When the note has its text, its entity_set replaces the legacy
        // entities, as FxEmbed does, because the legacy offsets and links
        // belong to the cut text. Only the links are read here, so the links of
        // a long post expand through expandTextEntities.
        // https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts#L517-L537
        val urls = if (noteText != null) {
            note?.obj("entity_set")?.array("urls").orEmpty()
        } else {
            tweet.array("url_entities").orEmpty()
        }
        val quoted = quoted(tweet)
        val poll = parsePoll(tweet.obj("card"))
        // AGPL-3.0, ported from Nitter, parseGraphUser: is_blue_verified (or the same flag inside
        // verification) makes the author blue, then verification.verified_type names the type;
        // parseUser reads protected, or privacy.protected.
        // https://github.com/zedeus/nitter/blob/master/src/parser.nim#parseGraphUser
        fun flag(element: JsonElement?) = (element as? JsonPrimitive)?.content == "true"
        val verification = user.obj("verification")
        val blue = flag(user["is_blue_verified"]) || flag(verification?.get("is_blue_verified"))
        val verified = parseVerifiedType(
            verification?.string("verified_type").orEmpty(),
            if (blue) VerifiedType.BLUE else VerifiedType.NONE
        )
        // https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#expandTweetEntitiesV2
        // textRange = js{"details", "display_text_range"}, textSlice = textRange{0}.getInt .. textRange{1}.getInt
        val textRange = details?.array("display_text_range")
        val displayRange = textRange?.let { range ->
            val first = (range.getOrNull(0) as? JsonPrimitive)?.content?.toIntOrNull()
            val last = (range.getOrNull(1) as? JsonPrimitive)?.content?.toIntOrNull()
            if (first != null && last != null) first..last else null
        }

        return Post(
            id = id,
            authorHandle = handle,
            authorName = user.obj("core")?.string("name") ?: handle,
            avatarUrl = user.obj("avatar")?.string("image_url"),
            // A note tweet already leaves out the mentions before its text, so only the legacy text
            // has a display range (Nitter's expandTweetEntitiesV2 reads details.display_text_range).
            text = expandTextEntities(
                raw, JsonArray(urls), if (noteText == null) displayRange else null, emptyList(),
                hasRedundantLink = media.isNotEmpty() || quoted != null
            ),
            links = urls.mapNotNull { (it as? JsonObject)?.string("expanded_url") }.distinct(),
            publishedAtMillis = details?.long("created_at_ms")
                ?: SyndicationSource.snowflakeToMillis(id),
            permalink = "https://x.com/$handle/status/$id",
            kind = if (quoted != null) PostKind.QUOTE else PostKind.ORIGINAL,
            isPinned = pinned,
            verified = verified,
            isProtected = flag(user["protected"]) || flag(user.obj("privacy")?.get("protected")),
            textCut = cut,
            media = media,
            quoted = quoted,
            // Nitter reads a card named like a poll as the poll and any other as a link card.
            card = if (poll != null) null else card(tweet.obj("card"), urls),
            poll = poll,
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

    // AGPL-3.0, ported from Nitter, expandTextEntities: the post's text cut to its display range
    // (textSlice, counted in code points as the runes of replacedWith: safeStart, safeEnd), the
    // t.co links the author typed (urls) replaced by their display form, which
    // [com.mtga.app.feature.post.linkify] matches back to the real address in [Post.links], and
    // the last t.co link dropped when it only points at the post's own media or quote
    // (hasRedundantLink, extractUrls' hideTwitter), as x.com itself does. Its user_mentions loop
    // is kept: a mention that starts before the display range is hidden. Where a source gives no
    // display range, the mentions the text starts with are read as the user_mentions, since X
    // puts the replied accounts first, and one of them being replyTo hides them all.
    // https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#expandTextEntities
    // not ported: hasCard and the rewrite of the card's url, hasArticle, the media entities, the
    // hashtags and symbols (replacements of kind rkHashtag, the post card colours them in
    // linkify), the mentions' own links (rkMention), replacements.deduplicate and sort, and
    // tweet.reply (the "Replying to" names come from the source's own field).
    internal fun expandTextEntities(
        text: String,
        entities: JsonArray,
        textSlice: Slice?,
        replyTo: List<String>,
        hasRedundantLink: Boolean
    ): String {
        var slice = textSlice
        if (slice == null && replyTo.isNotEmpty()) {
            val mention = Regex("""\s*@(\w{1,15})""")
            var idx = 0
            var name = false
            while (true) {
                val mentions = mention.matchAt(text, idx) ?: break
                if (replyTo.any { it.equals(mentions.groupValues[1], ignoreCase = true) }) name = true
                idx = mentions.range.last + 1
            }
            while (name && idx < text.length && text[idx].isWhitespace()) idx++
            if (name) slice = idx..Int.MAX_VALUE
        }
        val runes = text.codePoints().toArray()
        val runeLen = runes.size
        val safeStart = maxOf(0, slice?.first ?: 0)
        val safeEnd = minOf(runeLen, slice?.last ?: runeLen)
        var result = if (safeStart < safeEnd) String(runes, safeStart, safeEnd - safeStart) else ""
        result = result.trimEnd()

        val urls = entities.mapNotNull { it as? JsonObject }
        val trailing = TRAILING_TCO.find(result)
        if (trailing != null && hasRedundantLink) {
            val url = trailing.value.trim()
            val u = urls.find { it.string("url") == url }
            // A link to another post's media counts as the attachment too:
            // it is what the video under the text shows.
            val pointsAtMedia = u?.string("expanded_url")
                ?.let { MEDIA_PATH.containsMatchIn(it) } ?: true
            if (pointsAtMedia) result = result.substring(0, trailing.range.first).trimEnd()
        }
        for (u in urls) {
            val urlStr = u.string("url") ?: continue
            val display = u.string("display_url") ?: continue
            if (urlStr.isEmpty() || urlStr !in result) continue
            result = result.replace(urlStr, display)
        }
        return result
    }

    // AGPL-3.0, ported from Nitter, parseLegacyMediaEntities: the media of a tweet by type,
    // "photo", "video", "animated_gif", each with its url and thumb (media_url_https).
    // https://github.com/zedeus/nitter/blob/master/src/parser.nim#L313-L331
    // ext_alt_text is read for the three types (a photo's and a gif's altText, a video's title)
    // and given to TalkBack, never drawn.
    // not ported: addMedia (a list add), the video's
    // additional_media_info.source_user and attribution link (no attribution shown), the gif's
    // video_info.variants[0] (the best mp4 is taken for a gif as for a video).
    // The size is added, read as the post's own: original_info, else Squawker's sizes.large
    // (largestAspectRatio), else the video's aspect_ratio (fromMedia).
    private fun media(entities: JsonArray?): List<MediaItem> = entities.orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val still = obj.string("media_url_https") ?: return@mapNotNull null
        val original = obj.obj("original_info")
        val large = largestAspectRatio(listOf(obj))
        val aspect = fromMedia(obj).aspectRatio?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
        val size = listOf(
            original?.int("width") to original?.int("height"),
            large?.int("w") to large?.int("h"),
            aspect?.getOrNull(0) to aspect?.getOrNull(1)
        ).firstNotNullOfOrNull { (w, h) -> if (w != null && h != null && w > 0 && h > 0) w to h else null }
        val alt = obj.string("ext_alt_text")?.takeIf { it.isNotBlank() }
        when (obj.string("type")) {
            "photo" -> MediaItem(still, "$still?name=orig", MediaType.PHOTO, width = size?.first, height = size?.second, altText = alt)
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
                    durationLabel = if (gif) null else obj.obj("video_info")?.long("duration_millis")?.let(::duration),
                    width = size?.first,
                    height = size?.second,
                    altText = alt
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
        // A quote X will not show comes as a TweetTombstone (or TweetUnavailable, with no text):
        // it was dropped before and is kept as an unavailable quote.
        val gone = holder.obj("result") ?: holder
        if (gone.string("__typename") == "TweetTombstone") {
            return QuotedPost("", "", "", "", unavailable = tombstone(gone.obj("tombstone")))
        }
        if (gone.string("__typename") == "TweetUnavailable") {
            return QuotedPost("", "", "", "", unavailable = tombstone(null))
        }
        val result = unwrap(gone) ?: return null
        val handle = result.obj("core")?.obj("user_results")?.obj("result")
            ?.obj("core")?.string("screen_name") ?: return null
        val id = result.string("rest_id") ?: return null
        val text = Entities.unescape(
            result.obj("note_tweet")?.obj("note_tweet_results")?.obj("result")?.string("text")
                ?: result.obj("details")?.string("full_text").orEmpty()
        )
        return QuotedPost(
            handle = handle,
            name = result.obj("core")?.obj("user_results")?.obj("result")
                ?.obj("core")?.string("name") ?: handle,
            text = text,
            permalink = "https://x.com/$handle/status/$id",
            textCut = isCutNote(result),
            media = media(result.array("media_entities2"))
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

    // AGPL-3.0, ported from Nitter, parsePoll: the card is named pollNchoice_*, each choice has a
    // choiceN_label and a choiceN_count in its binding values, the poll ends at end_datetime_utc,
    // the leader is the index of the largest value and the votes are the values' sum. X's own
    // counts_are_final is read too, which ends the poll before its time. The percentage of an
    // option is renderPoll's perc, the value over the votes.
    // https://github.com/zedeus/nitter/blob/master/src/parser.nim#parsePoll
    // https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPoll
    // not ported: getStrVal's default argument as a template (read inline), the poll image card's
    // photo (jsCard image_large of a "poll...image" card, the post card has no place for it), the
    // status's text for a poll with a time left (`$(time - now())` up to the first comma, written
    // here as the largest unit).
    private fun parsePoll(js: JsonObject?): Poll? {
        val legacy = js?.obj("legacy") ?: return null
        val name = legacy.string("name").orEmpty()
        if ("poll" !in name || name.length < 5) return null
        val vals = legacy.array("binding_values").orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy({ it.string("key").orEmpty() }, { it.obj("value") })

        val values = mutableListOf<Long>()
        val options = mutableListOf<String>()
        // name format is pollNchoice_*
        for (i in '1'..name[4]) {
            val choice = "choice$i"
            values.add(vals[choice + "_count"]?.string("string_value")?.toLongOrNull() ?: 0L)
            options.add(vals[choice + "_label"]?.string("string_value").orEmpty())
        }
        if (options.isEmpty()) return null

        val time = vals["end_datetime_utc"]?.string("string_value")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val final = (vals["counts_are_final"]?.get("boolean_value") as? JsonPrimitive)?.content == "true"
        val status = if (time != null && time.isAfter(Instant.now()) && !final) {
            val timeLeft = Duration.between(Instant.now(), time)
            when {
                timeLeft.toDays() > 0 -> timeLeft.toDays().let { "$it day" + if (it == 1L) "" else "s" }
                timeLeft.toHours() > 0 -> timeLeft.toHours().let { "$it hour" + if (it == 1L) "" else "s" }
                else -> timeLeft.toMinutes().let { "$it minute" + if (it == 1L) "" else "s" }
            }
        } else {
            "Final results"
        }

        val votes = values.sum()
        val leader = values.indexOf(values.max())
        return Poll(
            options = List(options.size) { i ->
                val perc = if (values[i] > 0) values[i] * 100.0 / votes else 0.0
                PollOption(options[i], Math.round(perc).toInt(), votes > 0 && i == leader)
            },
            votes = votes,
            status = status
        )
    }

    // Squawker (MIT), TweetVideoMetadata.fromMedia: aspectRatio, variants and imageUrl of media.
    // https://github.com/j-fbriere/squawker/blob/master/lib/tweet/_video.dart#L75-L79
    // not ported: the 1.0 aspectRatio default (null, PostCard has its own), the division
    // (the pair is kept and divided in PostCard), streamUrlsBuilderFromVariants (the best
    // mp4 of variants is picked in media).
    private class TweetVideoMetadata(val aspectRatio: JsonArray?, val imageUrl: String?, val variants: JsonArray?)

    private fun fromMedia(media: JsonObject): TweetVideoMetadata {
        val videoInfo = media.obj("video_info")
        val aspectRatio = videoInfo?.array("aspect_ratio")
        val variants = videoInfo?.array("variants")
        val imageUrl = media.string("media_url_https")
        return TweetVideoMetadata(aspectRatio, imageUrl, variants)
    }

    private fun duration(millis: Long): String {
        val seconds = millis / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    // Squawker (MIT), TweetMedia: largestAspectRatio, the media's sizes.large.w over
    // sizes.large.h, the largest (reduce(math.max)), each `?? 1` as in the source. It
    // returns that large size (its w and h) and not the quotient: PostCard divides.
    // https://github.com/j-fbriere/squawker/blob/master/lib/tweet/_media.dart#L143
    private fun largestAspectRatio(media: List<JsonObject>): JsonObject? = media
        .mapNotNull { e -> e.obj("sizes")?.obj("large") }
        .maxByOrNull { (it.int("w") ?: 1).toDouble() / (it.int("h") ?: 1) }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
    private fun JsonObject.int(key: String): Int? =
        long(key)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()

    // Squawker (MIT), `factory TweetWithCard.tombstone(dynamic e)`: tweetWithCard.text is
    // e['richText']['text'], else e['text']['text'], else L10n.current.this_tweet_is_unavailable,
    // then replaceFirst(' Learn more', ''). Nitter's TweetTombstone selects tombstone.richText
    // then tombstone.text and getTombstone removes the same suffix
    // (https://github.com/zedeus/nitter/blob/master/src/parser.nim#L574).
    // https://github.com/j-fbriere/squawker/blob/master/lib/client/client.dart#L1398-L1405
    // not ported: idStr = '' and isTombstone = true (the caller makes the quote of this text).
    private fun tombstone(e: JsonObject?): String {
        val tweetWithCard = (e?.obj("richText")?.string("text") ?: e?.obj("text")?.string("text") ?: "This post is unavailable")
            .replaceFirst(" Learn more", "")
        return tweetWithCard
    }

    private const val COMPLETED_MARKER ="kind:\"GraphQLRequestStream.Completed\""
    private const val RESULT_TEXT_MARKER = "kind:\"Result.Ok\",value:\""

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
     * Copies the string literal at [quote] as a JSON string and returns the
     * index after it. Ported from Seroval's deserializeString
     * (https://github.com/lxsmnsyc/seroval/blob/main/packages/seroval/src/core/string.ts#deserializeString):
     * only the escapes its serializeString writes are decoded, which are the
     * backslash, the double quote, \n \r \b \t \f, \x3C and the line and
     * paragraph separators. Any other backslash stays a literal backslash, so
     * it is written doubled for JSON. The old rewrite of every \x and \' was
     * wider than Seroval and changed text that was never escaped.
     */
    private fun copyString(source: String, quote: Int, out: StringBuilder): Int {
        out.append('"')
        var index = quote + 1
        while (index < source.length) {
            val c = source[index]
            if (c == '"') {
                out.append('"')
                return index + 1
            }
            if (c != '\\') {
                // JSON refuses a raw control character inside a string.
                if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
                index++
                continue
            }
            var replacement: String? = null
            // Code units the escape spans, including the backslash.
            var length = 2
            when (source.getOrNull(index + 1)) {
                '\\' -> replacement = "\\\\"
                '"' -> replacement = "\\\""
                'n' -> replacement = "\\n"
                'r' -> replacement = "\\r"
                'b' -> replacement = "\\b"
                't' -> replacement = "\\t"
                'f' -> replacement = "\\f"
                'x' -> if (source.startsWith("3C", index + 2)) {
                    replacement = "<"
                    length = 4
                }
                'u' -> if (source.startsWith("2028", index + 2)) {
                    replacement = "\\u2028"
                    length = 6
                } else if (source.startsWith("2029", index + 2)) {
                    replacement = "\\u2029"
                    length = 6
                }
                else -> Unit
            }
            if (replacement != null) {
                out.append(replacement)
                index += length
            } else {
                out.append("\\\\")
                index++
            }
        }
        return index
    }

    /**
     * Index just past the string literal opened at [quote], or null when it
     * never closes. Same escape rule as Seroval's string writer, a backslash
     * takes the next character with it:
     * https://github.com/lxsmnsyc/seroval/blob/main/packages/seroval/src/core/string.ts
     */
    fun endOfString(source: String, quote: Int): Int? {
        var i = quote + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return null
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
