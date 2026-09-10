package com.mtga.app.core.model

/**
 * One canonical form for post ids: the numeric status id, for example "123".
 *
 * Every source must agree on this, because deduplication is by id. Nitter HTML
 * used to yield "/user/status/123", Nitter RSS a full URL, while x.com and
 * twstalker yield "123", so the same post read from two sources appeared twice.
 *
 * Anything that does not contain a recognisable status id is returned
 * unchanged, so an unexpected format degrades to "no deduplication" rather
 * than to two different posts collapsing into one.
 */
object PostId {

    fun normalize(raw: String): String {
        val path = raw.substringBefore('#').substringBefore('?').trimEnd('/')
        val candidate = if ("/status/" in path) {
            path.substringAfter("/status/").substringBefore('/')
        } else {
            path.substringAfterLast('/')
        }
        return if (candidate.isNotEmpty() && candidate.all(Char::isDigit)) candidate else raw
    }
}
