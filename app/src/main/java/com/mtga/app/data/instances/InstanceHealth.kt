package com.mtga.app.data.instances

import com.mtga.app.core.common.AppError

/**
 * CHALLENGED is its own state on purpose. An instance behind a bot check is
 * usually healthy, and calling it DOWN would invite the user to disable it.
 */
enum class HealthStatus { UNKNOWN, HEALTHY, SLOW, DEGRADED, DOWN, DISABLED, CHALLENGED }

/**
 * Live health of one instance. Deliberately not persisted: a health reading
 * from three days ago is worse than no reading, because it invites the app to
 * act on a stale assumption.
 */
data class InstanceHealth(
    val instanceId: String,
    val lastCheckedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val latencyMillis: Long? = null,
    val lastError: AppError? = null,
    val consecutiveFailures: Int = 0,
    val backoffUntilMillis: Long? = null
) {
    fun status(enabled: Boolean, now: Long = System.currentTimeMillis()): HealthStatus = when {
        !enabled -> HealthStatus.DISABLED
        lastCheckedAt == null -> HealthStatus.UNKNOWN
        lastError == null && (latencyMillis ?: 0) > SLOW_THRESHOLD_MS -> HealthStatus.SLOW
        lastError == null -> HealthStatus.HEALTHY
        lastError is AppError.ChallengeRequired -> HealthStatus.CHALLENGED
        consecutiveFailures >= DOWN_AFTER_FAILURES -> HealthStatus.DOWN
        else -> HealthStatus.DEGRADED
    }

    fun isBackedOff(now: Long = System.currentTimeMillis()): Boolean =
        backoffUntilMillis != null && now < backoffUntilMillis

    companion object {
        const val SLOW_THRESHOLD_MS = 2_500L
        const val DOWN_AFTER_FAILURES = 3
    }
}
