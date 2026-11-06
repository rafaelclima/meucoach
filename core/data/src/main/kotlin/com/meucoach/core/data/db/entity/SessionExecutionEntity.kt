package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.model.ExecutionSource

/**
 * One execution of a planned session (ADR 0024).
 *
 * The unique indexes enforce the two 1:1 rules at the database level: a session
 * has at most one execution and an activity is never linked to two sessions.
 * SQLite keeps multiple NULLs distinct, so manual/guided rows without an
 * activity coexist.
 */
@Entity(
    tableName = "session_executions",
    indices = [
        Index(value = ["plannedSessionId"], unique = true),
        Index(value = ["activityId"], unique = true),
    ],
)
data class SessionExecutionEntity(
    @PrimaryKey val id: String,
    val plannedSessionId: String?,
    val activityId: String?,
    val startedAt: Long?,
    val endedAt: Long?,
    val pausesJson: String,
    val durationSec: Int,
    val distanceM: Double?,
    val rpe: Int?,
    val source: ExecutionSource,
    val createdAt: Long,
)
