package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.LapEntity

@Dao
interface LapDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(laps: List<LapEntity>)

    @Query("SELECT * FROM laps WHERE activityId = :activityId ORDER BY lapIndex")
    suspend fun getForActivity(activityId: String): List<LapEntity>

    @Query("DELETE FROM laps WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String): Int
}
