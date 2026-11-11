package com.meucoach.app.ui.today

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Regression gate for ADR 0029: the F4 educational copy must stay free of
 * numbers, units, doses and rule/evidence IDs, and must match the approved text.
 */
class BriefingFuelContentTest {
    @Test
    fun `approved copy contains no numbers, units or rule and evidence ids`() {
        BriefingFuelContent.allStrings.forEach { text ->
            assertThat(text).doesNotMatch(".*\\d.*")
            assertThat(text).doesNotContainIgnoringCase("ml")
            assertThat(text).doesNotContainIgnoringCase("kcal")
            assertThat(text).doesNotContainIgnoringCase("macro")
            assertThat(text).doesNotContain("%")
            assertThat(text).doesNotContainIgnoringCase("eletrólito")
            assertThat(text).doesNotContainIgnoringCase("suplemento ")
            assertThat(text).doesNotContain("R-")
            assertThat(text).doesNotContain("EV-")
        }
    }

    @Test
    fun `hydration and nutrition copy matches the approved ADR 0029 text`() {
        assertThat(BriefingFuelContent.BEFORE_RUN)
            .isEqualTo("Comece o treino já hidratado. Nos minutos antes, beba água com calma.")
        assertThat(BriefingFuelContent.DURING)
            .isEqualTo(
                "Em corridas curtas, beba quando sentir sede. Em treinos longos ou no calor, planeje pausas para beber.",
            )
        assertThat(BriefingFuelContent.AFTER)
            .isEqualTo("Nas horas depois do treino, reponha líquidos junto com a alimentação de sempre.")
        assertThat(BriefingFuelContent.INDIVIDUAL_NOTE)
            .isEqualTo(
                "O quanto beber varia de pessoa para pessoa — suor, clima, duração. O MeuCoach não calcula quanto você deve beber.",
            )
        assertThat(BriefingFuelContent.NUTRITION_REFERRAL)
            .isEqualTo(
                "O MeuCoach não prescreve dietas nem suplementos. Para um plano alimentar individual, procure um nutricionista.",
            )
    }
}
