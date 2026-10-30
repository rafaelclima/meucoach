package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.ActivityAlternateEntity

@Dao
interface ActivityAlternateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(alternate: ActivityAlternateEntity)

    @Query("SELECT * FROM activity_alternates WHERE activityId = :activityId ORDER BY importedAt")
    suspend fun getForActivity(activityId: String): List<ActivityAlternateEntity>

    @Query("SELECT * FROM activity_alternates WHERE sourceFileSha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): ActivityAlternateEntity?

    @Query("DELETE FROM activity_alternates WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String): Int
}
