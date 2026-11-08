package com.meucoach.app.ui.today

import com.meucoach.app.application.readinessResult
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class TodayViewModelTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `shows a pending check-in with low confidence`() {
        val state = todayState(null, today = today)

        assertThat(state.readinessScore).isEqualTo(50)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.AMBER)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("Pendente")
        assertThat(state.hasCheckedIn).isFalse()
    }

    @Test
    fun `uses the check-in score and confidence`() {
        val state = todayState(checkIn(), today = today)

        assertThat(state.readinessScore).isGreaterThan(70)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.GREEN)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("7/20")
        assertThat(state.hasCheckedIn).isTrue()
    }

    @Test
    fun `flags illness as a warning`() {
        val state = todayState(checkIn(illnessOrInjury = true), today = today)

        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.RED)
        assertThat(state.readinessSignals.single().tone).isEqualTo(SignalTone.WARNING)
        assertThat(state.readinessSignals.single().hint).isEqualTo("sinal de alerta")
    }

    @Test
    fun `shows objective signals and raises confidence`() {
        val metrics = (0..9).map { daysAgo ->
            DailyMetrics(
                date = today.minusDays(daysAgo.toLong()),
                sleepDurationMin = if (daysAgo == 0) 450 else null,
                restingHeartRate = 58.0,
                hrvRmssdMs = 60.0 + daysAgo,
                source = DailyMetricsSource.CSV,
            )
        }

        val state = todayState(checkIn(), metrics = metrics, today = today)

        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.HIGH)
        assertThat(state.readinessSignals.map { it.label })
            .contains("Sono", "Variabilidade", "FC de repouso")
        assertThat(state.readinessSignals.first { it.label == "Sono" }.value).isEqualTo("7h30")
        assertThat(state.readinessSignals.first { it.label == "Sono" }.hint).isNull()
    }

    @Test
    fun `ignores metrics from the future`() {
        val future = DailyMetrics(
            date = today.plusDays(1),
            sleepDurationMin = 450,
            source = DailyMetricsSource.CSV,
        )

        val state = todayState(checkIn(), metrics = listOf(future), today = today)

        assertThat(state.readinessSignals.map { it.label }).doesNotContain("Sono")
    }

    @Test
    fun `uses the recommendation for the workout card`() {
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            load = knownLoad(),
            today = today,
        )

        assertThat(state.workout.title).isEqualTo("Fácil e conversável")
        assertThat(state.workout.duration).isEqualTo("45")
        assertThat(state.workout.pace).isEqualTo("7:30–8:24")
        assertThat(state.workout.heartRate).isEqualTo("146–161")
    }

    @Test
    fun `illness turns the workout into rest`() {
        val state = todayState(
            checkIn = checkIn(illnessOrInjury = true),
            profile = profile(),
            today = today,
        )

        assertThat(state.workout.title).isEqualTo("Descanso")
        assertThat(state.workout.duration).isEqualTo("—")
    }

    @Test
    fun `builds an explanation with rules and data gaps`() {
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            today = today,
        )

        val explanation = state.explanation
        assertThat(explanation).isNotNull
        assertThat(explanation!!.rules.map { it.id }).contains("R-ZONE-01")
        assertThat(explanation.evidence.map { it.id })
            .contains("EV-SEILER-2010", "EV-DANIELS-2014")
        assertThat(explanation.usedData)
            .contains("Check-in de hoje", "Ritmo no limite calibrado", "FC no limite calibrada")
        assertThat(explanation.missingData).contains("Sono", "Variabilidade cardíaca", "FC de repouso")
    }

    @Test
    fun `builds a readiness explanation with drivers and evidence`() {
        val metrics = (0..9).map { daysAgo ->
            DailyMetrics(
                date = today.minusDays(daysAgo.toLong()),
                sleepDurationMin = if (daysAgo == 0) 450 else null,
                restingHeartRate = 58.0,
                hrvRmssdMs = 60.0 + daysAgo,
                source = DailyMetricsSource.CSV,
            )
        }
        val state = todayState(checkIn(), metrics = metrics, today = today)

        val explanation = state.readinessExplanation
        assertThat(explanation).isNotNull
        assertThat(explanation!!.rationale).contains("Sua prontidão está", "sono de 7h30")
        assertThat(explanation.usedData).contains(
            "Check-in de hoje",
            "Sono recente",
            "Variabilidade cardíaca",
            "FC de repouso",
        )
        assertThat(explanation.rules.map { it.id }).contains("R-READY-01")
        assertThat(explanation.evidence.map { it.id }).contains(
            "EV-SAW-2016",
            "EV-WALSH-2021",
            "EV-PLEWS-2013",
            "EV-BUCHHEIT-2014",
        )
    }

    @Test
    fun `shows a flat resting heart rate as stable`() {
        val metrics = (0..30).map { daysAgo ->
            DailyMetrics(
                date = today.minusDays(daysAgo.toLong()),
                restingHeartRate = 58.0,
                source = DailyMetricsSource.CSV,
            )
        }
        val state = todayState(checkIn = null, metrics = metrics, today = today)

        assertThat(state.readinessSignals.first { it.label == "FC de repouso" }.value)
            .isEqualTo("estável")
        assertThat(state.readinessSignals.first { it.label == "FC de repouso" }.hint).isNull()
    }

    @Test
    fun `does not invent load when there is no data`() {
        val state = todayState(checkIn(), profile = profile(), today = today)

        assertThat(state.ctl).isNull()
        assertThat(state.atl).isNull()
        assertThat(state.tsb).isNull()
        assertThat(state.weeklyLoadKm).isNull()
        assertThat(state.days).hasSize(7)
        assertThat(state.days).allSatisfy { assertThat(it.loadKm).isZero() }
    }

    @Test
    fun `does not invent a recent activity without runs`() {
        val state = todayState(checkIn(), profile = profile(), today = today)

        assertThat(state.recentActivity).isNull()
    }

    @Test
    fun `carries real activity stats into the state`() {
        val recent = RecentActivityUi(
            id = "activity-1",
            title = "Corrida",
            subtitle = "sexta, 30 out",
            distance = "5,00 km",
            duration = "30:00",
            pace = "6:00 /km",
            zone = "—",
        )

        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            today = today,
            kmByDay = mapOf(today to 5.0),
            weeklyLoadKm = 5.0,
            recentActivity = recent,
        )

        assertThat(state.weeklyLoadKm).isEqualTo(5.0)
        assertThat(state.days.first { it.isToday }.loadKm).isEqualTo(5.0)
        assertThat(state.recentActivity?.title).isEqualTo("Corrida")
        assertThat(state.athleteName).isEmpty()
    }

    @Test
    fun `carries the weekly plan into the state`() {
        val profile = profile()
        val readiness = readinessResult(checkIn(), emptyList(), today)
        val plan = com.meucoach.core.domain.prescription.WeeklyPlanGenerator().generate(
            com.meucoach.core.domain.prescription.WeeklyPlanInput(
                profile = profile,
                readiness = readiness,
                weekStart = today,
                today = today,
            ),
        )

        val state = todayState(
            checkIn = checkIn(),
            profile = profile,
            today = today,
            plan = plan,
        )

        assertThat(state.plan).isNotNull
        assertThat(state.plan?.sessions).isNotEmpty()
        assertThat(state.plan?.qualityCount).isZero()
    }

    private fun profile() = com.meucoach.core.model.AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = com.meucoach.core.model.Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = com.meucoach.core.model.Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
        weeklyAvailability = com.meucoach.core.model.WeeklyAvailability(
            mapOf(today.dayOfWeek to 45),
        ),
    )

    private fun knownLoad() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 10.0,
        atl = 12.0,
        tsb = -2.0,
        acuteAverage7d = 10.0,
        chronicAverage28d = 10.0,
        acwr = 1.0,
        monotony = null,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = null,
            sourceFresh = true,
        ),
    )

    private fun checkIn(illnessOrInjury: Boolean = false) = DailyCheckIn(
        date = today,
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illnessOrInjury,
    )
}
