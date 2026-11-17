package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.load.calibrationNote
import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityGate
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.VolumeGates
import com.meucoach.core.domain.prescription.VolumeProgression
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * G-LOAD-03 (ADR 0046 §6, FR-405): cold start with 8 objective load days and
 * 13 pre-history days. The unstable ratio must not veto quality or grow
 * volume; the UI shows "em calibração" with the baseline progress.
 *
 * (Numbered G-LOAD-03 because G-LOAD-02 already names the full-coverage
 * 28-day engine fixture, which predates the PRD; the renumbering is recorded
 * in C4-26 and goes to the ADR/PRD in C4-27.)
 */
class LoadCalibrationGoldenTest {
    @Test
    fun `cold start keeps quality eligible and volume maintained`() {
        val input = readGolden("/golden/load-calibration/input.json")
        val expected = readGolden("/golden/load-calibration/expected.json")
        val snapshot = engineSnapshot(input)

        assertThat(snapshot.coverage.daysBeforeHistory).isEqualTo(expected.getInt("daysBeforeHistory"))
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(expected.getInt("daysWithLoad"))
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(expected.getInt("daysWithoutActivity"))
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(expected.getInt("daysWithoutData"))
        assertThat(snapshot.historyDays).isEqualTo(expected.getInt("historyDays"))
        assertThat(snapshot.baselineDays).isEqualTo(expected.getInt("baselineDays"))
        assertThat(snapshot.missingDays).isEqualTo(expected.getInt("missingDays"))
        assertThat(snapshot.acwrReliable).isEqualTo(expected.getBoolean("acwrReliable"))
        assertThat(snapshot.strainReliable).isEqualTo(expected.getBoolean("strainReliable"))
        assertThat(snapshot.acwrHigh).isEqualTo(expected.getBoolean("acwrHigh"))
        assertThat(snapshot.permitsQuality).isEqualTo(expected.getBoolean("permitsQuality"))
        assertThat(snapshot.permitsProgression).isEqualTo(expected.getBoolean("permitsProgression"))
        assertThat(snapshot.dataState.name).isEqualTo(expected.getString("dataState"))
        assertThat(snapshot.objectiveDataState.name).isEqualTo(expected.getString("objectiveDataState"))
        assertThat(snapshot.calibrationNote()).isEqualTo(expected.getString("calibrationNote"))
        assertThat(snapshot.ctl).isCloseTo(expected.getDouble("ctl"), within(1e-6))
        assertThat(snapshot.atl).isCloseTo(expected.getDouble("atl"), within(1e-6))
        assertThat(snapshot.tsb).isCloseTo(expected.getDouble("tsb"), within(1e-6))
        assertThat(snapshot.acuteAverage7d).isCloseTo(expected.getDouble("acuteAverage7d"), within(1e-6))
        assertThat(snapshot.chronicAverage28d).isCloseTo(expected.getDouble("chronicAverage28d"), within(1e-6))
        assertThat(checkNotNull(snapshot.acwr)).isCloseTo(expected.getDouble("acwr"), within(1e-6))
        assertThat(checkNotNull(snapshot.monotony)).isCloseTo(expected.getDouble("monotony"), within(1e-6))
        assertThat(checkNotNull(snapshot.strain)).isCloseTo(expected.getDouble("strain"), within(1e-6))

        val days = expected.getJSONObject("days")
        days.keys().forEach { date ->
            val dayExpected = days.getJSONObject(date)
            val day = snapshot.daily.first { it.date.toString() == date }
            assertThat(day.load).isCloseTo(dayExpected.getDouble("load"), within(1e-6))
            assertThat(day.metric).isEqualTo(LoadMetric.valueOf(dayExpected.getString("metric")))
        }

        val gate = QualityGate.evaluate(
            plannedSession = qualitySession(input),
            readiness = greenReadiness(),
            load = snapshot,
            recovery = null,
            profile = profile(),
            date = LocalDate.parse(input.getString("today")),
            phase = null,
            raceWeek = false,
        )
        assertThat(gate.eligible).isEqualTo(expected.getJSONObject("gate").getBoolean("eligible"))
        assertThat(gate.ruleId).isEqualTo(expected.getJSONObject("gate").getString("ruleId"))

        val volume = decideVolume(input, snapshot)
        val volumeExpected = expected.getJSONObject("volume")
        assertThat(volume.block.name).isEqualTo(volumeExpected.getString("block"))
        assertThat(volume.growthPercent).isEqualTo(volumeExpected.getInt("growthPercent"))
        assertThat(volume.minutes).isEqualTo(volumeExpected.getInt("maintainedMinutes"))
    }

    private fun engineSnapshot(input: JSONObject): LoadSnapshot {
        val today = LocalDate.parse(input.getString("today"))
        val profileJson = input.getJSONObject("profile")
        val profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.parse(profileJson.getString("dateOfBirth")),
            sex = Sex.valueOf(profileJson.getString("sex")),
            restingHeartRate = profileJson.getInt("restingHeartRate"),
            maxHeartRateObserved = profileJson.getInt("maxHeartRateObserved"),
            thresholdPace = Pace.fromSecondsPerKm(profileJson.getInt("thresholdPaceSecPerKm")),
            timezone = "UTC",
        )
        val activities = List(input.getJSONArray("activities").length()) { index ->
            val activity = input.getJSONArray("activities").getJSONObject(index)
            LoadActivity(
                date = LocalDate.parse(activity.getString("date")),
                durationSec = activity.getInt("durationSec"),
                averagePace = Pace.fromSecondsPerKm(activity.getInt("averagePaceSecPerKm")),
            )
        }
        return TrainingLoadEngine().calculate(
            activities = activities,
            profile = profile,
            today = today,
            sourceStatus = ActivitySourceStatus(
                lastImportedAt = Instant.parse(input.getString("lastImportedAt")),
                checkedAt = Instant.parse(input.getString("checkedAt")),
                firstActivityAt = Instant.parse(input.getString("firstActivityAt")),
            ),
            days = input.getInt("days"),
        )
    }

    private fun qualitySession(input: JSONObject) = PlannedSession(
        date = LocalDate.parse(input.getString("today")),
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = 40,
        rationale = "Qualidade planejada; o dia confirma.",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = QualityKind.CONTINUOUS,
        planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
    )

    private fun greenReadiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = ReadinessComponent.entries.toSet() - setOf(ReadinessComponent.SUBJECTIVE),
        flags = emptySet(),
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = com.meucoach.core.model.Experience.INTERMEDIATE,
        timezone = "UTC",
    )

    private fun decideVolume(input: JSONObject, snapshot: LoadSnapshot) = VolumeProgression().decide(
        targetWeekStart = LocalDate.parse(input.getJSONObject("volume").getString("targetWeekStart")),
        availabilityCeilingMinutes = input.getJSONObject("volume").getInt("availabilityCeilingMinutes"),
        references = List(input.getJSONObject("volume").getJSONArray("references").length()) { index ->
            val reference = input.getJSONObject("volume").getJSONArray("references").getJSONObject(index)
            VolumeReference(
                weekStart = LocalDate.parse(reference.getString("weekStart")),
                plannedMinutes = reference.getInt("plannedMinutes"),
                executedMinutes = reference.getInt("executedMinutes"),
                strongMinutes = reference.getInt("strongMinutes"),
                strongSessions = reference.getInt("strongSessions"),
                plannedSessions = reference.getInt("plannedSessions"),
                createdOn = LocalDate.parse(reference.getString("createdOn")),
                specialWeek = reference.getBoolean("specialWeek"),
            )
        },
        previousWeek = input.getJSONObject("volume").getJSONObject("previousWeek").toWeeklyPlan(),
        gates = VolumeGates(
            readinessGreen = true,
            loadKnown = snapshot.objectiveDataState == LoadDataState.KNOWN,
            acwrReliable = snapshot.acwrReliable,
            acwrInRange = snapshot.acwr != null &&
                snapshot.acwr in LoadSnapshot.ACWR_PROGRESSION_MIN..LoadSnapshot.ACWR_PROGRESSION_MAX,
            monotonyOk = snapshot.monotonyOk,
            strainOk = !snapshot.strainSpike || !snapshot.strainReliable,
        ),
    )

    private fun JSONObject.toWeeklyPlan(): WeeklyPlan {
        val sessions = List(getJSONArray("sessions").length()) { index ->
            val session = getJSONArray("sessions").getJSONObject(index)
            PlannedSession(
                date = LocalDate.parse(session.getString("date")),
                type = WorkoutType.valueOf(session.getString("type")),
                title = session.getString("title"),
                durationMin = session.getInt("durationMin"),
                rationale = "Semana anterior.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.valueOf(session.getString("status")),
                completionEvidence = session.optString("completionEvidence", null)
                    ?.let(CompletionEvidence::valueOf),
                activityId = session.optString("activityId", null),
            )
        }
        return WeeklyPlan(
            weekStart = LocalDate.parse(getString("weekStart")),
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = LocalDate.parse(getString("createdOn")),
        )
    }
}
