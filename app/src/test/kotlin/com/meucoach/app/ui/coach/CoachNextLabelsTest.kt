package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachNextLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `rotulo do dia usa hoje, amanha ou dia da semana`() {
        assertThat(CoachNextLabels.dayLabel(monday, monday, true)).isEqualTo("Hoje")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(1), false)).isEqualTo("Amanhã")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(2), false)).isEqualTo("QUA")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(5), false)).isEqualTo("SÁB")
    }

    @Test
    fun `futuro sem prescricao mostra o planejado sem decidir`() {
        val context = context(prescription = null)

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Amanhã · Ritmo forte · 35 min")
        assertThat(CoachNextLabels.statusLine(context)).contains("A decisão do dia sai no Hoje")
    }

    @Test
    fun `dia de prova nao mostra treino`() {
        val context = context(prescription = null, race = true)

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Dia de prova")
        assertThat(CoachNextLabels.statusLine(context)).contains("Dia de prova")
    }

    @Test
    fun `mantido, reduzido e descanso tem linha propria`() {
        assertThat(CoachNextLabels.statusLine(context(adjustment = PrescriptionAdjustment.KEEP)))
            .isEqualTo("Mantido como planejado.")
        assertThat(CoachNextLabels.statusLine(context(adjustment = PrescriptionAdjustment.REDUCE)))
            .isEqualTo("Reduzido para o dia.")
        assertThat(
            CoachNextLabels.statusLine(
                context(
                    adjustment = PrescriptionAdjustment.REST,
                    reason = QualityBlockReason.ILLNESS_OR_RED_READINESS,
                ),
            ),
        ).contains("Descanso")
    }

    @Test
    fun `bloqueio vira frase amigavel com o desbloqueio do motor`() {
        val line = CoachNextLabels.statusLine(
            context(
                adjustment = PrescriptionAdjustment.DOWNGRADE,
                reason = QualityBlockReason.SUBJECTIVE_MISSING,
            ),
        )

        assertThat(line).contains("Ajustado para fácil")
        assertThat(line).contains("check-in")
    }

    @Test
    fun `todo motivo de bloqueio tem copy sem sigla tecnica`() {
        QualityBlockReason.entries.forEach { reason ->
            val copy = CoachNextLabels.blockCopy(reason)

            assertThat(copy).isNotBlank()
            assertThat(copy).doesNotContain("ACWR")
            assertThat(copy).doesNotContain("R-")
        }
    }

    private fun context(
        adjustment: PrescriptionAdjustment = PrescriptionAdjustment.KEEP,
        reason: QualityBlockReason? = null,
        prescription: DailyPrescription? = prescription(adjustment, reason),
        race: Boolean = false,
    ) = CoachNextContext(
        session = PlannedSession(
            date = monday.plusDays(1),
            type = WorkoutType.QUALITY,
            title = "Ritmo forte",
            durationMin = 35,
            rationale = "Estímulo.",
            ruleIds = listOf("R-PER-03"),
            evidenceIds = listOf("EV-SEILER-2010"),
            status = SessionStatus.PLANNED,
        ),
        isToday = false,
        today = monday,
        prescription = prescription,
        raceEvent = if (race) {
            com.meucoach.core.model.RaceEvent(
                id = "race-1",
                distance = com.meucoach.core.model.RaceDistance.FIVE_K,
                targetDate = monday.plusDays(1),
                targetTimeSec = null,
                status = com.meucoach.core.model.RaceStatus.PLANNED,
                activityId = null,
                completionSource = null,
                createdAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
                updatedAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
            )
        } else {
            null
        },
    )

    private fun prescription(adjustment: PrescriptionAdjustment, reason: QualityBlockReason?) = DailyPrescription(
        date = monday.plusDays(1),
        plannedSessionId = null,
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Ritmo forte",
        plannedDurationMin = 35,
        prescribedType = if (adjustment == PrescriptionAdjustment.REST) {
            WorkoutType.REST
        } else {
            WorkoutType.EASY
        },
        title = "Fácil",
        prescribedDurationMin = if (adjustment == PrescriptionAdjustment.REST) null else 25,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = adjustment,
        rationale = "Dia leve.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
        qualityBlockReason = reason,
        decisionDetail = if (reason == null) null else "check-in de hoje ausente",
        unlockHint = if (reason == null) {
            null
        } else {
            "Faça o check-in de hoje para liberar o treino forte."
        },
    )
}
