package com.meucoach.app.ui.activities

import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.repository.ImportJobSummary
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActivityFormattersTest {
    @Test
    fun `formats distance with brazilian decimal separator`() {
        assertThat(formatDistance(Distance(5_106.0))).isEqualTo("5,11")
        assertThat(formatDistance(Distance(3_030.0))).isEqualTo("3,03")
    }

    @Test
    fun `formats duration with and without hours`() {
        assertThat(formatDuration(1_859)).isEqualTo("30:59")
        assertThat(formatDuration(3_723)).isEqualTo("1:02:03")
        assertThat(formatDuration(5)).isEqualTo("0:05")
    }

    @Test
    fun `formats date and time in the given zone`() {
        assertThat(
            formatDateTime(
                instant = Instant.parse("2026-10-24T08:17:10Z"),
                zoneId = ZoneId.of("America/Sao_Paulo"),
            ),
        ).isEqualTo("sáb, 24 out · 05:17")
    }

    @Test
    fun `formats pace`() {
        assertThat(paceLabel(Pace.fromSecondsPerKm(364))).isEqualTo("6:04")
        assertThat(paceLabel(null)).isEqualTo("—")
    }

    @Test
    fun `maps sport and source labels`() {
        assertThat(sportLabel(Sport.RUNNING)).isEqualTo("Corrida")
        assertThat(sportLabel(Sport.TREADMILL)).isEqualTo("Esteira")
        assertThat(sportLabel(Sport.WALKING)).isEqualTo("Caminhada")
        assertThat(sportLabel(Sport.OTHER)).isEqualTo("Atividade")
        assertThat(sourceLabel(ActivitySource.FIT)).isEqualTo("FIT")
        assertThat(sourceLabel(ActivitySource.HEALTH_CONNECT)).isEqualTo("Health Connect")
    }

    @Test
    fun `formats heart rate variants`() {
        assertThat(heartRateLabel(150, 170)).isEqualTo("150/170")
        assertThat(heartRateLabel(150, null)).isEqualTo("150")
        assertThat(heartRateLabel(null, 170)).isEqualTo("máx 170")
        assertThat(heartRateLabel(null, null)).isNull()
    }

    @Test
    fun `describes import jobs`() {
        assertThat(importStatusLabel(ImportJobStatus.DONE)).isEqualTo("Importado")
        assertThat(importStatusLabel(ImportJobStatus.DUPLICATE)).isEqualTo("Duplicado")
        assertThat(importStatusLabel(ImportJobStatus.FAILED)).isEqualTo("Falhou")
        assertThat(importJobDetail(job(ImportJobStatus.DONE, errorMessage = null)))
            .isEqualTo("Adicionado à biblioteca")
        assertThat(importJobDetail(job(ImportJobStatus.DUPLICATE, errorMessage = null)))
            .isEqualTo("Já fazia parte da biblioteca")
        assertThat(importJobDetail(job(ImportJobStatus.FAILED, errorMessage = "Arquivo inválido")))
            .isEqualTo("Arquivo inválido")
        assertThat(importJobDetail(job(ImportJobStatus.FAILED, errorMessage = null)))
            .isEqualTo("Não foi possível importar")
    }

    private fun job(status: ImportJobStatus, errorMessage: String?) = ImportJobSummary(
        id = "job-1",
        fileName = "corrida.fit",
        status = status,
        activityId = null,
        errorCode = null,
        errorMessage = errorMessage,
        createdAt = Instant.parse("2026-10-30T12:00:00Z"),
        finishedAt = null,
    )
}
