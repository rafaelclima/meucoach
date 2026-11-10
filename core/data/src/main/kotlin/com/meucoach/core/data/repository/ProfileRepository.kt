package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.profile.toEntity
import com.meucoach.core.data.profile.toModel
import com.meucoach.core.model.AthleteProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Single local athlete profile, stored next to the activities. */
class ProfileRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.profileDao()

    fun observeProfile(): Flow<AthleteProfile?> = dao.observe().map { it?.toModel() }

    suspend fun getProfile(): AthleteProfile? = dao.get()?.toModel()

    suspend fun saveProfile(profile: AthleteProfile): AthleteProfile = database.withTransaction {
        val now = clock.millis()
        val existing = dao.get()
        val createdAt = existing?.takeIf { it.id == profile.id }?.createdAt ?: now
        // The profile is a singleton; replacing the row keeps the schema simple.
        dao.deleteAll()
        dao.upsert(profile.toEntity(createdAt = createdAt, updatedAt = now))
        profile
    }

    suspend fun deleteProfile(): Int = dao.deleteAll()
}
