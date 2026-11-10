package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted race event (ADR 0030).
 *
 * [activeKey] materializes "at most one PLANNED event" with a unique index:
 * SQLite treats multiple NULLs as distinct, so finished events coexist. The
 * optional [activityId] is unique and cleared by the database when the activity
 * is permanently deleted; the event itself is preserved.
 */
@Entity(
    tableName = "race_events",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["targetDate"]),
        Index(value = ["activityId"], unique = true),
        Index(value = ["activeKey"], unique = true),
    ],
)
data class RaceEventEntity(
    @PrimaryKey val id: String,
    val distance: String,
    /** Local date in the profile timezone; ISO-8601. */
    val targetDate: String,
    val targetTimeSec: Int?,
    val status: String,
    val activityId: String?,
    /** "ACTIVE" only for a PLANNED event; null for finished history. */
    val activeKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
)
