package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sample_blobs",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SampleBlobEntity(
    @PrimaryKey val activityId: String,
    val version: Int,
    val encoding: String,
    val bytes: ByteArray,
    val sampleCount: Int,
    val startTimeUtc: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SampleBlobEntity) return false
        return activityId == other.activityId &&
            version == other.version &&
            encoding == other.encoding &&
            bytes.contentEquals(other.bytes) &&
            sampleCount == other.sampleCount &&
            startTimeUtc == other.startTimeUtc
    }

    override fun hashCode(): Int {
        var result = activityId.hashCode()
        result = 31 * result + version
        result = 31 * result + encoding.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + sampleCount
        result = 31 * result + startTimeUtc.hashCode()
        return result
    }
}
