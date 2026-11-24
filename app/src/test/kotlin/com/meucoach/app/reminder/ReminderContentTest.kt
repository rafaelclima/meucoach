package com.meucoach.app.reminder

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderContentTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `reminds when today has no check-in`() {
        val message = ReminderContent.checkIn(today, null)!!

        assertThat(message.kind).isEqualTo(ReminderKind.CHECK_IN)
        assertThat(message.deepLink).isEqualTo(DEEP_LINK_CHECK_IN)
        assertThat(message.body).contains("15 s")
    }

    @Test
    fun `skips the reminder when today is already checked in`() {
        val checkIn = DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2)

        assertThat(ReminderContent.checkIn(today, checkIn)).isNull()
        assertThat(
            ReminderContent.checkIn(today, checkIn.copy(date = today.minusDays(1))),
        ).isNotNull
    }

    @Test
    fun `reminds about a planned workout today`() {
        val plan = plan(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base aeróbica.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        )

        val message = ReminderContent.workout(today, plan)!!

        assertThat(message.title).isEqualTo("Treino de hoje: Fácil e conversável")
        assertThat(message.body).contains("40 min", "Base aeróbica")
        assertThat(message.deepLink).isEqualTo(DEEP_LINK_TODAY)
    }

    @Test
    fun `uses the adjusted prescription instead of the weekly plan`() {
        val plan = plan(
            PlannedSession(
                date = today,
                type = WorkoutType.QUALITY,
                title = "Intervalado",
                durationMin = 40,
                rationale = "Plano: forte.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        )
        val prescription = prescription(
            plannedType = WorkoutType.QUALITY,
            plannedDurationMin = 40,
            prescribedType = WorkoutType.EASY,
            title = "Fácil — prontidão moderada",
            prescribedDurationMin = 24,
            adjustment = PrescriptionAdjustment.DOWNGRADE,
            rationale = "Planejado: Intervalado (40 min). Hoje: fácil, 24 min.",
        )

        val message = ReminderContent.workout(today, plan, prescription)!!

        assertThat(message.title).isEqualTo("Treino de hoje: Fácil — prontidão moderada")
        assertThat(message.body).contains("Ajustado para hoje", "24 min", "Hoje: fácil")
        assertThat(message.body).doesNotContain("Intervalado · 40 min")
    }

    @Test
    fun `prescription rest cancels the workout reminder`() {
        val plan = plan(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        )
        val rest = prescription(
            plannedType = WorkoutType.EASY,
            plannedDurationMin = 30,
            prescribedType = WorkoutType.REST,
            title = "Descanso",
            prescribedDurationMin = null,
            adjustment = PrescriptionAdjustment.REST,
            rationale = "Prontidão baixa.",
        )

        assertThat(ReminderContent.workout(today, plan, rest)).isNull()
        assertThat(ReminderContent.workoutForPrescription(rest)).isNull()
    }

    @Test
    fun `prescription copy can be rebuilt without the plan for an active notification`() {
        val prescription = prescription(
            plannedType = WorkoutType.QUALITY,
            plannedDurationMin = 40,
            prescribedType = WorkoutType.EASY,
            title = "Fácil",
            prescribedDurationMin = 24,
            adjustment = PrescriptionAdjustment.DOWNGRADE,
            rationale = "Prontidão moderada.",
        )

        val message = ReminderContent.workoutForPrescription(prescription)!!

        assertThat(message.id).isEqualTo(ReminderContent.WORKOUT_ID)
        assertThat(message.title).contains("Fácil")
        assertThat(message.body).contains("Ajustado para hoje")
    }

    @Test
    fun `skips completed, skipped, rest or absent workouts`() {
        val session = PlannedSession(
            date = today,
            type = WorkoutType.EASY,
            title = "Fácil",
            durationMin = 30,
            rationale = "Base.",
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

        assertThat(
            ReminderContent.workout(
                today,
                plan(
                    session.copy(
                        status = SessionStatus.DONE,
                        completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                        activityId = "activity-1",
                    ),
                ),
            ),
        ).isNull()
        assertThat(
            ReminderContent.workout(
                today,
                plan(
                    session.copy(
                        status = SessionStatus.CLOSED,
                        closureReason =
                        com.meucoach.core.domain.prescription.SessionClosureReason.USER_SKIPPED,
                        closedAt = java.time.Instant.EPOCH,
                    ),
                ),
            ),
        ).isNull()
        assertThat(
            ReminderContent.workout(today, plan(session.copy(type = WorkoutType.REST))),
        ).isNull()
        assertThat(ReminderContent.workout(today, plan(session.copy(date = today.plusDays(1))))).isNull()
        assertThat(ReminderContent.workout(today, null)).isNull()
    }

    @Test
    fun `race day copy opens today and never repeats the workout reminder`() {
        val event = RaceEvent(
            id = "race-1",
            distance = RaceDistance.TEN_K,
            targetDate = today,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val message = ReminderContent.race(event)

        assertThat(message.kind).isEqualTo(ReminderKind.RACE)
        assertThat(message.id).isEqualTo(ReminderContent.RACE_ID)
        assertThat(message.title).isEqualTo("Prova hoje: 10 km")
        assertThat(message.body).contains("dia de prova")
        assertThat(message.body).doesNotContain("Treino de hoje")
        assertThat(message.deepLink).isEqualTo(DEEP_LINK_TODAY)

        val cancelled = ReminderContent.race(
            event.copy(
                status = com.meucoach.core.model.RaceStatus.CANCELLED,
                resolvedAt = Instant.EPOCH,
            ),
        )
        assertThat(cancelled.title).contains("cancelada")
        assertThat(cancelled.body).contains("não libera treino")
        assertThat(cancelled.deepLink).isEqualTo(DEEP_LINK_TODAY)
    }

    @Test
    fun `computes the next reminder delay`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val morning = today.atTime(7, 0).atZone(zone)
        val evening = today.atTime(19, 0).atZone(zone)

        assertThat(ReminderContent.nextDelay(morning, hour = 18)).isEqualTo(Duration.ofHours(11))
        assertThat(ReminderContent.nextDelay(evening, hour = 18)).isEqualTo(Duration.ofHours(23))
    }

    @Test
    fun `rejects invalid reminder times`() {
        val now = today.atTime(7, 0).atZone(ZoneId.of("UTC"))
        assertThatThrownBy { ReminderContent.nextDelay(now, hour = 24) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ReminderContent.nextDelay(now, hour = 8, minute = 60) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun prescription(
        plannedType: WorkoutType,
        plannedDurationMin: Int?,
        prescribedType: WorkoutType,
        title: String,
        prescribedDurationMin: Int?,
        adjustment: PrescriptionAdjustment,
        rationale: String,
    ) = DailyPrescription(
        date = today,
        plannedSessionId = "session-1",
        plannedType = plannedType,
        plannedTitle = "Planejado",
        plannedDurationMin = plannedDurationMin,
        prescribedType = prescribedType,
        title = title,
        prescribedDurationMin = prescribedDurationMin,
        targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
        adjustment = adjustment,
        rationale = rationale,
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
    )

    private fun plan(session: PlannedSession) = WeeklyPlan(
        weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
        sessions = listOf(session),
        weeklyMinutes = session.durationMin ?: 0,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Plano.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
