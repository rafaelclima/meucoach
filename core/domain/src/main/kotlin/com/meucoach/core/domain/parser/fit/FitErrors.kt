package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.ImportedActivity
import java.time.Instant

enum class FitErrorCode {
    EMPTY_FILE,
    TOO_SHORT,
    INVALID_HEADER,
    INVALID_CRC,
    TRUNCATED,
    INVALID_DEFINITION,
    INVALID_DATA,
    UNKNOWN_LOCAL_MESSAGE,
    INVALID_TIMESTAMP,
    MISSING_ACTIVITY,
    INVALID_DURATION,
}

class FitParseException(val code: FitErrorCode, message: String, cause: Throwable? = null) :
    IllegalArgumentException("$code: $message", cause)

enum class FitWarningCode {
    MISSING_CRC,
    TRAILING_DATA,
    UNKNOWN_MESSAGE,
    UNKNOWN_DEVELOPER_FIELD,
    MISSING_FILE_ID,
    MULTIPLE_SESSIONS,
    MISSING_SESSION,
    UNKNOWN_SPORT,
    MISSING_TIMESTAMP,
    NEGATIVE_SAMPLE_OFFSET,
    COMPRESSED_WITHOUT_TIMESTAMP,
}

data class FitWarning(val code: FitWarningCode, val message: String)

data class FitFileId(
    val fileType: String? = null,
    val manufacturer: Int? = null,
    val product: Int? = null,
    val serialNumber: Long? = null,
    val timeCreated: Instant? = null,
    val productName: String? = null,
)

data class FitDeviceInfo(
    val productName: String? = null,
    val manufacturer: Int? = null,
    val serialNumber: Long? = null,
    val source: String? = null,
    val timestamp: Instant? = null,
)

data class FitParseResult(
    val activity: ImportedActivity,
    val fileId: FitFileId? = null,
    val device: FitDeviceInfo? = null,
    val warnings: List<FitWarning> = emptyList(),
)
