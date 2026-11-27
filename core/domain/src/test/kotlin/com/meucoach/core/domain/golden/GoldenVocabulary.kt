package com.meucoach.core.domain.golden

import com.meucoach.core.domain.content.CheckedText
import com.meucoach.core.domain.content.ForbiddenVocabulary
import com.meucoach.core.domain.content.VocabularyGate
import com.meucoach.core.domain.prescription.WeeklyPlan
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject

/**
 * Vocabulary check shared by the plan golden tests (ADR 0071 F1).
 *
 * `expected.json` declares `forbiddenVocabulary` mirroring
 * [ForbiddenVocabulary.DISPLAYS]; the drift assertion below keeps the copy in
 * sync with the single source. The gate then scans `plan.rationale` plus each
 * session `title`/`rationale` against the baseline the catalog tests use,
 * scoped to this fixture file, so the blind spot in `noRace` cannot return:
 * every block is checked, not just the one without a race.
 */
internal fun assertGoldenVocabulary(
    expected: JSONObject,
    namedPlans: List<Pair<String, WeeklyPlan>>,
    fixture: String,
) {
    assertThat(expected.getJSONArray("forbiddenVocabulary").toStringList())
        .describedAs("forbiddenVocabulary in $fixture must mirror the canonical list")
        .containsExactlyElementsOf(ForbiddenVocabulary.DISPLAYS)
    val texts = namedPlans.flatMap { (block, plan) ->
        listOf(CheckedText("$fixture:$block :: plan.rationale", plan.rationale)) +
            plan.sessions.sortedBy { it.date }.flatMap { session ->
                listOf(
                    CheckedText("$fixture:$block :: session[${session.date.dayOfWeek}].title", session.title),
                    CheckedText("$fixture:$block :: session[${session.date.dayOfWeek}].rationale", session.rationale),
                )
            }
    }
    VocabularyGate.assertClean(texts, fixture)
}
