package com.mtga.app.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * One post, normalised.
 *
 * Every source produces this exact type, which is what lets a source be swapped
 * without the UI knowing. Fields a given source cannot supply stay null rather
 * than being faked, so the UI can hide what it does not have instead of showing
 * a plausible lie.
 */
@Serializable
data class Post(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val avatarUrl: String? = null,
    val text: String,
    val links: List<String> = emptyList(),
    val publishedAtMillis: Long,
    val permalink: String,
    val kind: PostKind = PostKind.ORIGINAL,
    val relatedHandle: String? = null,
    val isPinned: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val quoted: QuotedPost? = null,
    val card: LinkCard? = null,
    val poll: Poll? = null,
    val note: CommunityNote? = null,
    val stats: PostStats? = null,
    /**
     * True when [text] is only the cut head of a long post: X's page or embed
     * said the post is a note tweet (a long one) but did not give its text,
     * so only the first 280 characters came. A source that carries the whole
     * text never sets it, and a fuller text clears it, see [mergedWith].
     * Taken from FxEmbed (MIT), where a long post is the one whose
     * note_tweet.note_tweet_results.result exists and its full text is that
     * result's text: https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts
     * Defaults to false so files stored before the field existed still load.
     */
    val textCut: Boolean = false,
    /**
     * True once the whole text was asked for and answered, so the post is not
     * asked again at every start. Feeder's per item flag, set once its full
     * text fetch ran and false by default:
     * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/db/room/FeedItem.kt#L117
     * MTGA stores JSON files, not Room rows. Files written up to 2.15.17 name
     * the field `textChecked`: @JsonNames makes the decoder read that old key
     * too, so posts marked before the rename stay marked. The file is written
     * with the new key. A fuller text from another source resets it, see
     * [mergedWith]. Files stored before the field existed load as false.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("textChecked")
    val fullTextDownloaded: Boolean = false
) {
    /**
     * The same post seen again, possibly from a richer source. x.com gives the
     * head of a feed first, without cards or polls, and Nitter brings them a
     * moment later for the same id. The stored post keeps its identity and
     * takes whatever [fresh] knows that it does not, plus the newer counts,
     * since votes and likes move.
     *
     * Text is the fuller of the two. x.com's syndication endpoint cuts long
     * posts off mid sentence, and Nitter returns the whole thing for the same
     * id, so keeping the stored text unconditionally froze the truncated
     * version in the cache for good. A cut text is a prefix of the full one,
     * so length is the honest test and no source can shorten what is stored.
     */
    fun mergedWith(fresh: Post): Post {
        if (fresh.id != id) return this
        val fuller = fresh.text.fullerThan(text)
        val merged = copy(
            avatarUrl = avatarUrl ?: fresh.avatarUrl,
            text = fullerText(text, fresh.text),
            // A fuller text brings its own verdict. At equal length the post
            // stays cut only if both copies say so: one whole copy settles it.
            textCut = if (fuller) fresh.textCut else textCut && fresh.textCut,
            // The answer was about this text: a fuller one is a new question,
            // the same text seen again is not.
            fullTextDownloaded = if (fuller) fresh.fullTextDownloaded else fullTextDownloaded || fresh.fullTextDownloaded,
            links = if (fresh.text.fullerThan(text)) fresh.links.ifEmpty { links } else links,
            media = media.ifEmpty { fresh.media },
            quoted = quoted?.let { q -> q.mergedWith(fresh.quoted) } ?: fresh.quoted,
            card = fresh.card ?: card,
            poll = fresh.poll ?: poll,
            note = fresh.note ?: note,
            stats = stats?.let { old -> fresh.stats?.let(old::mergedWith) ?: old } ?: fresh.stats
        )
        return if (merged == this) this else merged
    }
}

/**
 * True when this text carries more of the post than [other]. A source that cuts
 * a long post off produces a prefix of the whole text, so length is the test.
 */
internal fun String.fullerThan(other: String): Boolean = trim().length > other.trim().length

/** The more complete of the two, [stored] winning a tie so identity is stable. */
internal fun fullerText(stored: String, fresh: String): String =
    if (fresh.fullerThan(stored)) fresh else stored

// Source: FxEmbed (MIT), the note tweet test of
// https://github.com/FxEmbed/FxEmbed/blob/main/packages/atmosphere/src/providers/twitter/processor.ts
// (lines 517 to 534). Its is_note_tweet is the marker; the window below is
// MTGA's own earlier guess, kept for posts stored without a marker.
/**
 * Whether a text may be the cut head of a long post. [marked] is the source's
 * own word (see [Post.textCut]). Posts stored before that marker existed have
 * none, so the old guess stays as a fallback: X cuts at 280 characters, and
 * a text of 240 to 290 counted in code points (an emoji is one, as X counts
 * it), without a trailing t.co link that X adds to the cut text, may be one.
 */
internal fun looksCut(text: String, marked: Boolean): Boolean {
    if (marked) return true
    val body = text.trimEnd().replace(TRAILING_TCO, "")
    return body.codePointCount(0, body.length) in CUT_WINDOW
}

private val TRAILING_TCO = Regex("\\s*https://t\\.co/\\w+$")
private val CUT_WINDOW = 240..290

@Serializable
enum class PostKind { ORIGINAL, REPOST, REPLY, QUOTE }

@Serializable
enum class MediaType { PHOTO, VIDEO, GIF }

/**
 * [previewUrl] is what gets shown, [downloadUrl] is the full resolution
 * original. Nitter serves both, and conflating them means either a blurry
 * gallery or a very slow timeline.
 */
@Serializable
data class MediaItem(
    val previewUrl: String,
    val downloadUrl: String,
    val type: MediaType,
    val durationLabel: String? = null
)

@Serializable
data class QuotedPost(
    val handle: String,
    val name: String,
    val text: String,
    val permalink: String,
    val note: CommunityNote? = null,
    /** As [Post.textCut], for the quoted post's own text. */
    val textCut: Boolean = false,
    /** As [Post.fullTextDownloaded], for the quoted post's own text, with the same old key. */
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("textChecked")
    val fullTextDownloaded: Boolean = false
) {
    /** Same rule as the post itself: the fuller text wins, and a note is kept. */
    fun mergedWith(fresh: QuotedPost?): QuotedPost {
        if (fresh == null) return this
        return copy(
            text = fullerText(text, fresh.text),
            note = fresh.note ?: note,
            textCut = if (fresh.text.fullerThan(text)) fresh.textCut else textCut && fresh.textCut,
            fullTextDownloaded = if (fresh.text.fullerThan(text)) fresh.fullTextDownloaded else fullTextDownloaded || fresh.fullTextDownloaded
        )
    }

    /** The post this one quotes, read from its permalink (status/<id>), or null. */
    val quotedId: String?
        get() = permalink.substringAfterLast("/status/", "").takeWhile(Char::isDigit).ifEmpty { null }
}

/**
 * Context written by X's community notes, as the source shows it under a post.
 * [links] are the sources the note cites, in order of appearance.
 */
@Serializable
data class CommunityNote(
    val text: String,
    val links: List<String> = emptyList()
)

/**
 * [large] mirrors Nitter's own split: summary, app and player cards are small,
 * with a thumbnail beside the text, the others put a wide picture on top.
 * [isArticle] marks an X article preview, which Nitter draws with a badge.
 */
@Serializable
data class LinkCard(
    val title: String,
    val description: String?,
    val destination: String?,
    val imageUrl: String?,
    val url: String?,
    val large: Boolean = true,
    val isArticle: Boolean = false
)

/**
 * A poll as the server last showed it. Percentages come rounded from the
 * source, so they may not add up to exactly 100. [status] is the source's own
 * wording, for example "Final results" or "2 days left".
 */
@Serializable
data class Poll(
    val options: List<PollOption>,
    val votes: Long? = null,
    val status: String? = null
)

@Serializable
data class PollOption(
    val label: String,
    val percent: Int,
    val leader: Boolean = false
)

@Serializable
data class PostStats(
    val replies: Int? = null,
    val reposts: Int? = null,
    val likes: Int? = null,
    val views: Int? = null
) {
    /** Newer counts win, a count the fresh source does not carry is kept. */
    fun mergedWith(fresh: PostStats) = PostStats(
        replies = fresh.replies ?: replies,
        reposts = fresh.reposts ?: reposts,
        likes = fresh.likes ?: likes,
        views = fresh.views ?: views
    )
}

/** A single account's feed as fetched from one instance. */
@Serializable
data class Feed(
    val handle: String,
    val displayName: String,
    val posts: List<Post>,
    val fetchedFromHost: String,
    val fetchedAtMillis: Long,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val nextCursor: String? = null,
    /**
     * Where a refresh left a hole: the FxTwitter cursor below a first page
     * that did not reach the posts stored before it. The gap filling reads
     * from there down to them, see FeedRepository.fillGaps.
     */
    val gapCursor: String? = null,
    /**
     * True when this feed's source marks pinned posts, which only a Nitter
     * profile page does. x.com's embed endpoint and RSS never
     * mention pins, so their word on [Post.isPinned] carries no weight and
     * must not overwrite what a Nitter page said.
     */
    val pinAware: Boolean = false,
    /**
     * True when this feed's first page held x.com's TimelinePinEntry: the pinned
     * post of [posts] is then THE pinned post, and a stored pin that is not it
     * is stale. A page without that entry leaves this false and says nothing
     * about pins. Distinct from [pinAware], whose absence of a pin also
     * counts. See FeedCache.append.
     */
    val pinNamed: Boolean = false,
    /** Profile card details. Only Nitter pages carry them, other sources leave them null. */
    val bannerUrl: String? = null,
    val location: String? = null,
    val website: String? = null,
    /** As Nitter words it, for example "Joined March 2007". */
    val joined: String? = null,
    val stats: ProfileStats? = null
)

/** The numbers on a profile card. Any may be missing. */
@Serializable
data class ProfileStats(
    val posts: Long? = null,
    val following: Long? = null,
    val followers: Long? = null,
    val likes: Long? = null
)

/**
 * A post with its surroundings, as read from its own page. Not cached: replies
 * change constantly and are only worth reading fresh.
 *
 * [ancestors] are the posts it answers, oldest first. [continuation] is the
 * author's own thread under it. [replies] are grouped in the small chains the
 * server shows, a reply followed by the answers to it.
 */
data class Conversation(
    val ancestors: List<Post>,
    val main: Post?,
    val continuation: List<Post>,
    val replies: List<List<Post>>,
    val host: String
)
