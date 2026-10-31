package com.meucoach.core.data.health

import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
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
 * Shared Health Connect sync used by the profile screen and the periodic
 * worker. The first run reads 30 days; later runs start one day before the
 * last success to catch late-arriving records. Successful runs persist the
 * date; permission/error runs do not, so they retry the same window.
 */
class HealthSyncService(
    private val source: HealthDailySource,
    private val metricsRepository: DailyMetricsRepository,
    private val profileRepository: ProfileRepository,
    private val syncStore: HealthSyncStore,
) {
    suspend fun sync(): HealthSyncResult {
        val profile = profileRepository.getProfile()
        val zone = profile
            ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val today = LocalDate.now(zone)
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
        if (result.error == null && result.missingPermissions.isEmpty()) {
            if (result.days.isNotEmpty()) metricsRepository.saveAll(result.days)
            syncStore.markSynced(today)
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
    }
}
