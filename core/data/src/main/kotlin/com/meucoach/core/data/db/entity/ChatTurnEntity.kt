package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted turn of the Coach conversation (Room v20, ADR 0054).
 *
 * Unifies `coach_messages` and `coach_summaries`: both only ever hold text
 * that already passed `CoachResponseValidator`, so a turn is copy context,
 * never a source of numbers, pace, heart rate or load. `role` is `COACH` in
 * v20; `YOU`/`SYSTEM` are reserved for their own slices.
 *
 * `expiresAt` carries the TTL by origin (90 d for EXPLAIN/REVIEW, 180 d for
 * SUMMARY, per `CoachMemoryPolicy`) computed at write time, so pruning is a
 * single `expiresAt < now` pass. IDs are derived and stable (`message-{id}`,
 * `summary-{weekStart}`) to keep the backfill idempotent.
 */
@Entity(
    tableName = "chat_turns",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["expiresAt"]),
        Index(value = ["origin", "weekStart"], unique = true),
        Index(value = ["origin", "inputsHash"], unique = true),
    ],
)
data class ChatTurnEntity(
    @PrimaryKey val id: String,
    val role: String,
    val title: String,
    val body: String,
    /** JSON array of the validated bullets. */
    val bulletsJson: String,
    val origin: String,
    /** Only set for `SUMMARY` turns. */
    val weekStart: String?,
    val createdAt: Long,
    val expiresAt: Long,
    /**
     * Motor context that produced the validated text. `null` only on legacy
     * rows the 20→21 migration could not match to a v19 row; new writes
     * always set it so reopening the same context never duplicates the turn.
     */
    val inputsHash: String? = null,
)
