package com.mtga.app.feature.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Turns post text into tappable text: @mentions open the profile in the app,
 * and link tokens open the full URL.
 *
 * Nitter shortens links in the visible text ("example.com/some/pa…") while the
 * real URLs arrive separately in [links]. A token is linked only when it
 * matches one of those real URLs, or when it is a complete http(s) URL itself.
 * That is what keeps "Node.js" or "e.g." from becoming broken links.
 */
internal fun linkify(
    text: String,
    links: List<String>,
    linkColor: Color,
    onMention: (String) -> Unit,
    onHashtag: (String) -> Unit = {}
): AnnotatedString {
    val style = TextLinkStyles(style = SpanStyle(color = linkColor))
    val matches = TOKEN.findAll(text).toList()

    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            val token = match.value
            val mention = match.groups[1]?.value
            // Group 2 is a hashtag or a cashtag, with its # or $: the search is for the tag as written.
            val hashtag = match.groups[2]?.value
            val target = if (mention == null && hashtag == null) resolve(token, links) else null
            if (mention == null && hashtag == null && target == null) continue

            append(text.substring(cursor, match.range.first))
            if (mention != null) {
                withLink(LinkAnnotation.Clickable("mention:$mention", style) { onMention(mention) }) {
                    append(token)
                }
            } else if (hashtag != null) {
                withLink(LinkAnnotation.Clickable("hashtag:$hashtag", style) { onHashtag(hashtag) }) {
                    append(token)
                }
            } else {
                withLink(LinkAnnotation.Url(target!!, style)) { append(token) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

private fun resolve(token: String, links: List<String>): String? {
    val wanted = normal(token)
    if (wanted.length < 4) return null
    links.firstOrNull { normal(it).startsWith(wanted) }?.let { return it }
    return token.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

private fun normal(value: String): String =
    value.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .trimEnd('…', '.', ',', ')', '!', '?')

// AGPL-3.0, ported from Nitter, extractHashtags: every hashtag and every cashtag (Nitter's symbols)
// adds a replacement of kind rkHashtag over its slice to the result. Nitter reads the slice from the
// entity's indices, the sources of this app give none, so it is found in the text. The slice runs
// from the symbol to the last character of the name: a link reads `symbol & name`, the first
// character then the rest, as replacedWith writes it.
// https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#extractHashtags
// https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#replacedWith
// not ported: the other kinds of replacement (rkUrl, rkMention, rkRemove), the bounds against the
// display range (validRepls, textSlice), and the href to Nitter's own search page: the app's
// Search takes the tag.
internal fun extractHashtags(result: MutableList<IntRange>, text: String) {
    for (js in TOKEN.findAll(text)) {
        if (js.groups[2] != null) result.add(js.range)
    }
}

// The text of a post card with its hashtags and cashtags in the link colour, as linkify below draws
// them in the post's page, without a tap: the whole card opens the post.
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/post/PostText.kt#linkify
// not ported: linkify's mentions, its addresses and the taps (the card has none of the three).
internal fun colourHashtags(text: String, linkColor: Color): AnnotatedString {
    val style = SpanStyle(color = linkColor)
    val result = mutableListOf<IntRange>()
    extractHashtags(result, text)
    return buildAnnotatedString {
        var cursor = 0
        for (match in result) {
            append(text.substring(cursor, match.first))
            withStyle(style) { append(text.substring(match.first, match.last + 1)) }
            cursor = match.last + 1
        }
        append(text.substring(cursor))
    }
}

/**
 * A hashtag or a cashtag: # or $ then a letter or underscore and word characters, not glued to a word, an
 * ampersand (an entity), a slash (an address) or another sign. The digits-only "#1" and "$5" are not tags.
 */
private const val HASHTAG = "(?<![\\p{L}\\p{N}_&/#\$@])[#\$][\\p{L}_][\\p{L}\\p{N}_]*"

/** Group 1 is a mention handle, group 2 a hashtag or cashtag. Anything else is a link candidate. */
private val TOKEN = Regex(
    "(?<![\\w@])@([A-Za-z0-9_]{1,15})\\b" +
        "|(?:https?://)?(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?:/[^\\s]*)?" +
        "|($HASHTAG)"
)
