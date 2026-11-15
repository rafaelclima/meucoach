package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Fatia 2 — ADR 0043: biblioteca de sessões por (fase, nível) (TDD RED).
 *
 * Contrato: [SessionLibrary.templates] devolve estruturas de sessão sem pace
 * inventado (durações resolve a fiação pela disponibilidade); iniciante nunca
 * recebe qualidade contínua/intervalada; `TAPER` só tem micro-dose de strides;
 * `RECOVERY` é só fácil; `BASE` não tem qualidade principal. Tudo cita
 * `R-PER-02`.
 */
class SessionLibraryTest {
    @Test
    fun `beginner never receives continuous or interval quality in any phase`() {
        MesoPhase.entries.forEach { phase ->
            val kinds = SessionLibrary.templates(phase, Experience.BEGINNER)
                .mapNotNull { it.qualityKind }

            assertThat(kinds)
                .describedAs("phase $phase")
                .doesNotContain(QualityKind.CONTINUOUS, QualityKind.INTERVALS)
        }
    }

    @Test
    fun `beginner quality is strides only`() {
        MesoPhase.entries.forEach { phase ->
            val qualities = SessionLibrary.templates(phase, Experience.BEGINNER)
                .filter { it.type == WorkoutType.QUALITY }

            assertThat(qualities.map { it.qualityKind })
                .describedAs("phase $phase")
                .doesNotContain(QualityKind.CONTINUOUS, QualityKind.INTERVALS)
            assertThat(qualities).hasSizeLessThanOrEqualTo(1)
        }
    }

    @Test
    fun `build gives two qualities to intermediate and advanced`() {
        val intermediate = SessionLibrary.templates(MesoPhase.BUILD, Experience.INTERMEDIATE)
        assertThat(intermediate.filter { it.role == SessionRole.QUALITY_MAIN }.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS)
        assertThat(intermediate.filter { it.role == SessionRole.QUALITY_SECOND }.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS)

        val advanced = SessionLibrary.templates(MesoPhase.BUILD, Experience.ADVANCED)
        assertThat(
            advanced.filter { it.role == SessionRole.QUALITY_MAIN || it.role == SessionRole.QUALITY_SECOND }
                .map { it.qualityKind },
        ).containsExactly(QualityKind.INTERVALS, QualityKind.INTERVALS)
    }

    @Test
    fun `taper only carries the strides micro-dose for every level`() {
        Experience.entries.forEach { level ->
            val templates = SessionLibrary.templates(MesoPhase.TAPER, level)

            assertThat(templates.map { it.role })
                .describedAs("level $level")
                .doesNotContain(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
            val strides = templates.single { it.role == SessionRole.STRIDES }
            assertThat(strides.type).isEqualTo(WorkoutType.QUALITY)
            assertThat(strides.durationPolicy).isEqualTo(DurationPolicy.MICRO_DOSE_25)
        }
    }

    @Test
    fun `recovery is easy only for every level`() {
        Experience.entries.forEach { level ->
            val templates = SessionLibrary.templates(MesoPhase.RECOVERY, level)

            assertThat(templates).describedAs("level $level").hasSize(1)
            assertThat(templates.single().role).isEqualTo(SessionRole.EASY_RUN)
        }
    }

    @Test
    fun `peak carries one specific quality per level`() {
        val intermediate = SessionLibrary.templates(MesoPhase.PEAK, Experience.INTERMEDIATE)
        assertThat(intermediate.filter { it.role == SessionRole.QUALITY_MAIN }.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS)
        assertThat(intermediate.map { it.role }).doesNotContain(SessionRole.QUALITY_SECOND)

        val advanced = SessionLibrary.templates(MesoPhase.PEAK, Experience.ADVANCED)
        assertThat(advanced.filter { it.role == SessionRole.QUALITY_MAIN }.map { it.qualityKind })
            .containsExactly(QualityKind.INTERVALS)
    }

    @Test
    fun `base has no main quality for any level`() {
        Experience.entries.forEach { level ->
            val roles = SessionLibrary.templates(MesoPhase.BASE, level).map { it.role }

            assertThat(roles)
                .describedAs("level $level")
                .contains(SessionRole.EASY_RUN, SessionRole.LONG_RUN, SessionRole.STRIDES)
                .doesNotContain(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
        }
    }

    @Test
    fun `every template is auditable and displayable`() {
        MesoPhase.entries.forEach { phase ->
            Experience.entries.forEach { level ->
                SessionLibrary.templates(phase, level).forEach { template ->
                    assertThat(template.title)
                        .describedAs("$phase/$level/${template.role}")
                        .isNotBlank()
                    assertThat(template.blurb)
                        .describedAs("$phase/$level/${template.role}")
                        .isNotBlank()
                    assertThat(template.ruleIds)
                        .describedAs("$phase/$level/${template.role}")
                        .contains("R-PER-02")
                    assertThat(template.evidenceIds)
                        .describedAs("$phase/$level/${template.role}")
                        .isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `library is deterministic`() {
        assertThat(SessionLibrary.templates(MesoPhase.BUILD, Experience.ADVANCED)).isEqualTo(
            SessionLibrary.templates(MesoPhase.BUILD, Experience.ADVANCED),
        )
    }
}
