package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.SampleBlobEntity

@Dao
interface SampleBlobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(blob: SampleBlobEntity)

    @Query("SELECT * FROM sample_blobs WHERE activityId = :activityId")
    suspend fun getForActivity(activityId: String): SampleBlobEntity?

    @Query("DELETE FROM sample_blobs WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String): Int
}
