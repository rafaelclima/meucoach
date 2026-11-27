package com.meucoach.core.domain.content

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit contract of the forbidden vocabulary itself (ADR 0071 F1).
 *
 * The gate tests assert user texts against this list; these tests pin the
 * matching semantics (word boundaries, approved exceptions) so a change in
 * behavior fails here first, with a small message.
 */
class ForbiddenVocabularyTest {
    @Test
    fun `lists twenty eight blacklist rows plus the voice rule`() {
        assertThat(ForbiddenVocabulary.TERMS).hasSize(29)
        assertThat(ForbiddenVocabulary.DISPLAYS).contains(
            "ACWR",
            "VDOT",
            "qualidade",
            "pace",
            "taper",
            "micro-dose",
            "PEAK / BUILD / TAPER / BASE / RECOVERY",
            "R- / EV-",
            "motor",
        )
    }

    @Test
    fun `flags enum names without flagging the canonical labels`() {
        assertThat(ForbiddenVocabulary.violates("Fase BUILD.")).isTrue()
        assertThat(ForbiddenVocabulary.violates("Fase TAPER.")).isTrue()
        assertThat(ForbiddenVocabulary.violates("Etapa: Semana de prova.")).isFalse()
        assertThat(ForbiddenVocabulary.violates("Fase de construção.")).isFalse()
    }

    @Test
    fun `keeps the approved pace title but flags pace in prose`() {
        assertThat(ForbiddenVocabulary.violates("Pace de prova 5K")).isFalse()
        assertThat(ForbiddenVocabulary.violates("Use o pace do bloco.")).isTrue()
        assertThat(ForbiddenVocabulary.violates("o pace é referência")).isTrue()
    }

    @Test
    fun `keeps facil as a zone label and adjective but flags the session name`() {
        assertThat(ForbiddenVocabulary.violates("Fácil (Z2)")).isFalse()
        assertThat(ForbiddenVocabulary.violates("dia fácil")).isFalse()
        assertThat(ForbiddenVocabulary.violates("Fácil")).isTrue()
    }

    @Test
    fun `flags rule and evidence ids`() {
        assertThat(ForbiddenVocabulary.violates("ver R-PLAN-01 e EV-SEILER-2010")).isTrue()
        assertThat(ForbiddenVocabulary.violates("sem ids aqui")).isFalse()
    }

    @Test
    fun `flags motor but keeps runner slang untouched`() {
        assertThat(ForbiddenVocabulary.violates("direto do motor")).isTrue()
        assertThat(ForbiddenVocabulary.violates("Bora ver o que importa?")).isFalse()
        assertThat(ForbiddenVocabulary.violates("Me enrolei na explicação.")).isFalse()
    }

    @Test
    fun `blank text never violates`() {
        assertThat(ForbiddenVocabulary.violates("")).isFalse()
        assertThat(ForbiddenVocabulary.violates("   ")).isFalse()
        assertThat(ForbiddenVocabulary.findViolations("")).isEmpty()
    }

    @Test
    fun `reports the term and an excerpt for an actionable failure`() {
        val violations = ForbiddenVocabulary.findViolations("Sem progressão: ACWR 1,62 fora da faixa.")

        assertThat(violations).hasSize(1)
        assertThat(violations.single().term.display).isEqualTo("ACWR")
        assertThat(violations.single().matched).isEqualTo("ACWR")
        assertThat(violations.single().excerpt).contains("ACWR")
    }
}
