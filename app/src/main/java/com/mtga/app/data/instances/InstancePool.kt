package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError
import com.mtga.app.core.common.ChallengeKind
import com.mtga.app.core.common.Outcome
import com.mtga.app.core.network.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.pow

/**
 * Owns the instance list, their live health, and the failover decision.
 *
 * Everything above this layer asks for "a working instance" and never picks a
 * host itself. That is what makes losing an instance a degraded experience
 * rather than a broken one.
 */
class InstancePool(
    private val store: InstanceStore,
    private val probe: InstanceProbe,
    private val directory: InstanceDirectory,
    private val connectivity: ConnectivityMonitor,
    private val scope: CoroutineScope
) {

    private val _instances = MutableStateFlow(store.load())
    val instances: StateFlow<List<NitterInstance>> = _instances.asStateFlow()

    /** Where the server list stands: last update, whether one is running, why it failed. */
    data class ListStatus(
        val updatedAtMillis: Long? = null,
        val updating: Boolean = false,
        val lastError: AppError? = null
    )

    private val _listStatus = MutableStateFlow(ListStatus(updatedAtMillis = store.listUpdatedAt()))
    val listStatus: StateFlow<ListStatus> = _listStatus.asStateFlow()

    private val listMutex = Mutex()

    @Volatile
    private var listJob: Job? = null

    // ---- the public list ----------------------------------------------------

    fun updateListAsync(force: Boolean, reset: Boolean = false) {
        listJob = scope.launch { updateList(force, reset) }
    }

    /**
     * Fetches the Nitter wiki list and merges it in. Skipped when the list is
     * less than a day old, unless [force]. A failure keeps the current list,
     * an outdated list still beats an empty pool.
     */
    suspend fun updateList(force: Boolean, reset: Boolean = false) = listMutex.withLock {
        val updatedAt = store.listUpdatedAt()
        val fresh = updatedAt != null &&
            System.currentTimeMillis() - updatedAt < InstanceDirectory.MAX_AGE_MS
        if (!force && fresh && _instances.value.any { it.builtIn }) return@withLock

        _listStatus.value = _listStatus.value.copy(updating = true)
        when (val fetched = directory.fetch()) {
            is Outcome.Success -> {
                // The first merge after 1.3.x replaces choices that were made
                // against the compiled list, see InstanceListMerge.
                val firstTime = updatedAt == null
                mutate { InstanceListMerge.merge(it, fetched.value, reset = reset || firstTime) }
                val now = System.currentTimeMillis()
                store.markListUpdated(now)
                _listStatus.value = ListStatus(updatedAtMillis = now)
            }
            is Outcome.Failure -> _listStatus.value = _listStatus.value.copy(
                updating = false,
                lastError = fetched.error
            )
        }
        Unit
    }

    private val _health = MutableStateFlow<Map<String, InstanceHealth>>(emptyMap())
    val health: StateFlow<Map<String, InstanceHealth>> = _health.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    init {
        // A fresh install has no servers at all until this lands, and an
        // existing one refreshes a list older than a day.
        updateListAsync(force = false)
    }

    // ---- probing -----------------------------------------------------------

    /** Probes every enabled instance in parallel. */
    suspend fun probeAll() {
        _probing.value = true
        try {
            val targets = _instances.value.filter { it.enabled }
            coroutineScope {
                targets.map { instance -> async { probe.probe(instance) } }
                    .awaitAll()
                    .forEach(::record)
            }
        } finally {
            _probing.value = false
        }
    }

    fun probeAllAsync() {
        scope.launch { probeAll() }
    }

    private fun record(result: ProbeResult) {
        val now = System.currentTimeMillis()
        val previous = _health.value[result.instanceId] ?: InstanceHealth(result.instanceId)

        val updated = if (result.error == null) {
            previous.copy(
                lastCheckedAt = now,
                lastSuccessAt = now,
                latencyMillis = result.latencyMillis.takeIf { it > 0 } ?: previous.latencyMillis,
                lastError = null,
                consecutiveFailures = 0,
                backoffUntilMillis = null
            )
        } else {
            val failures = previous.consecutiveFailures + 1
            previous.copy(
                lastCheckedAt = now,
                latencyMillis = result.latencyMillis.takeIf { it > 0 } ?: previous.latencyMillis,
                lastError = result.error,
                consecutiveFailures = failures,
                backoffUntilMillis = now + backoffMillis(result.error, failures)
            )
        }

        _health.value = _health.value + (result.instanceId to updated)
    }

    /**
     * Exponential, but an explicit Retry-After always wins. Servers telling us
     * how long to wait is the strongest signal available, and ignoring it is
     * how a client earns a permanent ban.
     */
    private fun backoffMillis(error: AppError, failures: Int): Long {
        if (error is AppError.RateLimited && error.retryAfterSeconds != null) {
            return error.retryAfterSeconds * 1_000L
        }
        // A check is not a failure of the instance. Sinking it in the order is
        // enough, backing it off would hide a host the user just cleared.
        if (error is AppError.ChallengeRequired && error.kind != ChallengeKind.WAF_BLOCK) {
            return 0L
        }
        val exponential = BASE_BACKOFF_MS * 2.0.pow((failures - 1).coerceAtMost(6)).toLong()
        return min(exponential, MAX_BACKOFF_MS)
    }

    // ---- selection ---------------------------------------------------------

    /**
     * Order MTGA will try instances in: enabled, not backed off, healthiest and
     * fastest first, then the ones we have never tried, then everything else.
     */
    fun preferredOrder(now: Long = System.currentTimeMillis()): List<NitterInstance> {
        val healthMap = _health.value
        return _instances.value
            .filter { it.enabled }
            .sortedWith(
                compareBy(
                    { healthMap[it.id]?.isBackedOff(now) == true },
                    { healthMap[it.id]?.lastError != null },
                    { healthMap[it.id]?.lastCheckedAt == null },
                    { healthMap[it.id]?.latencyMillis ?: Long.MAX_VALUE }
                )
            )
    }

    /**
     * Runs [block] against instances in preferred order until one succeeds.
     * Failures are recorded, so a bad instance sinks in the ordering for the
     * next call instead of being retried first every time.
     */
    suspend fun <T> withInstance(block: suspend (NitterInstance) -> Outcome<T>): Outcome<T> {
        if (!connectivity.isOnline()) return Outcome.Failure(AppError.Offline)

        var candidates = preferredOrder()
        if (candidates.isEmpty()) {
            // On a fresh install the list may still be on its way. Waiting a
            // moment for it beats reporting an empty pool.
            listJob?.join()
            candidates = preferredOrder()
        }
        if (candidates.isEmpty()) return Outcome.Failure(AppError.NoHealthyInstance(emptyList()))

        val tried = mutableListOf<String>()
        var lastError: AppError? = null
        var notFoundVotes = 0
        var answered = 0
        var notFound: AppError? = null
        var passableCheck: AppError.ChallengeRequired? = null

        for (instance in candidates) {
            tried += instance.host
            when (val outcome = block(instance)) {
                is Outcome.Success -> {
                    record(ProbeResult(instance.id, 0, 200, null))
                    return outcome
                }
                is Outcome.Failure -> {
                    val error = outcome.error
                    lastError = error
                    record(ProbeResult(instance.id, 0, null, error))

                    // A suspended or protected account is a statement about X
                    // that any working instance would repeat, so stop.
                    if (error is AppError.AccountUnavailable) return outcome

                    // Remember the first check a person could complete, so
                    // the reader is offered that one rather than whatever
                    // firewall happened to be tried last.
                    if (passableCheck == null &&
                        error is AppError.ChallengeRequired &&
                        error.kind != ChallengeKind.WAF_BLOCK
                    ) {
                        passableCheck = error
                    }

                    if (spokeForUpstream(error)) answered++
                    if (error is AppError.AccountNotFound) {
                        notFoundVotes++
                        notFound = error
                    }
                }
            }
        }

        // "Not found" is only believed when every instance that actually
        // answered agrees. A Nitter instance with a broken upstream session
        // returns 404 for perfectly real accounts, and one dead host must not
        // be able to veto the whole pool.
        if (notFound != null && notFoundVotes == answered && answered > 0) {
            return Outcome.Failure(notFound)
        }

        return Outcome.Failure(
            passableCheck
                ?: lastError?.takeUnless { it is AppError.AccountNotFound }
                ?: AppError.NoHealthyInstance(tried)
        )
    }

    /** Did the instance actually respond, as opposed to failing in transport. */
    private fun spokeForUpstream(error: AppError): Boolean = when (error) {
        is AppError.AccountNotFound,
        is AppError.ParseFailure,
        is AppError.FeedGated -> true
        else -> false
    }

    // ---- editing -----------------------------------------------------------

    fun setEnabled(id: String, enabled: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun move(id: String, delta: Int) = mutate { list ->
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) {
            list
        } else {
            list.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id && !it.builtIn } }

    fun addCustom(rawUrl: String, rssUrl: String?): Boolean {
        val normalised = normaliseUrl(rawUrl) ?: return false
        val id = "custom-" + normalised.substringAfter("://").filter { it.isLetterOrDigit() }
        if (_instances.value.any { it.id == id }) return false

        mutate { list ->
            list + NitterInstance(
                id = id,
                label = normalised.substringAfter("://"),
                baseUrl = normalised,
                rssBaseUrl = rssUrl?.let(::normaliseUrl),
                builtIn = false
            )
        }
        return true
    }

    /** Fetches the list again and lets it decide every listed server's switch. */
    fun resetFromList() = updateListAsync(force = true, reset = true)

    /** https only, no exceptions. A privacy front end over plain HTTP is worse than none. */
    private fun normaliseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> "https://$trimmed"
        }
        val host = withScheme.substringAfter("://")
        return if (host.contains('.') && !host.contains(' ')) withScheme else null
    }

    private fun mutate(transform: (List<NitterInstance>) -> List<NitterInstance>) {
        val updated = transform(_instances.value)
        _instances.value = updated
        store.save(updated)
    }

    // ---- reporting ---------------------------------------------------------

    /** Plain text the user can paste into a bug report. No identifiers, no history. */
    fun diagnosticReport(): String = buildString {
        appendLine("MTGA diagnostic report")
        appendLine("device online: ${connectivity.isOnline()}")
        appendLine()
        _instances.value.forEach { instance ->
            val h = _health.value[instance.id]
            appendLine(instance.label)
            appendLine("  enabled: ${instance.enabled}")
            appendLine("  status: ${h?.status(instance.enabled) ?: "UNKNOWN"}")
            appendLine("  latency: ${h?.latencyMillis?.let { "${it}ms" } ?: "n/a"}")
            appendLine("  failures in a row: ${h?.consecutiveFailures ?: 0}")
            appendLine("  last error: ${h?.lastError?.let { it::class.java.simpleName } ?: "none"}")
            // The snippet is the only thing that distinguishes "our parser is
            // broken" from "the instance served an error page", so it belongs
            // in any report a human is going to read or send us.
            (h?.lastError as? AppError.ParseFailure)?.let { failure ->
                appendLine("  parser generation: v${failure.selectorSetVersion}")
                appendLine("  page said: ${failure.snippet ?: "nothing"}")
            }
            (h?.lastError as? AppError.FeedGated)?.let { gated ->
                appendLine("  gate id: ${gated.requestId ?: "unknown"}")
            }
            appendLine()
        }
    }

    private companion object {
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 15 * 60_000L
    }
}
