package com.meucoach.core.domain.golden

import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanUpgrade
import com.meucoach.core.domain.replan.PlanUpgradeResult
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import java.time.LocalDate

/**
 * G-UPGRADE-01 (ADR 0048 §6, FR-409): a v6 week with a DONE Tuesday and an
 * easy Friday upgrades to v7 preserving the DONE session and adding the
 * structural quality slot on Friday, without growing volume or the long run.
 */
class PlanUpgradeGoldenTest {
    @Test
    fun `upgrades the stored week preserving history`() {
        val input = readGolden("/golden/plan-upgrade/input.json")
        val expected = readGolden("/golden/plan-upgrade/expected.json")

        val today = LocalDate.parse(input.getString("today"))
        val current = input.getJSONObject("stored").toPlan(input.getString("weekStart"))
        val regenerated = input.getJSONObject("regenerated").toPlan(input.getString("weekStart"))
        val target = input.getString("targetVersion")

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Upgraded::class.java)
        val upgraded = (result as PlanUpgradeResult.Upgraded).plan

        assertThat(upgraded.engineVersion).isEqualTo(expected.getString("engineVersion"))
        assertThat(upgraded.qualityCount).isEqualTo(expected.getInt("qualityCount"))
        assertThat(upgraded.weeklyMinutes).isEqualTo(expected.getInt("weeklyMinutes"))
        assertThat(upgraded.createdOn).isEqualTo(current.createdOn)

        val preserved = upgraded.sessions.first { it.date.toString() == expected.getString("preservedDone") }
        assertThat(preserved.status).isEqualTo(SessionStatus.DONE)

        val qualityExpected = expected.getJSONObject("quality")
        val quality = upgraded.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(quality.date.toString()).isEqualTo(qualityExpected.getString("date"))
        assertThat(quality.date.dayOfWeek.name).isEqualTo(qualityExpected.getString("dayOfWeek"))
        assertThat(quality.title).isEqualTo(qualityExpected.getString("title"))
        assertThat(quality.qualityKind).isEqualTo(QualityKind.valueOf(qualityExpected.getString("kind")))
        assertThat(quality.durationMin).isEqualTo(qualityExpected.getInt("durationMin"))
        assertThat(quality.planDecision?.reason?.name).isEqualTo(qualityExpected.getString("planDecision"))

        assertThat(upgraded.sessions.map { it.title })
            .doesNotContainAnyElementsOf(expected.getJSONArray("forbiddenTitles").toStringList())

        val second = PlanUpgrade.upgrade(upgraded, regenerated, today, target, Experience.INTERMEDIATE)
        assertThat(second).isInstanceOf(PlanUpgradeResult.Skipped::class.java)
    }

    private fun JSONObject.toPlan(weekStart: String): WeeklyPlan {
        val sessions = getJSONArray("sessions")
        val parsed = List(sessions.length()) { index ->
            sessions.getJSONObject(index).toSession()
        }
        return WeeklyPlan(
            weekStart = LocalDate.parse(weekStart),
            sessions = parsed,
            weeklyMinutes = parsed.sumOf { it.durationMin ?: 0 },
            longRunMinutes = parsed.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
            qualityCount = parsed.count { it.type == WorkoutType.QUALITY },
            rationale = getString("rationale"),
            confidence = DataConfidence.valueOf(getString("confidence")),
            ruleIds = getJSONArray("ruleIds").toStringList(),
            evidenceIds = getJSONArray("evidenceIds").toStringList(),
            createdOn = LocalDate.parse(getString("createdOn")),
            engineVersion = getString("engineVersion"),
        )
    }

    private fun JSONObject.toSession(): PlannedSession = PlannedSession(
        date = LocalDate.parse(getString("date")),
        type = WorkoutType.valueOf(getString("type")),
        title = getString("title"),
        durationMin = getInt("durationMin"),
        rationale = "Base.",
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        status = SessionStatus.valueOf(getString("status")),
        completionEvidence = optString("completionEvidence", null)?.let(CompletionEvidence::valueOf),
        qualityKind = optString("qualityKind", null)?.let(QualityKind::valueOf),
        planDecision = optString("planDecision", null)?.let {
            PlanDecision(PlanDecisionReason.valueOf(it), "Oportunidade estrutural.")
        },
    )
}
