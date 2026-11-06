package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.meucoach.core.data.db.entity.DailyPrescriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPrescriptionDao {
    @Query(
        "SELECT * FROM daily_prescriptions WHERE date = :date " +
            "ORDER BY createdAt DESC, rowid DESC LIMIT 1",
    )
    fun observeLatest(date: String): Flow<DailyPrescriptionEntity?>

    @Query(
        "SELECT * FROM daily_prescriptions WHERE date = :date " +
            "ORDER BY createdAt DESC, rowid DESC LIMIT 1",
    )
    suspend fun latest(date: String): DailyPrescriptionEntity?

    @Query("SELECT * FROM daily_prescriptions WHERE date = :date ORDER BY createdAt, rowid")
    suspend fun versions(date: String): List<DailyPrescriptionEntity>

    @Query("SELECT * FROM daily_prescriptions ORDER BY date, createdAt, rowid")
    suspend fun getAll(): List<DailyPrescriptionEntity>

    @Insert
    suspend fun insert(prescription: DailyPrescriptionEntity)

    @Query("DELETE FROM daily_prescriptions")
    suspend fun deleteAll(): Int
}
