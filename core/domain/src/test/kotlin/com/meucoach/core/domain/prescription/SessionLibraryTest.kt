package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * ADR 0043/0047: session catalog by (phase, level, distance).
 *
 * The catalog exposes easy and quality templates separately; quality rotation
 * decides which stimulus a week receives. There is no legacy weekly projection.
 * The generator (C4-17) consumes these APIs directly.
 */
class SessionLibraryTest {
    @Test
    fun `beginner has no structured quality outside the taper micro-dose`() {
        listOf(MesoPhase.BASE, MesoPhase.BUILD, MesoPhase.PEAK).forEach { phase ->
            assertThat(SessionLibrary.qualityTemplates(phase, Experience.BEGINNER))
                .describedAs("phase $phase")
                .isEmpty()
        }

        val taper = SessionLibrary.qualityTemplates(MesoPhase.TAPER, Experience.BEGINNER)
        assertThat(taper.map { it.qualityKind }).containsExactly(QualityKind.STRIDES_ONLY)
        assertThat(taper.single().type).isEqualTo(WorkoutType.QUALITY)
    }

    @Test
    fun `beginner easy catalog carries strides and the optional intro fartlek`() {
        val base = SessionLibrary.easyTemplates(MesoPhase.BASE, Experience.BEGINNER)
        assertThat(base.map { it.role }).containsExactly(SessionRole.EASY_RUN, SessionRole.STRIDES)

        val withIntro = SessionLibrary.easyTemplates(
            MesoPhase.BUILD,
            Experience.BEGINNER,
            includeIntroFartlek = true,
        )
        assertThat(withIntro.map { it.role }).contains(
            SessionRole.EASY_RUN,
            SessionRole.STRIDES,
            SessionRole.INTRO_FARTLEK,
        )
        assertThat(withIntro.first { it.role == SessionRole.INTRO_FARTLEK }.type)
            .isEqualTo(WorkoutType.EASY)
    }

    @Test
    fun `beginner taper easy catalog carries one easy strides template`() {
        val beginner = SessionLibrary.easyTemplates(MesoPhase.TAPER, Experience.BEGINNER)
        assertThat(beginner.map { it.role }).containsExactly(SessionRole.EASY_RUN, SessionRole.STRIDES)

        val strides = beginner.first { it.role == SessionRole.STRIDES }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.qualityKind).isNull()
        assertThat(strides.structure).isNotNull

        val intermediate = SessionLibrary.easyTemplates(MesoPhase.TAPER, Experience.INTERMEDIATE)
        assertThat(intermediate.map { it.role }).containsExactly(SessionRole.EASY_RUN)
    }

    @Test
    fun `build gives two qualities with distinct kinds to intermediate and advanced`() {
        val intermediate = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.INTERMEDIATE,
            weekInPhase = 0,
            maxQualities = 2,
        )
        assertThat(intermediate.map { it.role })
            .containsExactly(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
        assertThat(intermediate.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS, QualityKind.INTERVALS)

        val advanced = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            weekInPhase = 0,
            maxQualities = 2,
        )
        assertThat(advanced.map { it.qualityKind })
            .containsExactly(QualityKind.INTERVALS, QualityKind.HILLS)
    }

    @Test
    fun `taper only carries the strides micro-dose for every level`() {
        Experience.entries.forEach { level ->
            val qualities = SessionLibrary.qualityTemplates(MesoPhase.TAPER, level)

            assertThat(qualities.map { it.role })
                .describedAs("level $level")
                .containsExactly(SessionRole.STRIDES)
            val strides = qualities.single()
            assertThat(strides.type).isEqualTo(WorkoutType.QUALITY)
            assertThat(strides.durationPolicy).isEqualTo(DurationPolicy.MICRO_DOSE_25)
            assertThat(strides.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        }
    }

    @Test
    fun `recovery is easy only for every level`() {
        Experience.entries.forEach { level ->
            val easy = SessionLibrary.easyTemplates(MesoPhase.RECOVERY, level)

            assertThat(easy).describedAs("level $level").hasSize(1)
            assertThat(easy.single().role).isEqualTo(SessionRole.EASY_RUN)
            assertThat(SessionLibrary.qualityTemplates(MesoPhase.RECOVERY, level))
                .describedAs("level $level")
                .isEmpty()
        }
    }

    @Test
    fun `peak carries one specific race-pace quality per distance`() {
        RaceDistance.entries.forEach { distance ->
            val intermediate = SessionLibrary.qualityRotation(
                phase = MesoPhase.PEAK,
                level = Experience.INTERMEDIATE,
                distance = distance,
                weekInPhase = 0,
            )
            assertThat(intermediate.map { it.qualityKind })
                .describedAs("intermediate $distance")
                .containsExactly(QualityKind.RACE_PACE)

            val advanced = SessionLibrary.qualityRotation(
                phase = MesoPhase.PEAK,
                level = Experience.ADVANCED,
                distance = distance,
                weekInPhase = 0,
            )
            assertThat(advanced.first().qualityKind)
                .describedAs("advanced $distance")
                .isEqualTo(QualityKind.RACE_PACE)
        }
    }

    @Test
    fun `base exposes easy and strides for every level`() {
        Experience.entries.forEach { level ->
            val easy = SessionLibrary.easyTemplates(MesoPhase.BASE, level)

            assertThat(easy.map { it.role })
                .describedAs("level $level")
                .contains(SessionRole.EASY_RUN, SessionRole.STRIDES)
                .doesNotContain(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
        }
    }

    @Test
    fun `every template is auditable and displayable`() {
        MesoPhase.entries.forEach { phase ->
            Experience.entries.forEach { level ->
                val templates = SessionLibrary.easyTemplates(phase, level) +
                    SessionLibrary.qualityTemplates(phase, level) +
                    RaceDistance.entries.flatMap { distance ->
                        SessionLibrary.qualityTemplates(phase, level, distance)
                    }
                templates.forEach { template ->
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
                    if (template.type == WorkoutType.QUALITY) {
                        assertThat(template.structure)
                            .describedAs("$phase/$level/${template.role}")
                            .isNotNull()
                    }
                }
            }
        }
    }

    @Test
    fun `library is deterministic`() {
        val first = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            weekInPhase = 1,
            maxQualities = 2,
        )
        val second = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            weekInPhase = 1,
            maxQualities = 2,
        )

        assertThat(first).isEqualTo(second)
    }
}
