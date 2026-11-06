package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.model.Sport
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(activity: ActivityEntity)

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun findByIdIncludingDeleted(id: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE sourceFileSha256 = :sha256 AND deletedAt IS NULL LIMIT 1")
    suspend fun findBySha256(sha256: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE sourceFileSha256 = :sha256 LIMIT 1")
    suspend fun findBySha256IncludingDeleted(sha256: String): ActivityEntity?

    @Query(
        "SELECT * FROM activities WHERE deletedAt IS NULL AND sport = :sport " +
            "AND startTimeUtc BETWEEN :fromUtc AND :toUtc ORDER BY startTimeUtc",
    )
    suspend fun findCandidates(sport: Sport, fromUtc: Long, toUtc: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE deletedAt IS NULL ORDER BY startTimeUtc DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE deletedAt IS NULL ORDER BY startTimeUtc DESC")
    suspend fun getAll(): List<ActivityEntity>

    @Query("SELECT MAX(importedAt) FROM activities WHERE deletedAt IS NULL")
    fun observeLastImportedAt(): Flow<Long?>

    @Query("SELECT MAX(importedAt) FROM activities WHERE deletedAt IS NULL")
    suspend fun lastImportedAt(): Long?

    @Query("SELECT * FROM activities WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrash(): List<ActivityEntity>

    @Query("SELECT id FROM activities WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedIds(): List<String>

    @Query("SELECT * FROM activities WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun getDeletedBefore(cutoff: Long): List<ActivityEntity>

    @Query("DELETE FROM activities WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun hardDeleteBefore(cutoff: Long): Int

    @Query("SELECT * FROM activities ORDER BY startTimeUtc DESC")
    suspend fun getAllIncludingDeleted(): List<ActivityEntity>

    @Query("UPDATE activities SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, deletedAt: Long): Int

    @Query("UPDATE activities SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String): Int

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun hardDelete(id: String): Int

    @Query("DELETE FROM activities")
    suspend fun deleteAll(): Int
}
