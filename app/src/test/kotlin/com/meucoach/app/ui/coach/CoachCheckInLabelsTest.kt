package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
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
class CoachCheckInLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `system turn marca o check-in feito`() {
        assertThat(CoachCheckInLabels.SYSTEM_LINE).contains("Check-in feito")
    }

    @Test
    fun `qualidade mantida segue como planejado`() {
        val ack = CoachCheckInLabels.ack(
            trainingContext(prescription = prescription(adjustment = PrescriptionAdjustment.KEEP)),
        )

        assertThat(ack).contains("como planejado")
    }

    @Test
    fun `rebaixado pelo sono explica o dia leve`() {
        val ack = CoachCheckInLabels.ack(
            trainingContext(
                prescription = prescription(
                    adjustment = PrescriptionAdjustment.DOWNGRADE,
                    reason = QualityBlockReason.SHORT_SLEEP,
                ),
            ),
        )

        assertThat(ack).contains("Sono curto")
    }

    @Test
    fun `sem prescricao a decisao sai no Hoje`() {
        val ack = CoachCheckInLabels.ack(trainingContext(prescription = null))

        assertThat(ack).contains("sai no Hoje")
    }

    @Test
    fun `sem check-in nao ha resposta`() {
        assertThat(CoachCheckInLabels.ack(trainingContext(prescription = null, hasCheckIn = false))).isNull()
    }

    @Test
    fun `vespera nao responde ao check-in de hoje`() {
        assertThat(
            CoachCheckInLabels.ack(
                trainingContext(
                    date = monday.plusDays(1),
                    prescription = null,
                ),
            ),
        ).isNull()
    }

    @Test
    fun `dia de prova nao responde ao check-in`() {
        assertThat(CoachCheckInLabels.ack(raceContext())).isNull()
    }

    private fun trainingContext(
        date: LocalDate = monday,
        prescription: DailyPrescription? = null,
        hasCheckIn: Boolean = true,
    ) = CoachNextContext(
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
        hasCheckIn = hasCheckIn,
    )

    private fun raceContext() = CoachNextContext(
        subject = CoachNextSubject.Race(
            RaceEvent(
                id = "race-1",
                distance = RaceDistance.FIVE_K,
                targetDate = monday,
                createdAt = Instant.parse("2026-11-16T12:00:00Z"),
                updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
            ),
        ),
        today = monday,
        hasPlan = true,
        hasProfile = true,
        hasCheckIn = true,
    )

    private fun prescription(adjustment: PrescriptionAdjustment, reason: QualityBlockReason? = null) =
        DailyPrescription(
            date = monday,
            plannedSessionId = null,
            plannedType = WorkoutType.QUALITY,
            plannedTitle = "Ritmo forte",
            plannedDurationMin = 35,
            prescribedType = WorkoutType.EASY,
            title = "Fácil",
            prescribedDurationMin = 25,
            targets = PrescriptionTargets(primary = TargetKind.RPE),
            adjustment = adjustment,
            rationale = "Dia leve.",
            ruleIds = listOf("R-PRESC-02"),
            evidenceIds = listOf("EV-SAW-2016"),
            confidence = DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v6",
            inputsHash = "hash",
            qualityBlockReason = reason,
            decisionDetail = if (reason == null) null else "sono curto",
            unlockHint = if (reason == null) null else "Faça o check-in de hoje para liberar o treino forte.",
        )
}
