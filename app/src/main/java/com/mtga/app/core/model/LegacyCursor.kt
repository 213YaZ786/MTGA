package com.mtga.app.core.model

/**
 * Cursors written by a source MTGA no longer has.
 *
 * twstalker was dropped in 2.6.1. Cursors are not interchangeable between
 * sources, so one of its cursors sitting in a cache written by an older
 * version must be recognised and abandoned rather than handed to a Nitter,
 * which would silently restart the feed from the top.
 *
 * This outlives the source on purpose: the caches are on readers' phones, and
 * they are the ones that still hold these strings.
 */
object LegacyCursor {

    private const val TWSTALKER_PREFIX = "tws|"

    fun fromDroppedSource(cursor: String?): Boolean =
        cursor?.startsWith(TWSTALKER_PREFIX) == true
}
