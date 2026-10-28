package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Decodes the FIT activity subset used by Zepp/Amazfit files.
 *
 * The parser is intentionally deterministic and side-effect free. It validates
 * the FIT framing and CRC, reads little- and big-endian definitions, supports
 * compressed timestamps and developer fields, and normalizes running cadence
 * from strides/min to steps/min.
 */
class FitParser {
    fun parse(bytes: ByteArray): FitParseResult {
        val decoded = FitReader(bytes).read()
        val warnings = decoded.warnings.toMutableList()
        val messages = decoded.messages
        val fileIdMessage = messages.firstOrNull { it.globalNumber == MESSAGE_FILE_ID }
        val fileId = fileIdMessage?.toFileId()
        if (fileId == null) {
            warnings.add(FitWarning(FitWarningCode.MISSING_FILE_ID, "FIT file has no file_id"))
        }
        val deviceMessage = messages.firstOrNull { it.globalNumber == MESSAGE_DEVICE_INFO }
        val device = deviceMessage?.toDeviceInfo()
        val sessions = messages.filter { it.globalNumber == MESSAGE_SESSION }
        if (sessions.size > 1) {
            warnings.add(
                FitWarning(
                    FitWarningCode.MULTIPLE_SESSIONS,
                    "FIT file has ${sessions.size} sessions; using the first",
                ),
            )
        }
        val session = sessions.firstOrNull()
        if (session == null) {
            warnings.add(
                FitWarning(FitWarningCode.MISSING_SESSION, "FIT file has no session message"),
            )
        }
        val records = messages.filter { it.globalNumber == MESSAGE_RECORD }
        val events = messages.filter { it.globalNumber == MESSAGE_EVENT }
        val activityMessage = messages.lastOrNull { it.globalNumber == MESSAGE_ACTIVITY }

        val startTime = session?.instant(2)
            ?: events.firstOrNull { it.fieldInt(0) == EVENT_TIMER && it.fieldInt(1) == EVENT_START }
                ?.timestamp
            ?: records.firstOrNull()?.timestamp
            ?: activityMessage?.instant(TIMESTAMP_FIELD)
            ?: fileId?.timeCreated
            ?: throw FitParseException(
                FitErrorCode.MISSING_ACTIVITY,
                "No session, event or record timestamp to anchor the activity",
            )

        val sportValue = session?.fieldInt(5)
        val subSportValue = session?.fieldInt(6)
        val sport = mapSport(sportValue, subSportValue)
        if (sportValue == null) {
            warnings.add(
                FitWarning(FitWarningCode.UNKNOWN_SPORT, "FIT file has no sport; using OTHER"),
            )
        }
        val subSport = subSportName(subSportValue)

        val durationSec = (
            session?.seconds(7)?.takeIf { it > 0.0 }
                ?: session?.seconds(8)?.takeIf { it > 0.0 }
                ?: activityMessage?.seconds(0)?.takeIf { it > 0.0 }
                ?: records.lastOrNull()?.timestamp
                    ?.let { Duration.between(startTime, it).seconds.toDouble() }
                    ?.takeIf { it > 0.0 }
            )?.roundToInt()
            ?: throw FitParseException(
                FitErrorCode.INVALID_DURATION,
                "FIT activity has no positive duration",
            )

        val distanceM = session?.scaled(9, 100.0)?.takeIf { it > 0.0 }
            ?: records.lastOrNull()?.scaled(5, 100.0)
            ?: 0.0
        val movingTimeSec = (
            session?.seconds(8)?.takeIf { it > 0.0 }
                ?: activityMessage?.seconds(0)?.takeIf { it > 0.0 }
            )?.roundToInt()

        val samples = buildSamples(
            records = records,
            startTime = startTime,
            sport = sport,
            warnings = warnings,
        )
        val averageHeartRate = session?.intIn(16, 30..250)
            ?: samples.mapNotNull { it.heartRate }.averageOrNull()
        val maxHeartRate = session?.intIn(17, 30..250)
            ?: samples.mapNotNull { it.heartRate }.maxOrNull()
        val averageCadence = session?.intIn(18, 1..150)?.times(2)
            ?: samples.mapNotNull { it.cadenceSpm }.filter { it > 0 }.averageOrNull()
        val maxCadence = session?.intIn(19, 1..150)?.times(2)
            ?: samples.mapNotNull { it.cadenceSpm }.maxOrNull()
        val averagePower = session?.intIn(20, 0..2500)
            ?: samples.mapNotNull { it.powerW }.averageOrNull()
        val maxPower = session?.intIn(21, 0..2500)
            ?: samples.mapNotNull { it.powerW }.maxOrNull()
        val averagePace = averagePace(
            durationSec = durationSec,
            distanceM = distanceM,
            session = session,
        )
        val averageStepLength = session?.scaled(134, 10.0)?.div(1_000.0)
            ?.takeIf { it > 0.0 }

        val laps = messages
            .filter { it.globalNumber == MESSAGE_LAP }
            .mapIndexed { index, lap -> lap.toLap(index) }

        val activity = ImportedActivity(
            source = ActivitySource.FIT,
            sport = sport,
            startTime = startTime,
            durationSec = durationSec,
            distanceM = distanceM,
            sourceId = fileId?.serialNumber?.toString(),
            subSport = subSport,
            movingTimeSec = movingTimeSec,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate,
            averagePace = averagePace,
            averageCadenceSpm = averageCadence,
            maxCadenceSpm = maxCadence,
            averagePowerW = averagePower,
            maxPowerW = maxPower,
            averageStepLengthM = averageStepLength,
            elevationGainM = session?.scaled(22, 1.0),
            elevationLossM = session?.scaled(23, 1.0),
            calories = session?.intIn(11, 0..100_000),
            laps = laps,
            samples = samples,
            deviceName = device?.productName ?: fileId?.productName,
            parserVersion = PARSER_VERSION,
        )

        return FitParseResult(
            activity = activity,
            fileId = fileId,
            device = device,
            warnings = warnings.distinct(),
        )
    }

    private fun buildSamples(
        records: List<DecodedMessage>,
        startTime: Instant,
        sport: Sport,
        warnings: MutableList<FitWarning>,
    ): List<ActivitySample> {
        val normalizeCadence = sport == Sport.RUNNING || sport == Sport.TREADMILL
        val samples = mutableListOf<ActivitySample>()
        for (record in records) {
            val timestamp = record.timestamp
            if (timestamp == null) {
                warnings.add(
                    FitWarning(
                        FitWarningCode.MISSING_TIMESTAMP,
                        "Record without timestamp was ignored",
                    ),
                )
                continue
            }
            val offset = Duration.between(startTime, timestamp).seconds
            if (offset < 0) {
                warnings.add(
                    FitWarning(
                        FitWarningCode.NEGATIVE_SAMPLE_OFFSET,
                        "Sample before activity start at $timestamp",
                    ),
                )
                continue
            }
            if (offset > Int.MAX_VALUE) {
                warnings.add(
                    FitWarning(
                        FitWarningCode.NEGATIVE_SAMPLE_OFFSET,
                        "Sample offset is too large at $timestamp",
                    ),
                )
                continue
            }
            val rawCadence = record.intIn(4, 0..150)
            val cadence = if (normalizeCadence) rawCadence?.times(2) else rawCadence
            val developerFields = record.developerFields.mapNotNull { (name, value) ->
                value.asNumber()?.let { name to it }
            }.toMap()
            samples += ActivitySample(
                offsetSec = offset.toInt(),
                timestamp = timestamp,
                latitude = semicirclesToDegrees(record.fieldDouble(0), latitude = true),
                longitude = semicirclesToDegrees(record.fieldDouble(1), latitude = false),
                altitudeM = record.altitudeMeters(),
                speedMps = record.speedMetersPerSecond()?.takeIf { it in 0.0..30.0 },
                heartRate = record.intIn(3, 30..250),
                cadenceSpm = cadence?.takeIf { it in 0..300 },
                powerW = record.intIn(7, 0..2500),
                stepLengthM = record.stepLengthMeters()?.takeIf { it in 0.0..3.0 },
                developerFields = developerFields.filterValues { it.isFinite() },
            )
        }
        return samples.sortedBy { it.offsetSec }
    }

    private fun DecodedMessage.toLap(index: Int): ActivityLap {
        val lapDuration = (seconds(8)?.takeIf { it > 0.0 } ?: seconds(7) ?: 0.0).roundToInt()
        val speed = scaled(13, 1000.0) ?: scaled(110, 1000.0)
        return ActivityLap(
            index = fieldInt(254) ?: index,
            durationSec = lapDuration,
            distanceM = scaled(9, 100.0) ?: 0.0,
            startTime = instant(2),
            averageHeartRate = intIn(15, 30..250),
            maxHeartRate = intIn(16, 30..250),
            averagePace = speed?.takeIf { it > 0.0 }?.let(Pace::fromSpeedMetersPerSecond),
            averageCadenceSpm = intIn(17, 0..300),
            averagePowerW = intIn(19, 0..2500),
            elevationGainM = scaled(21, 1.0),
        )
    }

    private fun DecodedMessage.toFileId(): FitFileId = FitFileId(
        fileType = fieldInt(0)?.let(::fileTypeName),
        manufacturer = fieldInt(1),
        product = fieldInt(2),
        serialNumber = fieldLong(3),
        timeCreated = instant(4),
        productName = fieldText(8),
    )

    private fun DecodedMessage.toDeviceInfo(): FitDeviceInfo = FitDeviceInfo(
        productName = fieldText(27),
        manufacturer = fieldInt(2),
        serialNumber = fieldLong(3),
        source = developerFields["source"]?.asText(),
        timestamp = timestamp,
    )

    private fun averagePace(durationSec: Int, distanceM: Double, session: DecodedMessage?): Pace? {
        if (distanceM > 0.0) {
            val secondsPerKm = (durationSec / (distanceM / 1_000.0)).roundToInt()
            if (secondsPerKm > 0) return Pace.fromSecondsPerKm(secondsPerKm)
        }
        val speed = session?.scaled(14, 1000.0) ?: session?.scaled(124, 1000.0)
        return speed?.takeIf { it > 0.0 }?.let(Pace::fromSpeedMetersPerSecond)
    }

    private fun DecodedMessage.altitudeMeters(): Double? {
        val field = if (fields.containsKey(78)) 78 else 2
        val raw = fieldDouble(field) ?: return null
        val baseType = fieldTypes[field]
        val meters = if (baseType == FitBaseType.FLOAT32 || baseType == FitBaseType.FLOAT64) {
            raw
        } else {
            raw / 5.0 - 500.0
        }
        return meters.takeIf { it in -500.0..9000.0 }
    }

    private fun DecodedMessage.speedMetersPerSecond(): Double? {
        val field = if (fields.containsKey(73)) 73 else 6
        val raw = fieldDouble(field) ?: return null
        val baseType = fieldTypes[field]
        return if (baseType == FitBaseType.FLOAT32 || baseType == FitBaseType.FLOAT64) {
            raw
        } else {
            raw / 1_000.0
        }
    }

    private fun DecodedMessage.stepLengthMeters(): Double? {
        val raw = fieldDouble(85) ?: return null
        val baseType = fieldTypes[85]
        return if (baseType == FitBaseType.FLOAT32 || baseType == FitBaseType.FLOAT64) {
            raw
        } else {
            raw / 10.0 / 1_000.0
        }
    }

    private fun semicirclesToDegrees(raw: Double?, latitude: Boolean): Double? {
        val degrees = raw?.times(180.0 / 2_147_483_648.0) ?: return null
        val range = if (latitude) -90.0..90.0 else -180.0..180.0
        return degrees.takeIf { it in range }
    }

    private fun DecodedMessage.fieldInt(field: Int): Int? = fields[field]?.asNumber()?.roundToInt()

    private fun DecodedMessage.fieldLong(field: Int): Long? = fields[field]?.asNumber()?.toLong()

    private fun DecodedMessage.fieldDouble(field: Int): Double? = fields[field]?.asNumber()

    private fun DecodedMessage.fieldText(field: Int): String? = fields[field]?.asText()?.takeIf { it.isNotBlank() }

    private fun DecodedMessage.intIn(field: Int, range: IntRange): Int? = fieldInt(field)?.takeIf { it in range }

    private fun DecodedMessage.scaled(field: Int, divisor: Double): Double? = fieldDouble(field)?.div(divisor)

    private fun DecodedMessage.seconds(field: Int): Double? = scaled(field, 1_000.0)

    private fun DecodedMessage.instant(field: Int): Instant? {
        if (field == TIMESTAMP_FIELD) return timestamp
        return fieldDouble(field)?.toLong()?.let(::fitTimestamp)
    }

    private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private fun List<Int>.maxOrNull(): Int? = if (isEmpty()) null else max()

    private fun mapSport(sport: Int?, subSport: Int?): Sport = when {
        sport == SPORT_RUNNING && (subSport == SUB_SPORT_TREADMILL || subSport == SUB_SPORT_INDOOR_RUNNING) ->
            Sport.TREADMILL

        sport == SPORT_RUNNING -> Sport.RUNNING

        sport == SPORT_WALKING -> Sport.WALKING

        else -> Sport.OTHER
    }

    private fun subSportName(value: Int?): String? = when (value) {
        SUB_SPORT_TREADMILL -> "treadmill"
        SUB_SPORT_STREET -> "street"
        SUB_SPORT_TRAIL -> "trail"
        SUB_SPORT_TRACK -> "track"
        SUB_SPORT_INDOOR_RUNNING -> "indoor_running"
        else -> null
    }

    private fun fileTypeName(value: Int): String = when (value) {
        4 -> "activity"
        else -> value.toString()
    }

    companion object {
        const val PARSER_VERSION = "1.0.0"

        private const val TIMESTAMP_FIELD = 253
        private const val MESSAGE_FILE_ID = 0
        private const val MESSAGE_SESSION = 18
        private const val MESSAGE_LAP = 19
        private const val MESSAGE_RECORD = 20
        private const val MESSAGE_EVENT = 21
        private const val MESSAGE_DEVICE_INFO = 23
        private const val MESSAGE_ACTIVITY = 34
        private const val EVENT_TIMER = 0
        private const val EVENT_START = 0
        private const val SPORT_RUNNING = 1
        private const val SPORT_WALKING = 11
        private const val SUB_SPORT_TREADMILL = 1
        private const val SUB_SPORT_STREET = 2
        private const val SUB_SPORT_TRAIL = 3
        private const val SUB_SPORT_TRACK = 4
        private const val SUB_SPORT_INDOOR_RUNNING = 45
    }
}
