package com.meucoach.core.data.db

import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Sport

internal fun testActivity(id: String = "activity-1", sourceFileSha256: String? = null, deletedAt: Long? = null) =
    ActivityEntity(
        id = id,
        source = ActivitySource.FIT,
        sourceId = "source-1",
        sourceFileSha256 = sourceFileSha256,
        sport = Sport.RUNNING,
        subSport = "street",
        startTimeUtc = 1_700_000_000_000,
        zoneId = "America/Sao_Paulo",
        localDate = "2023-11-14",
        durationSec = 1_800,
        movingTimeSec = 1_750,
        distanceM = 5_000.0,
        averagePaceSecPerKm = 360.0,
        bestPaceSecPerKm = 320.0,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averageCadenceSpm = 170,
        maxCadenceSpm = 180,
        averagePowerW = 250,
        maxPowerW = 300,
        averageStepLengthM = 1.1,
        elevationGainM = 40.0,
        elevationLossM = 35.0,
        calories = 320,
        trainingEffectAerobic = 3.2,
        trainingEffectAnaerobic = 0.4,
        trainingLoadPeak = 120.0,
        deviceName = "Amazfit Balance",
        rawFilePath = "imports/activity.fit",
        parserVersion = "1.0.0",
        importedAt = 1_700_000_100_000,
        deletedAt = deletedAt,
    )

internal fun testLap(activityId: String = "activity-1") = LapEntity(
    activityId = activityId,
    lapIndex = 0,
    startTimeUtc = 1_700_000_000_000,
    durationSec = 900,
    distanceM = 2_500.0,
    averageHeartRate = 148,
    maxHeartRate = 160,
    averagePaceSecPerKm = 360.0,
    averageCadenceSpm = 168,
    averagePowerW = 240,
    elevationGainM = 20.0,
)

internal fun testBlob(activityId: String = "activity-1") = SampleBlobEntity(
    activityId = activityId,
    version = 1,
    encoding = "gzip-columnar-v1",
    bytes = byteArrayOf(1, 2, 3, 4),
    sampleCount = 4,
    startTimeUtc = 1_700_000_000_000,
)

internal fun testJob(activityId: String? = "activity-1") = ImportJobEntity(
    id = "job-1",
    fileName = "activity.fit",
    sha256 = "abc123",
    mime = "application/vnd.garmin.fit",
    sizeBytes = 12_345,
    status = ImportJobStatus.DONE,
    parserVersion = "1.0.0",
    activityId = activityId,
    errorCode = null,
    errorMessage = null,
    createdAt = 1_700_000_000_000,
    finishedAt = 1_700_000_100_000,
)
