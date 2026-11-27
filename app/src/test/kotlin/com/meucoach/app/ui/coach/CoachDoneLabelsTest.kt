package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
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
class CoachDoneLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `system turn marca o treino feito`() {
        assertThat(CoachDoneLabels.SYSTEM_LINE).contains("Treino feito")
    }

    @Test
    fun `treino de hoje feito comemora sem citar o titulo`() {
        val cheer = CoachDoneLabels.cheer(trainingContext(doneSession()))

        assertThat(cheer).isEqualTo("Boa! Treino concluído.")
    }

    @Test
    fun `treino pendente nao comemora`() {
        assertThat(CoachDoneLabels.cheer(trainingContext(plannedSession()))).isNull()
    }

    @Test
    fun `sem sessao hoje nao comemora`() {
        assertThat(CoachDoneLabels.cheer(trainingContext(todaySession = null))).isNull()
    }

    @Test
    fun `dia de prova nao comemora treino`() {
        assertThat(CoachDoneLabels.cheer(raceContext())).isNull()
    }

    @Test
    fun `comemoracao cabe em uma bolha`() {
        assertThat(CoachDoneLabels.cheer(trainingContext(doneSession()))!!.length)
            .isLessThanOrEqualTo(140)
    }

    private fun trainingContext(todaySession: PlannedSession?) = CoachNextContext(
        subject = todaySession?.let {
            CoachNextSubject.Training(
                plannedSession(),
            )
        },
        today = monday,
        hasPlan = true,
        hasProfile = true,
        todaySession = todaySession,
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
        todaySession = doneSession(),
    )

    private fun plannedSession() = PlannedSession(
        date = monday.plusDays(2),
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = 35,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.PLANNED,
    )

    private fun doneSession() = PlannedSession(
        date = monday,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = com.meucoach.core.model.CompletionEvidence.MANUAL,
    )
}
