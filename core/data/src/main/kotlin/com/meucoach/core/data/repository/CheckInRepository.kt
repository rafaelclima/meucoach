package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.DailyCheckInEntity
import com.meucoach.core.model.DailyCheckIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class CheckInRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.dailyCheckInDao()

    fun observe(date: LocalDate): Flow<DailyCheckIn?> = dao.observe(date.toString()).map { it?.toModel() }

    fun observeAll(): Flow<List<DailyCheckIn>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(date: LocalDate): DailyCheckIn? = dao.get(date.toString())?.toModel()

    suspend fun save(checkIn: DailyCheckIn): DailyCheckIn = database.withTransaction {
        val now = clock.millis()
        val createdAt = dao.get(checkIn.date.toString())?.createdAt ?: now
        dao.upsert(checkIn.toEntity(createdAt = createdAt, updatedAt = now))
        checkIn
    }

    suspend fun delete(date: LocalDate): Boolean = dao.delete(date.toString()) > 0

    private fun DailyCheckInEntity.toModel() = DailyCheckIn(
        date = LocalDate.parse(date),
        sleepQuality = sleepQuality,
        fatigue = fatigue,
        soreness = soreness,
        stress = stress,
        illnessOrInjury = illnessOrInjury,
        heatLevel = heatLevel,
    )

    private fun DailyCheckIn.toEntity(createdAt: Long, updatedAt: Long) = DailyCheckInEntity(
        date = date.toString(),
        sleepQuality = sleepQuality,
        fatigue = fatigue,
        soreness = soreness,
        stress = stress,
        illnessOrInjury = illnessOrInjury,
        heatLevel = heatLevel,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
