package com.meucoach.app.ui.common

import com.meucoach.core.data.health.HealthConnectStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

/**
 * One status vocabulary for the profile summary and the data screen. The user
 * sees the same words wherever Health Connect is mentioned, and the label
 * always says what to do next instead of only exposing the platform state.
 */
fun healthConnectStatusLabel(status: HealthConnectStatus, hasPermissions: Boolean, storedDays: Int = 0): String =
    when (status) {
        HealthConnectStatus.UNAVAILABLE -> "Indisponível neste aparelho"

        HealthConnectStatus.UPDATE_REQUIRED -> "Atualize o Health Connect"

        HealthConnectStatus.AVAILABLE -> when {
            !hasPermissions -> "Ainda não conectado"
            storedDays > 0 -> "Conectado · $storedDays dias"
            else -> "Conectado · sem dados ainda"
        }
    }

fun healthConnectLastSyncLabel(
    lastSyncedAt: Instant?,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? = lastSyncedAt?.let { instant ->
    val elapsed = Duration.between(instant, now)
    when {
        elapsed.isNegative || elapsed < Duration.ofMinutes(1) -> "Atualizado agora"
        elapsed < Duration.ofHours(1) -> "Atualizado há ${elapsed.toMinutes()} min"
        elapsed < Duration.ofHours(24) -> "Atualizado há ${elapsed.toHours()} h"
        elapsed < Duration.ofHours(48) -> "Atualizado ontem"
        else -> "Atualizado em ${instant.atZone(zone).format(SHORT_DATE)}"
    }
}

fun isHealthConnectConnected(status: HealthConnectStatus, hasPermissions: Boolean): Boolean =
    status == HealthConnectStatus.AVAILABLE && hasPermissions
