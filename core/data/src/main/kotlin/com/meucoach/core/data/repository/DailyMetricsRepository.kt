package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.DailyMetricsEntity
import com.meucoach.core.model.DailyMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class DailyMetricsRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.dailyMetricsDao()

    fun observeAll(): Flow<List<DailyMetrics>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun getAll(): List<DailyMetrics> = dao.getAll().map { it.toModel() }

    suspend fun get(date: LocalDate): DailyMetrics? = dao.get(date.toString())?.toModel()

    suspend fun saveAll(metrics: List<DailyMetrics>): Int = database.withTransaction {
        val now = clock.millis()
        metrics.forEach { row ->
            val createdAt = dao.get(row.date.toString())?.createdAt ?: now
            dao.upsert(row.toEntity(createdAt = createdAt, updatedAt = now))
        }
        metrics.size
    }

    suspend fun delete(date: LocalDate): Boolean = dao.delete(date.toString()) > 0

    private fun DailyMetricsEntity.toModel() = DailyMetrics(
        date = LocalDate.parse(date),
        sleepDurationMin = sleepDurationMin,
        restingHeartRate = restingHeartRate,
        hrvRmssdMs = hrvRmssdMs,
        spo2Avg = spo2Avg,
        source = source,
    )

    private fun DailyMetrics.toEntity(createdAt: Long, updatedAt: Long) = DailyMetricsEntity(
        date = date.toString(),
        sleepDurationMin = sleepDurationMin,
        restingHeartRate = restingHeartRate,
        hrvRmssdMs = hrvRmssdMs,
        spo2Avg = spo2Avg,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
