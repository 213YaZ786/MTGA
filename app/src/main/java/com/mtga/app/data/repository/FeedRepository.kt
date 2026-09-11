package com.mtga.app.data.repository

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.data.html.HtmlSource
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.rss.RssSource
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.twstalker.TwstalkerSource
import com.mtga.app.data.xcom.XComSource

/**
 * The only place that decides which source to use.
 *
 * HTML first. That reverses the original plan, and the reason is concrete:
 * the one surviving instance restricts RSS to clients it has approved, while
 * its web pages stay open. RSS remains wired up because it is cheaper and
 * because a different instance, or a whitelisted one, makes it the better path
 * again without any change above this class.
 */
class FeedRepository(
    private val pool: InstancePool,
    private val html: HtmlSource,
    private val rss: RssSource,
    private val twstalker: TwstalkerSource,
    private val xcom: XComSource,
    private val settings: SettingsStore
) {

    /**
     * The newest posts straight from x.com, when enabled. Head only, no
     * cursor, so it is fetched alongside [loadFeed] rather than instead of it.
     * Returns null when the setting is off.
     */
    suspend fun loadHead(handle: String): Outcome<Feed>? =
        if (settings.current.useXcomDirect) xcom.fetchLatest(handle) else null

    /** A post's conversation, from whichever Nitter server is healthy. Never cached. */
    suspend fun loadConversation(handle: String, id: String): Outcome<Conversation> =
        pool.withInstance { instance -> html.fetchConversation(instance, handle, id) }

    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> {
        // A twstalker cursor can only be continued by twstalker. Cursors are not
        // interchangeable between sources, and handing one to the wrong source
        // silently restarts the feed.
        if (TwstalkerSource.handles(cursor)) {
            if (settings.current.useTwstalker) return twstalker.fetch(handle, cursor)
            // Switched off after it served this account. No other source can
            // continue its cursor, so the honest answer is that this source
            // has nothing more. The empty page makes the cache drop the
            // cursor, and paging stops instead of failing on every scroll.
            return Outcome.Success(
                Feed(
                    handle = handle,
                    displayName = "",
                    posts = emptyList(),
                    fetchedFromHost = TwstalkerSource.HOST,
                    fetchedAtMillis = System.currentTimeMillis()
                )
            )
        }

        val viaHtml = pool.withInstance { instance -> html.fetchProfile(instance, handle, cursor) }
        if (viaHtml is Outcome.Success) return viaHtml

        val htmlError = (viaHtml as Outcome.Failure).error

        // RSS only helps when the page itself was the problem, and it has no
        // concept of paging, so it can only ever stand in for the first page.
        if (cursor != null || !worthTryingRss(htmlError)) {
            // Paging cannot cross sources, so a mid-scroll failure stays failed.
            return if (cursor != null) viaHtml else fallBackToTwstalker(handle, htmlError)
        }

        val viaRss = rss.fetchFeed(handle)
        if (viaRss is Outcome.Success) return viaRss

        return fallBackToTwstalker(handle, htmlError)
    }

    /**
     * Last resort, and deliberately last. This host shows ads and runs
     * analytics, so it learns which accounts are read. That is a real cost, and
     * it is only worth paying when every privacy respecting instance has failed,
     * and only when the person switched it on in the connection check.
     * The UI always names the server that answered, so this is never silent.
     */
    private suspend fun fallBackToTwstalker(
        handle: String,
        originalError: AppError
    ): Outcome<Feed> {
        // Off by default, and then never contacted at all.
        if (!settings.current.useTwstalker) return Outcome.Failure(originalError)
        return when (val viaTwstalker = twstalker.fetch(handle, null)) {
            is Outcome.Success -> viaTwstalker
            // Report the Nitter failure, which is the one that matters.
            is Outcome.Failure -> Outcome.Failure(originalError)
        }
    }

    private fun worthTryingRss(error: AppError): Boolean = when (error) {
        // A check is deliberately absent. The feed host of a checked instance
        // is either behind the same check or gated, as rss.xcancel.com is, so
        // trying it only spends a request against a fragile host.
        is AppError.ParseFailure,
        is AppError.ClientRefused,
        is AppError.InstanceError,
        is AppError.Timeout -> true
        else -> false
    }
}
