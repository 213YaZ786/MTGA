package com.mtga.app.data.repository

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.model.Feed
import com.mtga.app.data.html.HtmlSource
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.rss.RssSource

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
    private val rss: RssSource
) {

    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> {
        val viaHtml = pool.withInstance { instance -> html.fetchProfile(instance, handle, cursor) }
        if (viaHtml is Outcome.Success) return viaHtml

        val htmlError = (viaHtml as Outcome.Failure).error

        // Falling back to RSS only helps when the page itself was the problem.
        // If the account is gone or we are offline, RSS says the same thing
        // more slowly.
        // RSS has no concept of paging, so it can only ever stand in for the
        // first page. Falling back to it while paging would silently restart
        // the feed from the top.
        if (cursor != null || !worthTryingRss(htmlError)) return viaHtml

        return when (val viaRss = rss.fetchFeed(handle)) {
            is Outcome.Success -> viaRss
            // Report the HTML failure, since that is the path that matters and
            // the RSS failure is usually just the gate notice.
            is Outcome.Failure -> Outcome.Failure(htmlError)
        }
    }

    private fun worthTryingRss(error: AppError): Boolean = when (error) {
        is AppError.ParseFailure,
        is AppError.ClientRefused,
        is AppError.InstanceError,
        is AppError.Timeout -> true
        else -> false
    }
}
