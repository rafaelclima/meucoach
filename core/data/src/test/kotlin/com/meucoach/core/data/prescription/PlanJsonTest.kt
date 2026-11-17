package com.meucoach.core.data.prescription

import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.domain.prescription.BlockKind
import com.meucoach.core.domain.prescription.WorkoutBlock
import com.meucoach.core.domain.prescription.WorkoutStructure
import com.meucoach.core.domain.prescription.WorkoutType
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C4-50 (ADR 0044/0048, FR-412): the export carries the quality decision
 * fields (`qualityKind`, `structure`, `planDecision`) and never rebuilds a
 * partial decision — unknown or missing codes decode as null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanJsonTest {
    @Test
    fun `session round-trips quality decision fields`() {
        val session = session(
            type = WorkoutType.QUALITY,
            qualityKind = "FARTLEK",
            planDecisionCode = "QUALITY_OPPORTUNITY",
            planDecisionDetail = "Oportunidade estrutural da semana.",
        )

        val json = PlanJson.sessionToJson(session)
        assertThat(json.getString("qualityKind")).isEqualTo("FARTLEK")
        assertThat(json.getString("planDecisionCode")).isEqualTo("QUALITY_OPPORTUNITY")
        assertThat(json.getString("planDecisionDetail")).isEqualTo("Oportunidade estrutural da semana.")
        assertThat(json.getJSONObject("structure").getInt("totalMin")).isEqualTo(40)

        val rebuilt = PlanJson.sessionsFromJson("[" + json.toString() + "]", "2026-11-02")
        assertThat(rebuilt).hasSize(1)
        assertThat(rebuilt.single().qualityKind).isEqualTo("FARTLEK")
        assertThat(rebuilt.single().planDecisionCode).isEqualTo("QUALITY_OPPORTUNITY")
        assertThat(rebuilt.single().planDecisionDetail).isEqualTo("Oportunidade estrutural da semana.")
        assertThat(JSONObject(rebuilt.single().structureJson).getInt("totalMin")).isEqualTo(40)
    }

    @Test
    fun `unknown quality kind decodes as null without losing the decision`() {
        val json = PlanJson.sessionToJson(
            session(
                type = WorkoutType.QUALITY,
                qualityKind = "FARTLEK",
                planDecisionCode = "QUALITY_OPPORTUNITY",
                planDecisionDetail = "Oportunidade estrutural da semana.",
            ),
        )
        json.put("qualityKind", "BOGUS")

        val rebuilt = PlanJson.sessionsFromJson("[" + json.toString() + "]", "2026-11-02")

        assertThat(rebuilt).hasSize(1)
        assertThat(rebuilt.single().qualityKind).isNull()
        assertThat(rebuilt.single().planDecisionCode).isEqualTo("QUALITY_OPPORTUNITY")
        assertThat(rebuilt.single().planDecisionDetail).isEqualTo("Oportunidade estrutural da semana.")
    }

    @Test
    fun `detail without a known code decodes as null decision`() {
        val json = PlanJson.sessionToJson(
            session(
                type = WorkoutType.QUALITY,
                planDecisionCode = "QUALITY_OPPORTUNITY",
                planDecisionDetail = "Oportunidade estrutural da semana.",
            ),
        )
        json.put("planDecisionCode", "BOGUS")

        val rebuilt = PlanJson.sessionsFromJson("[" + json.toString() + "]", "2026-11-02")

        assertThat(rebuilt).hasSize(1)
        assertThat(rebuilt.single().planDecisionCode).isNull()
        assertThat(rebuilt.single().planDecisionDetail).isNull()
    }

    @Test
    fun `kind on a non-quality session is dropped`() {
        val json = PlanJson.sessionToJson(session(type = WorkoutType.EASY))
        json.put("qualityKind", "FARTLEK")

        val rebuilt = PlanJson.sessionsFromJson("[" + json.toString() + "]", "2026-11-02")

        assertThat(rebuilt).hasSize(1)
        assertThat(rebuilt.single().qualityKind).isNull()
    }

    @Test
    fun `legacy json without decision keys decodes as nulls`() {
        val json = PlanJson.sessionToJson(session(type = WorkoutType.QUALITY))
        json.remove("qualityKind")
        json.remove("planDecisionCode")
        json.remove("planDecisionDetail")
        json.remove("structure")

        val rebuilt = PlanJson.sessionsFromJson("[" + json.toString() + "]", "2026-11-02")

        assertThat(rebuilt).hasSize(1)
        assertThat(rebuilt.single().qualityKind).isNull()
        assertThat(rebuilt.single().planDecisionCode).isNull()
        assertThat(rebuilt.single().planDecisionDetail).isNull()
        assertThat(rebuilt.single().structureJson).isNull()
    }

    private fun session(
        type: WorkoutType,
        qualityKind: String? = null,
        planDecisionCode: String? = null,
        planDecisionDetail: String? = null,
    ) = PlannedSessionEntity(
        id = "2026-11-02-2026-11-04",
        weekStart = "2026-11-02",
        date = "2026-11-04",
        type = type,
        title = "Fartlek leve",
        durationMin = 40,
        rationale = "Oportunidade estrutural; o dia confirma.",
        ruleIdsJson = "[\"R-PLAN-02\"]",
        evidenceIdsJson = "[\"EV-SEILER-2010\"]",
        targetsJson = "{}",
        status = com.meucoach.core.domain.prescription.SessionStatus.PLANNED,
        skipReason = null,
        completedAt = null,
        structureJson = WorkoutStructureJson.toJson(
            WorkoutStructure(
                version = 1,
                warmupMin = 10,
                blocks = listOf(
                    WorkoutBlock(
                        kind = BlockKind.WORK,
                        repetitions = 5,
                        workSec = 180,
                        recoverySec = 120,
                        target = "I",
                    ),
                ),
                cooldownMin = 5,
                totalMin = 40,
            ),
        ),
        qualityKind = qualityKind,
        planDecisionCode = planDecisionCode,
        planDecisionDetail = planDecisionDetail,
    )
}
