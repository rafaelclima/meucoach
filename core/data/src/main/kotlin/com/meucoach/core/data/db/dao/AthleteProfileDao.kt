package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteProfileDao {
    @Query("SELECT * FROM athlete_profile LIMIT 1")
    fun observe(): Flow<AthleteProfileEntity?>

    @Query("SELECT * FROM athlete_profile LIMIT 1")
    suspend fun get(): AthleteProfileEntity?

    @Upsert
    suspend fun upsert(profile: AthleteProfileEntity)

    @Query("DELETE FROM athlete_profile")
    suspend fun deleteAll(): Int
}
