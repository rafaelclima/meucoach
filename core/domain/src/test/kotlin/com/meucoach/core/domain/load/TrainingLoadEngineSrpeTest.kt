package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Fatia sRPE (ADR 0039, FR-013): o esforço percebido é o terceiro nível de carga
 * — só preenche o dia quando nem TRIMP nem rTSS estão disponíveis, nunca soma
 * com eles, e é declarado como contribuição subjetiva na série.
 */
class TrainingLoadEngineSrpeTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val engine = TrainingLoadEngine()

    @Test
    fun `srpe fills the day only when neither trimp nor rtss applies`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today,
                    durationSec = 3_600,
                    averageHeartRate = 150,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
                LoadActivity(
                    date = today.minusDays(1),
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            ),
            executions = listOf(LoadExecution(date = today.minusDays(2), durationMin = 40, rpe = 6)),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.daily.last().metric).isEqualTo(LoadMetric.TRIMP)
        assertThat(snapshot.daily[26].metric).isEqualTo(LoadMetric.RTSS)
        val subjective = snapshot.daily.first { it.date == today.minusDays(2) }
        assertThat(subjective.metric).isEqualTo(LoadMetric.SRPE)
        assertThat(subjective.load).isCloseTo(240.0, within(1e-6))
    }

    @Test
    fun `multiple subjective executions in the same day sum`() {
        val snapshot = engine.calculate(
            activities = emptyList(),
            executions = listOf(
                LoadExecution(date = today, durationMin = 30, rpe = 6),
                LoadExecution(date = today, durationMin = 20, rpe = 4),
            ),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        val day = snapshot.daily.last()
        assertThat(day.metric).isEqualTo(LoadMetric.SRPE)
        assertThat(day.load).isCloseTo(260.0, within(1e-6))
    }

    @Test
    fun `an activity without usable objective metric does not block the srpe day`() {
        // Corrida vinculada sem FC e sem pace útil (sem pace de limiar) no mesmo dia
        // de um RPE registrado: o dia continua subjetivo, não vira dado ausente.
        val snapshot = engine.calculate(
            activities = listOf(LoadActivity(date = today, durationSec = 3_600)),
            executions = listOf(LoadExecution(date = today, durationMin = 40, rpe = 5)),
            profile = profile(thresholdPace = null),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        val day = snapshot.daily.last()
        assertThat(day.metric).isEqualTo(LoadMetric.SRPE)
        assertThat(day.load).isCloseTo(200.0, within(1e-6))
    }

    @Test
    fun `subjective days are declared in the window`() {
        val snapshot = engine.calculate(
            activities = emptyList(),
            executions = (0 until 10).map { day ->
                LoadExecution(date = today.minusDays(day.toLong()), durationMin = 40, rpe = 5)
            },
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 42,
        )

        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(10)
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(10)
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        assertThat(snapshot.objectiveDataState).isEqualTo(LoadDataState.INSUFFICIENT)
    }

    @Test
    fun `objective data state mirrors data state without subjective days`() {
        val snapshot = engine.calculate(
            activities = (0 until 10).map { day ->
                LoadActivity(
                    date = today.minusDays(day.toLong()),
                    durationSec = 3_600,
                    averageHeartRate = 150,
                )
            },
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 42,
        )

        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(0)
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        assertThat(snapshot.objectiveDataState).isEqualTo(LoadDataState.KNOWN)
    }

    @Test
    fun `a recent rpe log keeps the source fresh without an import`() {
        val status = ActivitySourceStatus(
            lastImportedAt = null,
            lastSubjectiveLogAt = Instant.parse("2026-10-28T10:00:00Z"),
            checkedAt = Instant.parse("2026-10-30T10:00:00Z"),
        )
        assertThat(status.isFresh).isTrue()

        val stale = status.copy(lastSubjectiveLogAt = Instant.parse("2026-09-01T10:00:00Z"))
        assertThat(stale.isFresh).isFalse()
    }

    @Test
    fun `an old import is not rescued by an even older subjective log`() {
        val status = ActivitySourceStatus(
            lastImportedAt = Instant.parse("2026-09-01T10:00:00Z"),
            lastSubjectiveLogAt = Instant.parse("2026-09-02T10:00:00Z"),
            checkedAt = Instant.parse("2026-10-30T10:00:00Z"),
        )
        assertThat(status.isFresh).isFalse()
    }

    private fun profile(thresholdPace: Pace? = Pace.fromSecondsPerKm(360)) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = thresholdPace,
        timezone = "America/Sao_Paulo",
    )

    private fun source() = ActivitySourceStatus(
        lastImportedAt = Instant.parse("2026-10-29T10:00:00Z"),
        checkedAt = Instant.parse("2026-10-30T10:00:00Z"),
    )
}
