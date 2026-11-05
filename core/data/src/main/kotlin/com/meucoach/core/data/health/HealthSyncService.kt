package com.meucoach.core.data.health

import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthSyncResult(
    val importedDays: Int,
    val missingPermissions: Set<String>,
    val error: String? = null,
    val from: LocalDate,
    val to: LocalDate,
    val incremental: Boolean,
)

/**
 * Shared Health Connect sync used by the app resume, the profile screen and the
 * periodic worker. The first run reads 30 days; later runs start one day before
 * the last success to catch late-arriving records. Successful runs persist the
 * date; permission/error runs do not, so they retry the same window.
 */
class HealthSyncService(
    private val source: HealthDailySource,
    private val metricsRepository: DailyMetricsRepository,
    private val profileRepository: ProfileRepository,
    private val syncStore: HealthSyncStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()

    /** Manual/worker sync. Bypasses the freshness window on purpose. */
    suspend fun sync(): HealthSyncResult = mutex.withLock { syncLocked() }

    /**
     * Automatic sync used when the app resumes. Returns null when the last
     * success is fresh enough or a recent failure is still in backoff, so the
     * caller does not need to know the policy.
     */
    suspend fun syncIfStale(
        maxAge: Duration = STALE_AFTER,
        retryBackoff: Duration = RETRY_BACKOFF,
    ): HealthSyncResult? = mutex.withLock {
        val now = clock.instant()
        val lastSyncedAt = syncStore.lastSyncedAt()
        if (lastSyncedAt != null && Duration.between(lastSyncedAt, now) < maxAge) {
            return@withLock null
        }
        val lastAttemptAt = syncStore.lastAttemptAt()
        if (lastAttemptAt != null && Duration.between(lastAttemptAt, now) < retryBackoff) {
            return@withLock null
        }
        syncLocked(now)
    }

    private suspend fun syncLocked(now: Instant = clock.instant()): HealthSyncResult {
        val profile = profileRepository.getProfile()
        val zone = profile
            ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        if (source.status() != HealthConnectStatus.AVAILABLE) {
            return HealthSyncResult(
                importedDays = 0,
                missingPermissions = source.requiredPermissions,
                error = "Health Connect indisponível neste aparelho.",
                from = today,
                to = today,
                incremental = false,
            )
        }

        val lastSynced = syncStore.lastSyncedDate()
        val from = lastSynced?.minusDays(1) ?: today.minusDays(WINDOW_DAYS - 1L)
        val safeFrom = if (from.isAfter(today)) today else from
        val result = source.read(from = safeFrom, to = today, zoneId = zone)
        when {
            result.error != null -> syncStore.markAttempt(now)

            result.missingPermissions.isEmpty() -> {
                if (result.days.isNotEmpty()) metricsRepository.saveAll(result.days)
                syncStore.markSynced(now, today)
            }
        }
        return HealthSyncResult(
            importedDays = result.days.size,
            missingPermissions = result.missingPermissions,
            error = result.error,
            from = safeFrom,
            to = today,
            incremental = lastSynced != null,
        )
    }

    companion object {
        const val WINDOW_DAYS = 30L
        val STALE_AFTER: Duration = Duration.ofHours(6)
        val RETRY_BACKOFF: Duration = Duration.ofMinutes(15)
    }
}
