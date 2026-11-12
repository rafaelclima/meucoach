package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meucoach.core.data.db.entity.ImportJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: ImportJobEntity)

    @Update
    suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun findById(id: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportJobEntity>>

    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    suspend fun getAll(): List<ImportJobEntity>

    /**
     * Closes jobs that a killed process left mid-import. Only the two
     * non-terminal states are touched, so a new terminal state added later is
     * never rewritten by accident.
     */
    @Query(
        """
        UPDATE import_jobs
        SET status = 'FAILED', errorCode = :errorCode, errorMessage = :message, finishedAt = :finishedAt
        WHERE status IN ('QUEUED', 'PARSING') AND createdAt < :createdBefore
        """,
    )
    suspend fun failInterrupted(createdBefore: Long, finishedAt: Long, errorCode: String, message: String): Int

    @Query("DELETE FROM import_jobs WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String): Int

    @Query("DELETE FROM import_jobs")
    suspend fun deleteAll(): Int
}
