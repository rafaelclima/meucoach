package com.meucoach.app.importer

import com.meucoach.core.data.importer.ImportResult

/** Deep links consumed by [com.meucoach.app.reminder.NotificationNavigation]. */
const val DEEP_LINK_ACTIVITY = "meucoach://activity"
const val DEEP_LINK_ACTIVITIES = "meucoach://activities"
const val DEEP_LINK_DATA = "meucoach://data"

data class ImportNotificationMessage(val title: String, val body: String, val deepLink: String)

/**
 * Copy for the "import finished" notification. Pure so the wording is tested
 * without Android. One notification id is reused: a batch import keeps the
 * shade clean and the last result wins.
 */
object ImportNotification {
    const val ID = 2_001

    fun message(result: ImportResult, fileName: String): ImportNotificationMessage = when (result) {
        is ImportResult.Imported -> ImportNotificationMessage(
            title = if (result.restored) "Treino restaurado" else "Treino importado",
            body = buildString {
                append(fileName)
                result.replacedSource?.let { append(" · substituiu o arquivo ${it.name}") }
                if (result.warnings.isNotEmpty()) {
                    append(" · ${result.warnings.size} aviso(s)")
                }
                append(". Toque para abrir.")
            },
            deepLink = "$DEEP_LINK_ACTIVITY/${result.activityId}",
        )

        is ImportResult.Duplicate -> ImportNotificationMessage(
            title = "Treino já importado",
            body = "$fileName já está no app. Toque para abrir.",
            deepLink = "$DEEP_LINK_ACTIVITY/${result.activityId}",
        )

        is ImportResult.Failed -> failure(fileName, result.message)

        is ImportResult.TrashConflict -> ImportNotificationMessage(
            title = "Atividade na lixeira",
            body = "$fileName já existe na lixeira. Restaure-a em Dados antes de importar de novo.",
            deepLink = DEEP_LINK_DATA,
        )

        ImportResult.Skipped -> ImportNotificationMessage(
            title = "Importação cancelada",
            body = "O arquivo não foi importado porque os dados foram apagados ou não há perfil.",
            deepLink = DEEP_LINK_ACTIVITIES,
        )
    }

    /** Same copy for a failure that never reached a job (staged file lost, timeout). */
    fun failure(fileName: String, message: String): ImportNotificationMessage = ImportNotificationMessage(
        title = "Não foi possível importar",
        body = "$fileName: $message",
        deepLink = DEEP_LINK_ACTIVITIES,
    )
}
