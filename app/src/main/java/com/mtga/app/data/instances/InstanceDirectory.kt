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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the Nitter server list comes from. MTGA ships no list of its own.
 *
 * Public instances appear and vanish within weeks, so a list compiled into the
 * app is wrong by the time it is installed. This reads the Nitter project's
 * own wiki page instead, as raw markdown from GitHub, at every launch and
 * whenever the person asks.
 *
 * Trust cost, stated plainly: GitHub sees one request per app launch from this
 * device, about 5 kB. It learns nothing about which accounts are read.
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
            Outcome.Success(withInstances(listed, instances()))
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

    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/instances/InstanceDirectory.kt#fetch
    // The wiki's fetch, for the tracker (api.rs#instances serves its cache at /api/v1/instances): a 429 or
    // any failure is logged quietly and the wiki's list stands.
    private suspend fun instances(): List<InstanceListParser.Listed> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
            val response = client.get(TRACKER_URL)
            val body = response.bodyAsText()
            val status = response.status.value
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            if (status !in 200..299) {
                log.record(RequestLog.Kind.LIST, TRACKER_URL, "HTTP error", status, body.length, elapsed, "tracker skipped, the wiki's list stands")
                return@withContext emptyList()
            }

            val listed = parseCacheData(body)
            log.record(
                RequestLog.Kind.LIST, TRACKER_URL, "ok", status, body.length, elapsed,
                "servers: ${listed.size}"
            )
            listed
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            log.record(
                RequestLog.Kind.LIST, TRACKER_URL, "transport failure",
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                detail = "${t::class.java.simpleName}: ${t.message}"
            )
            emptyList()
        }
    }

    companion object {
        const val TRACKER_URL = "https://status.d420.de/api/v1/instances"

        // https://github.com/4cuck/nitter-status/blob/main/entities/src/state/mod.rs#CacheHost
        // not ported: is_upstream (deprecated, version_state replaces it), is_latest_version (deprecated),
        // points, recent_pings, ping_max, ping_min, ping_avg, version, version_url, last_healthy,
        // version_state, country, recent_checks, healthy_percentage_overall, connectivity, community_funded
        // (the pool probes the servers itself and never reads them), __show_last_seen (the tracker's page).
        @Serializable
        private data class CacheHost(
            val url: String,
            val domain: String = "",
            val rss: Boolean = true,
            val healthy: Boolean = false,
            @SerialName("is_bad_host") val isBadHost: Boolean = false,
            val unreliable: Boolean = false
        )

        // https://github.com/4cuck/nitter-status/blob/main/entities/src/state/mod.rs#CacheData
        // not ported: last_update, latest_commit (not read).
        @Serializable
        private data class CacheData(val hosts: List<CacheHost> = emptyList())

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/instances/InstanceListParser.kt#parse
        // The wiki's parse, for the tracker's CacheData (api.rs#instances): hosts healthy, not a bad host and
        // not unreliable, https only, host only, no onion or i2p. First mention wins, one entry per host.
        fun parseCacheData(body: String): List<InstanceListParser.Listed> {
            val cache = runCatching { json.decodeFromString<CacheData>(body) }.getOrNull() ?: return emptyList()
            val found = LinkedHashMap<String, InstanceListParser.Listed>()
            for (entry in cache.hosts) {
                if (!entry.healthy || entry.isBadHost || entry.unreliable) continue
                if (!entry.url.startsWith("https://")) continue
                val host = entry.url.removePrefix("https://").substringBefore('/').substringBefore('?').lowercase()
                if (!HOST_NAME.matches(host) || host.endsWith(".onion") || host.endsWith(".i2p")) continue
                found.putIfAbsent(host, InstanceListParser.Listed(host, "https://$host", listedWorking = true))
            }
            return found.values.toList()
        }

        private val HOST_NAME = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""")

        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/instances/InstanceListParser.kt#parse
        // The wiki's entries first, then the tracker's the wiki does not hold, merged by host.
        fun withInstances(wiki: List<InstanceListParser.Listed>, found: List<InstanceListParser.Listed>): List<InstanceListParser.Listed> {
            val listed = wiki.mapTo(LinkedHashSet()) { it.host }
            return wiki + found.filter { listed.add(it.host) }
        }

        const val LIST_URL = "https://raw.githubusercontent.com/wiki/zedeus/nitter/Instances.md"
        const val HOST = "raw.githubusercontent.com"
        const val PARSER_VERSION = 1

        /**
         * How long a fetched list is trusted before the next launch refetches.
         *
         * It was a day, on the reasoning that the wiki changes over weeks. The
         * reasoning was wrong the day xcancel started answering 451: what
         * changes in hours is not the list, it is which of its servers still
         * work, and every stale hour is spent on the wrong ones. The floor is
         * kept short rather than removed so that relaunching the app three
         * times in a row does not fetch three times. The file is about 5 kB,
         * from the one host MTGA contacts outside the reading path.
         */
        const val MAX_AGE_MS = 15 * 60_000L
    }
}
