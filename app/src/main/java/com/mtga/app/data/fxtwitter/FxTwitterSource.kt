package com.mtga.app.data.fxtwitter

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.model.Conversation
import com.mtga.app.core.model.Feed
import com.mtga.app.core.model.FxCursor
import com.mtga.app.core.model.PostKind
import com.mtga.app.core.network.ErrorMapper
import com.mtga.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * An account's whole timeline, page after page, through FxTwitter's public
 * API (the open source service behind post previews in chat apps). Its
 * server reads X's own timeline and answers plain JSON: twenty posts a page
 * with X's cursor, long posts whole, videos with their files. Checked in
 * October 2026 down to June on an account posting daily.
 *
 * Cost to be honest about: FxTwitter sees which accounts are read. It is a
 * source the reader can switch off in Settings.
 *
 * Its answers fail now and then with a 404 that the same request passes a
 * second later, so each read is tried a few times. A 404 is therefore never
 * taken to mean the account is gone.
 */
class FxTwitterSource(
    private val client: HttpClient,
    private val log: RequestLog,
    private val throttle: HostThrottle
) {

    /** A page of [handle]'s timeline, the newest when [cursor] is null. */
    suspend fun timeline(handle: String, cursor: String?): Outcome<Feed> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/2/profile/$handle/statuses" +
            (cursor?.let { "?cursor=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        when (val read = read(url, RequestLog.Kind.PROFILE)) {
            is Read.Failed -> Outcome.Failure(read.error)
            is Read.Body -> {
                val page = FxTwitterParser.timeline(read.text)
                if (page == null) {
                    log.record(RequestLog.Kind.PROFILE, url, "not a timeline", bodyBytes = read.text.length, durationMillis = read.millis)
                    return@withContext Outcome.Failure(AppError.ParseFailure(HOST, PARSER_VERSION, read.text.take(200)))
                }
                val profile = FxTwitterParser.profile(read.text, handle)
                log.record(
                    RequestLog.Kind.PROFILE, url, "ok",
                    httpStatus = 200, bodyBytes = read.text.length, durationMillis = read.millis,
                    detail = "posts: ${page.posts.size} | reposts: ${page.posts.count { it.kind == PostKind.REPOST }}" +
                        (page.posts.minByOrNull { it.publishedAtMillis }?.let { " | oldest: ${java.time.Instant.ofEpochMilli(it.publishedAtMillis)}" } ?: "") +
                        " | next: ${if (page.bottomCursor != null && page.posts.isNotEmpty()) "yes" else "none"}"
                )
                Outcome.Success(
                    Feed(
                        handle = handle,
                        // Blank when no post of the account's own is on the
                        // page: the stored profile keeps its name.
                        displayName = profile?.name.orEmpty(),
                        posts = page.posts,
                        fetchedFromHost = HOST,
                        fetchedAtMillis = System.currentTimeMillis(),
                        avatarUrl = profile?.avatarUrl,
                        bio = profile?.bio,
                        bannerUrl = profile?.bannerUrl,
                        nextCursor = page.bottomCursor?.takeIf { page.posts.isNotEmpty() }?.let(FxCursor::of)
                    )
                )
            }
        }
    }

    /** A post with the posts around it and its replies. */
    suspend fun conversation(id: String): Outcome<Conversation> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/2/conversation/$id"
        when (val read = read(url, RequestLog.Kind.THREAD)) {
            is Read.Failed -> Outcome.Failure(read.error)
            is Read.Body -> FxTwitterParser.conversation(read.text, HOST)
                ?.let { conversation ->
                    log.record(
                        RequestLog.Kind.THREAD, url, "ok", httpStatus = 200, bodyBytes = read.text.length, durationMillis = read.millis,
                        detail = "above: ${conversation.ancestors.size} | thread below: ${conversation.continuation.size} | replies: ${conversation.replies.size}"
                    )
                    Outcome.Success(conversation)
                }
                ?: Outcome.Failure(AppError.PostUnavailable(HOST, null))
        }
    }

    private sealed interface Read {
        class Body(val text: String, val millis: Long) : Read
        class Failed(val error: AppError) : Read
    }

    private suspend fun read(url: String, kind: RequestLog.Kind): Read {
        var last: AppError = AppError.Unknown(null)
        repeat(TRIES) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)
            if (!throttle.acquire(HOST)) return Read.Failed(AppError.RateLimited(HOST, null))
            val started = System.currentTimeMillis()
            try {
                val response = client.get(url) {
                    header("User-Agent", "MTGA")
                    header("Accept", "application/json")
                }
                val text = response.bodyAsText()
                val millis = System.currentTimeMillis() - started
                val status = response.status.value
                if (status == 200) {
                    throttle.clear(HOST)
                    return Read.Body(text, millis)
                }
                last = if (status == 404) AppError.InstanceError(HOST, 404) else ErrorMapper.fromResponse(HOST, response, text) ?: AppError.InstanceError(HOST, status)
                if (status == 429) throttle.penalise(HOST, response.headers["Retry-After"]?.toLongOrNull())
                log.record(kind, url, if (attempt < TRIES - 1) "http $status, trying again" else "http $status", httpStatus = status, bodyBytes = text.length, durationMillis = millis)
                if (status == 429) return Read.Failed(last)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                last = ErrorMapper.fromThrowable(HOST, t)
                log.record(kind, url, "transport failure", durationMillis = System.currentTimeMillis() - started, detail = "${t::class.java.simpleName}: ${t.message}")
            }
        }
        return Read.Failed(last)
    }

    companion object {
        const val HOST = "api.fxtwitter.com"
        private const val TRIES = 3
        private const val RETRY_DELAY_MS = 1_500L
        private const val PARSER_VERSION = 1
    }
}
