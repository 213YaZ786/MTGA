package com.mtga.app.data.xcom

import com.mtga.app.core.model.Post

/**
 * Completes the posts x.com serves cut.
 *
 * A post longer than 280 characters is cut everywhere x.com serves it short:
 * the rendered page shows it with a "show more" button, and the embed endpoint
 * returns the same cut text. The whole text is in the page regardless, in the
 * hydration payload, as a NoteTweet record.
 *
 * Nothing here reads that button, so its label never matters. "Show more",
 * "Voir plus" or any other locale all lead to the same payload.
 *
 * Tying a note back to its post id would mean walking three levels of minified
 * references that X renames at will. The cut text is a prefix of the whole one,
 * so matching on the prefix is enough and survives renaming.
 *
 * Lives outside [XComSource] because it is pure Kotlin and belongs in a unit
 * test, which is what would have caught the failure this file fixes.
 */
internal object NoteTweetText {

    /** Replaces the text of every post the page carries a longer version of. */
    fun complete(posts: List<Post>, page: String): List<Post> {
        val notes = texts(page)
        if (notes.isEmpty()) return posts
        return posts.map { post ->
            val head = post.text.trimEnd()
            if (head.length < MIN_MATCH) return@map post
            val whole = notes.firstOrNull { it.startsWith(head) }
                ?: notes.firstOrNull { it.startsWith(head.take(MIN_MATCH)) }
            if (whole != null && whole.length > head.length) post.copy(text = whole) else post
        }
    }

    /**
     * Every NoteTweet text the page carries, unescaped, in order, without
     * repeats.
     *
     * Read by scanning rather than by one regex over the whole record. The
     * regex this replaces demanded that text follow __typename immediately.
     * X now writes rest_id between the two, so it matched nothing at all and
     * every long post stayed cut. A scan that only assumes the two fields
     * share a record survives the next field being inserted.
     */
    fun texts(page: String): List<String> {
        val found = LinkedHashSet<String>()
        var from = 0
        while (true) {
            val at = page.indexOf(TYPE_MARKER, from)
            if (at < 0) break
            from = at + TYPE_MARKER.length
            textField(page, from)?.let { if (it.isNotEmpty()) found += it }
        }
        return found.toList()
    }

    /**
     * The text field of the record that starts at [start], or null when the
     * record ends first. Both spellings are accepted because the page carries
     * two payloads: the initial one is JSON with quoted keys, the streamed
     * ones are JavaScript with bare keys.
     */
    private fun textField(page: String, start: Int): String? {
        val limit = minOf(page.length, start + FIELD_WINDOW)
        var i = start
        while (i < limit) {
            when {
                // A brace closes or opens a nested record. Anything past it
                // belongs to a different one, so this record has no text.
                page[i] == '}' || page[i] == '{' -> return null
                page.startsWith(BARE_KEY, i) && !isNameChar(page.getOrNull(i - 1)) ->
                    return unescape(readString(page, i + BARE_KEY.length))
                page.startsWith(QUOTED_KEY, i) ->
                    return unescape(readString(page, i + QUOTED_KEY.length))
            }
            i++
        }
        return null
    }

    /** Guards against matching the tail of a longer key such as full_text. */
    private fun isNameChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_')

    /** The raw, still escaped contents of the string literal opened at [from]. */
    private fun readString(page: String, from: Int): String {
        var i = from
        while (i < page.length) {
            when (page[i]) {
                '\\' -> i += 2
                '"' -> return page.substring(from, i)
                else -> i++
            }
        }
        return page.substring(from)
    }

    /** The payload is JavaScript, so its string escapes are JSON's. */
    internal fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i == raw.lastIndex) {
                out.append(c)
                i++
                continue
            }
            when (val escape = raw[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'b' -> { out.append('\b'); i += 2 }
                'u' -> {
                    val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                    val code = hex.toIntOrNull(16)
                    if (hex.length == 4 && code != null) {
                        out.append(code.toChar())
                        i += 6
                    } else {
                        out.append(escape)
                        i += 2
                    }
                }
                else -> { out.append(escape); i += 2 }
            }
        }
        return out.toString()
    }

    /**
     * Closing quote included, so NoteTweetResults and NoteTweetData, which are
     * wrappers holding no text, are not mistaken for the record itself.
     */
    private const val TYPE_MARKER = "\"NoteTweet\""

    private const val BARE_KEY = "text:\""
    private const val QUOTED_KEY = "\"text\":\""

    /** Wide enough for the few fields X writes before text, short enough to fail fast. */
    private const val FIELD_WINDOW = 512

    /** Long enough that no two posts of one page share it by accident. */
    private const val MIN_MATCH = 40
}
