package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.network.ErrorMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the Nitter server list comes from. MTGA ships no list of its own.
 *
 * Public instances appear and vanish within weeks, so a list compiled into the
 * app is wrong by the time it is installed. This reads the Nitter project's
 * own wiki page instead, as raw markdown from GitHub, at most once a day and
 * whenever the person asks.
 *
 * Trust cost, stated plainly: GitHub sees one request a day from this device.
 * It learns nothing about which accounts are read.
 */
class InstanceDirectory(
    private val client: HttpClient,
    private val log: RequestLog
) {

    suspend fun fetch(): Outcome<List<InstanceListParser.Listed>> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
            val response = client.get(LIST_URL)
            val body = response.bodyAsText()
            val status = response.status.value
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            if (status !in 200..299) {
                log.record(RequestLog.Kind.LIST, LIST_URL, "HTTP error", status, body.length, elapsed)
                // Not the general mapper: its 404 means a missing account,
                // here it means the wiki page moved.
                val error = if (status == 429) {
                    AppError.RateLimited(HOST, response.headers["Retry-After"]?.toLongOrNull())
                } else {
                    AppError.InstanceError(HOST, status)
                }
                return@withContext Outcome.Failure(error)
            }

            val listed = InstanceListParser.parse(body)
            if (listed.isEmpty()) {
                // The page moved or its table changed. Keeping the last good
                // list beats emptying the pool.
                log.record(
                    RequestLog.Kind.LIST, LIST_URL, "parse found no servers",
                    status, body.length, elapsed, "wiki layout changed, keeping the last list"
                )
                return@withContext Outcome.Failure(
                    AppError.ParseFailure(HOST, PARSER_VERSION, body.take(200))
                )
            }

            log.record(
                RequestLog.Kind.LIST, LIST_URL, "ok", status, body.length, elapsed,
                "servers: ${listed.size} | listed working: ${listed.count { it.listedWorking == true }}"
            )
            Outcome.Success(listed)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            log.record(
                RequestLog.Kind.LIST, LIST_URL, "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            Outcome.Failure(ErrorMapper.fromThrowable(HOST, t))
        }
    }

    companion object {
        const val LIST_URL = "https://raw.githubusercontent.com/wiki/zedeus/nitter/Instances.md"
        const val HOST = "raw.githubusercontent.com"
        const val PARSER_VERSION = 1

        /** The list changes over weeks, not hours. */
        const val MAX_AGE_MS = 24 * 60 * 60_000L
    }
}
