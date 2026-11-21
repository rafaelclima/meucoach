package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachOpeningLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `saudacao varia com a hora`() {
        assertThat(CoachOpeningLabels.greeting(8)).isEqualTo("Bom dia!")
        assertThat(CoachOpeningLabels.greeting(15)).isEqualTo("Boa tarde!")
        assertThat(CoachOpeningLabels.greeting(22)).isEqualTo("Boa noite!")
        assertThat(CoachOpeningLabels.greeting(4)).isEqualTo("Boa noite!")
    }

    @Test
    fun `prova hoje vira boa prova`() {
        val text = CoachOpeningLabels.opening(raceContext(monday), 8)

        assertThat(text).contains("dia de prova")
        assertThat(text).contains("boa prova")
    }

    @Test
    fun `prova amanha pede boa noite de sono`() {
        val text = CoachOpeningLabels.opening(raceContext(monday.plusDays(1)), 20)

        assertThat(text).contains("Amanhã é dia de prova")
    }

    @Test
    fun `vespera de treino mostra o planejado`() {
        val text = CoachOpeningLabels.opening(trainingContext(date = monday.plusDays(1)), 20)

        assertThat(text).contains("Amanhã tem Fácil · 30 min")
    }

    @Test
    fun `hoje sem check-in e sem prescricao pede check-in`() {
        val text = CoachOpeningLabels.opening(trainingContext(date = monday), 8)

        assertThat(text).contains("check-in")
        assertThat(text).doesNotContain("Planejado:")
    }

    @Test
    fun `hoje com prescricao mantem abertura curta`() {
        val text = CoachOpeningLabels.opening(
            trainingContext(date = monday, prescription = prescription()),
            8,
        )

        assertThat(text).contains("Bora ver o que importa?")
    }

    @Test
    fun `abertura cabe em uma bolha`() {
        val contexts = listOf(
            raceContext(monday),
            raceContext(monday.plusDays(1)),
            trainingContext(date = monday),
            trainingContext(date = monday.plusDays(1)),
            trainingContext(date = monday, prescription = prescription()),
        )

        contexts.forEach { context ->
            listOf(8, 15, 22).forEach { hour ->
                assertThat(CoachOpeningLabels.opening(context, hour).length)
                    .isLessThanOrEqualTo(140)
            }
        }
    }

    private fun trainingContext(date: LocalDate, prescription: DailyPrescription? = null) = CoachNextContext(
        subject = CoachNextSubject.Training(
            PlannedSession(
                date = date,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        today = monday,
        hasPlan = true,
        hasProfile = true,
        prescription = prescription,
    )

    private fun raceContext(date: LocalDate) = CoachNextContext(
        subject = CoachNextSubject.Race(
            RaceEvent(
                id = "race-1",
                distance = RaceDistance.FIVE_K,
                targetDate = date,
                createdAt = Instant.parse("2026-11-16T12:00:00Z"),
                updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
            ),
        ),
        today = monday,
        hasPlan = true,
        hasProfile = true,
    )

    private fun prescription() = DailyPrescription(
        date = monday,
        plannedSessionId = null,
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Ritmo forte",
        plannedDurationMin = 35,
        prescribedType = WorkoutType.EASY,
        title = "Fácil",
        prescribedDurationMin = 25,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = PrescriptionAdjustment.DOWNGRADE,
        rationale = "Dia leve.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
        qualityBlockReason = null,
        decisionDetail = null,
        unlockHint = null,
    )
}
