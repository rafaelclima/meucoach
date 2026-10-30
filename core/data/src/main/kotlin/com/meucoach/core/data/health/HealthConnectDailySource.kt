package com.meucoach.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.meucoach.core.domain.health.HealthMetricsAggregator
import com.meucoach.core.domain.health.HealthRawData
import com.meucoach.core.domain.health.HealthSample
import com.meucoach.core.domain.health.HealthSleepSession
import com.meucoach.core.model.DailyMetrics
import java.time.LocalDate
import java.time.ZoneId

enum class HealthConnectStatus {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class HealthConnectReadResult(
    val days: List<DailyMetrics>,
    val missingPermissions: Set<String>,
    val error: String? = null,
)

interface HealthDailySource {
    val requiredPermissions: Set<String>

    suspend fun status(): HealthConnectStatus

    suspend fun grantedPermissions(): Set<String>

    suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult
}

/**
 * Android Health Connect adapter. The pure aggregation lives in core:domain;
 * this class only talks to the platform and maps records to canonical inputs.
 */
class HealthConnectDailySource(context: Context) : HealthDailySource {
    private val appContext = context.applicationContext

    override val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    override suspend fun status(): HealthConnectStatus = when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
        else -> HealthConnectStatus.UNAVAILABLE
    }

    override suspend fun grantedPermissions(): Set<String> {
        if (status() != HealthConnectStatus.AVAILABLE) return emptySet()
        return runCatching {
            HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
        }.getOrElse { emptySet() }
    }

    override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult {
        if (status() != HealthConnectStatus.AVAILABLE) {
            return HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = requiredPermissions,
                error = "Health Connect indisponível neste aparelho.",
            )
        }
        val client = runCatching { HealthConnectClient.getOrCreate(appContext) }.getOrElse {
            return HealthConnectReadResult(emptyList(), requiredPermissions, "Health Connect indisponível.")
        }
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrElse { emptySet() }
        val missing = requiredPermissions - granted
        if (missing.isNotEmpty()) {
            return HealthConnectReadResult(emptyList(), missing)
        }

        val fromInstant = from.atStartOfDay(zoneId).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant()
        return runCatching {
            val raw = HealthRawData(
                sleepSessions = client.readAll(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(fromInstant, toInstant),
                    ),
                ).map { HealthSleepSession(it.startTime, it.endTime) },
                restingHeartRates = client.readAll(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(fromInstant, toInstant),
                    ),
                ).map { HealthSample(it.time, it.beatsPerMinute.toDouble()) },
                hrvRmssd = client.readAll(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(fromInstant, toInstant),
                    ),
                ).map { HealthSample(it.time, it.heartRateVariabilityMillis) },
                oxygenSaturation = client.readAll(
                    ReadRecordsRequest(
                        recordType = OxygenSaturationRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(fromInstant, toInstant),
                    ),
                ).map { HealthSample(it.time, it.percentage.value) },
            )
            HealthConnectReadResult(
                days = HealthMetricsAggregator.aggregate(raw, zoneId, from, to),
                missingPermissions = emptySet(),
            )
        }.getOrElse { error ->
            HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = error.message ?: "Não foi possível ler o Health Connect.",
            )
        }
    }

    private suspend fun <T : Record> HealthConnectClient.readAll(request: ReadRecordsRequest<T>): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = readRecords(
                ReadRecordsRequest(
                    recordType = request.recordType,
                    timeRangeFilter = request.timeRangeFilter,
                    ascendingOrder = request.ascendingOrder,
                    pageSize = request.pageSize,
                    pageToken = token,
                ),
            )
            records += response.records
            token = response.pageToken
            pages++
        } while (token != null && pages < MAX_PAGES)
        return records
    }

    private companion object {
        const val MAX_PAGES = 100
    }
}
