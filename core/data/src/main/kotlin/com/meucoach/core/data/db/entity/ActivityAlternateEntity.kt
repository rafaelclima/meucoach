package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.model.ActivitySource

/**
 * A second file that describes an activity already stored in [ActivityEntity].
 *
 * The canonical row keeps the richest source (FIT > TCX > GPX); every other
 * file is recorded here so provenance is not lost when a richer format
 * replaces the aggregate.
 */
@Entity(
    tableName = "activity_alternates",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("activityId"),
        Index(value = ["sourceFileSha256"], unique = true),
    ],
)
data class ActivityAlternateEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val source: ActivitySource,
    val sourceId: String?,
    val sourceFileSha256: String?,
    val rawFilePath: String?,
    val parserVersion: String,
    val importedAt: Long,
    val sampleCount: Int,
    val durationSec: Int,
    val distanceM: Double,
)
