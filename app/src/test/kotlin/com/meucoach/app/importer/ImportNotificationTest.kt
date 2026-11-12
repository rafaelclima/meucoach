package com.meucoach.app.importer

import com.meucoach.core.data.importer.ImportErrorCode
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.model.ActivitySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ImportNotificationTest {
    @Test
    fun `failures without a job still explain themselves`() {
        val message = ImportNotification.failure(
            fileName = "corrida.gpx",
            message = ImportFailurePolicy.GIVE_UP_MESSAGE,
        )

        assertThat(message.title).isEqualTo("Não foi possível importar")
        assertThat(message.body)
            .isEqualTo("corrida.gpx: Não foi possível ler o arquivo. Importe o arquivo de novo.")
        assertThat(message.deepLink).isEqualTo("meucoach://activities")
    }

    @Test
    fun `opens the imported activity`() {
        val message = ImportNotification.message(
            ImportResult.Imported(
                jobId = "job-1",
                activityId = "activity-1",
                source = ActivitySource.FIT,
            ),
            fileName = "corrida.fit",
        )

        assertThat(message.title).isEqualTo("Treino importado")
        assertThat(message.body).isEqualTo("corrida.fit. Toque para abrir.")
        assertThat(message.deepLink).isEqualTo("meucoach://activity/activity-1")
    }

    @Test
    fun `mentions replacement and warnings`() {
        val message = ImportNotification.message(
            ImportResult.Imported(
                jobId = "job-1",
                activityId = "activity-1",
                source = ActivitySource.FIT,
                warnings = listOf("GPS: 2 pontos descartados"),
                replacedSource = ActivitySource.TCX,
            ),
            fileName = "corrida.fit",
        )

        assertThat(message.body)
            .isEqualTo("corrida.fit · substituiu o arquivo TCX · 1 aviso(s). Toque para abrir.")
    }

    @Test
    fun `restored activities say so`() {
        val message = ImportNotification.message(
            ImportResult.Imported(
                jobId = "job-1",
                activityId = "activity-1",
                source = ActivitySource.GPX,
                restored = true,
            ),
            fileName = "volta.gpx",
        )

        assertThat(message.title).isEqualTo("Treino restaurado")
    }

    @Test
    fun `duplicates still open the stored activity`() {
        val message = ImportNotification.message(
            ImportResult.Duplicate(jobId = "job-1", activityId = "activity-9"),
            fileName = "corrida.fit",
        )

        assertThat(message.title).isEqualTo("Treino já importado")
        assertThat(message.body).isEqualTo("corrida.fit já está no app. Toque para abrir.")
        assertThat(message.deepLink).isEqualTo("meucoach://activity/activity-9")
    }

    @Test
    fun `trash conflicts send the user to data`() {
        val message = ImportNotification.message(
            ImportResult.TrashConflict(jobId = "job-1", activityId = "activity-1"),
            fileName = "corrida.fit",
        )

        assertThat(message.title).isEqualTo("Atividade na lixeira")
        assertThat(message.body).contains("Restaure")
        assertThat(message.deepLink).isEqualTo("meucoach://data")
    }

    @Test
    fun `failures explain the reason and open the list`() {
        val message = ImportNotification.message(
            ImportResult.Failed(
                jobId = "job-1",
                code = ImportErrorCode.PARSE_ERROR,
                message = "Arquivo corrompido.",
            ),
            fileName = "quebrado.fit",
        )

        assertThat(message.title).isEqualTo("Não foi possível importar")
        assertThat(message.body).isEqualTo("quebrado.fit: Arquivo corrompido.")
        assertThat(message.deepLink).isEqualTo("meucoach://activities")
    }
}
