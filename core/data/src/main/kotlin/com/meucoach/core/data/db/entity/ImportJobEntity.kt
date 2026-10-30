package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportJobStatus {
    QUEUED,
    PARSING,
    DONE,
    FAILED,
    DUPLICATE,
}

@Entity(
    tableName = "import_jobs",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("activityId"),
        Index("sha256"),
    ],
)
data class ImportJobEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val sha256: String,
    val mime: String?,
    val sizeBytes: Long,
    val status: ImportJobStatus,
    val parserVersion: String?,
    val activityId: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val finishedAt: Long?,
)
