package com.meucoach.app.ui.common

import com.meucoach.core.data.health.HealthConnectStatus
import java.time.LocalDate
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

fun healthConnectLastSyncLabel(lastSyncDate: LocalDate?, today: LocalDate = LocalDate.now()): String? =
    lastSyncDate?.let { date ->
        when (date) {
            today -> "Última leitura hoje"
            today.minusDays(1) -> "Última leitura ontem"
            else -> "Última leitura em ${date.format(SHORT_DATE)}"
        }
    }

fun isHealthConnectConnected(status: HealthConnectStatus, hasPermissions: Boolean): Boolean =
    status == HealthConnectStatus.AVAILABLE && hasPermissions
