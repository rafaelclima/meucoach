package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

/**
 * C4-16 — deterministic weekly rotation (ADR 0047 §2.7).
 *
 * `weekInPhase` comes from the plan/mesocycle, never from the clock. The
 * rotation walks the eligible catalog so two consecutive weeks do not repeat
 * the main stimulus, the second quality always has a different kind, and the
 * light `BASE` fartlek only appears in the second half of the phase.
 */
class QualityRotationTest {
    private val allPhases = MesoPhase.entries.toList()
    private val allLevels = Experience.entries.toList()
    private val allDistances = RaceDistance.entries.toList()

    @Test
    fun `same inputs select the same quality`() {
        val first = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.INTERMEDIATE,
            weekInPhase = 2,
            maxQualities = 2,
            phaseWeeks = 4,
        )
        val second = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.INTERMEDIATE,
            weekInPhase = 2,
            maxQualities = 2,
            phaseWeeks = 4,
        )

        assertThat(first).isEqualTo(second)
        assertThat(first.map { it.role })
            .containsExactly(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
    }

    @Test
    fun `consecutive weeks rotate the main kind in build`() {
        val kinds = (0..3).map { week ->
            SessionLibrary.qualityRotation(
                phase = MesoPhase.BUILD,
                level = Experience.INTERMEDIATE,
                weekInPhase = week,
            ).single().qualityKind
        }

        assertThat(kinds).containsExactly(
            QualityKind.CONTINUOUS,
            QualityKind.INTERVALS,
            QualityKind.FARTLEK,
            QualityKind.HILLS,
        )
        assertThat(kinds).doesNotHaveDuplicates()
    }

    @Test
    fun `rotation wraps after the catalog size`() {
        val first = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            weekInPhase = 0,
        )
        val wrapped = SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            weekInPhase = 4,
        )

        assertThat(wrapped).isEqualTo(first)
    }

    @Test
    fun `second quality never repeats the main kind across the matrix`() {
        rotationCases().forEach { case ->
            (0..5).forEach { week ->
                val single = SessionLibrary.qualityRotation(
                    phase = case.phase,
                    level = case.level,
                    distance = case.distance,
                    weekInPhase = week,
                    phaseWeeks = 6,
                )
                val selection = SessionLibrary.qualityRotation(
                    phase = case.phase,
                    level = case.level,
                    distance = case.distance,
                    weekInPhase = week,
                    maxQualities = 2,
                    phaseWeeks = 6,
                )

                val described = "${case.phase}/${case.level}/${case.distance}/week $week"
                assertThat(selection.firstOrNull())
                    .describedAs(described)
                    .isEqualTo(single.singleOrNull()?.copy(role = SessionRole.QUALITY_MAIN))
                assertThat(selection.map { it.role })
                    .describedAs(described)
                    .containsExactlyElementsOf(expectedRoles(selection.size))
                if (selection.size == 2) {
                    assertThat(selection[0].qualityKind)
                        .describedAs(described)
                        .isNotEqualTo(selection[1].qualityKind)
                }
            }
        }
    }

    @Test
    fun `base exposes the light fartlek only in the second half of the phase`() {
        listOf(Experience.INTERMEDIATE, Experience.ADVANCED).forEach { level ->
            listOf(0, 1).forEach { week ->
                assertThat(
                    SessionLibrary.qualityRotation(
                        phase = MesoPhase.BASE,
                        level = level,
                        weekInPhase = week,
                        phaseWeeks = 4,
                    ),
                )
                    .describedAs("$level week $week")
                    .isEmpty()
            }
            listOf(2, 3).forEach { week ->
                val selection = SessionLibrary.qualityRotation(
                    phase = MesoPhase.BASE,
                    level = level,
                    weekInPhase = week,
                    phaseWeeks = 4,
                )

                assertThat(selection).describedAs("$level week $week").hasSize(1)
                assertThat(selection.single().qualityKind).isEqualTo(QualityKind.FARTLEK)
            }
        }
    }

    @Test
    fun `base uses the later half for an odd phase length`() {
        listOf(0, 1, 2).forEach { week ->
            assertThat(
                SessionLibrary.qualityRotation(
                    phase = MesoPhase.BASE,
                    level = Experience.INTERMEDIATE,
                    weekInPhase = week,
                    phaseWeeks = 5,
                ),
            )
                .describedAs("week $week")
                .isEmpty()
        }

        listOf(3, 4).forEach { week ->
            assertThat(
                SessionLibrary.qualityRotation(
                    phase = MesoPhase.BASE,
                    level = Experience.INTERMEDIATE,
                    weekInPhase = week,
                    phaseWeeks = 5,
                ).map { it.qualityKind },
            )
                .describedAs("week $week")
                .containsExactly(QualityKind.FARTLEK)
        }
    }

    @Test
    fun `base stays conservative without a phase length`() {
        listOf(Experience.INTERMEDIATE, Experience.ADVANCED).forEach { level ->
            assertThat(
                SessionLibrary.qualityRotation(
                    phase = MesoPhase.BASE,
                    level = level,
                    weekInPhase = 3,
                ),
            )
                .describedAs(level.name)
                .isEmpty()
        }
    }

    @Test
    fun `other phases rotate without a phase length`() {
        listOf(MesoPhase.BUILD, MesoPhase.PEAK).forEach { phase ->
            assertThat(
                SessionLibrary.qualityRotation(
                    phase = phase,
                    level = Experience.INTERMEDIATE,
                    weekInPhase = 2,
                ),
            )
                .describedAs(phase.name)
                .isNotEmpty()
        }
    }

    @Test
    fun `beginner never receives structured quality from the rotation`() {
        listOf(MesoPhase.BASE, MesoPhase.BUILD, MesoPhase.PEAK).forEach { phase ->
            assertThat(
                SessionLibrary.qualityRotation(
                    phase = phase,
                    level = Experience.BEGINNER,
                    weekInPhase = 3,
                    maxQualities = 2,
                    phaseWeeks = 6,
                ),
            )
                .describedAs(phase.name)
                .isEmpty()
        }

        val taper = SessionLibrary.qualityRotation(
            phase = MesoPhase.TAPER,
            level = Experience.BEGINNER,
            weekInPhase = 0,
            maxQualities = 2,
        )
        assertThat(taper).hasSize(1)
        assertThat(taper.single().qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
    }

    @Test
    fun `single template phases return only the main quality`() {
        val taper = SessionLibrary.qualityRotation(
            phase = MesoPhase.TAPER,
            level = Experience.INTERMEDIATE,
            weekInPhase = 0,
            maxQualities = 2,
        )
        assertThat(taper.map { it.role }).containsExactly(SessionRole.QUALITY_MAIN)

        val peak = SessionLibrary.qualityRotation(
            phase = MesoPhase.PEAK,
            level = Experience.INTERMEDIATE,
            distance = RaceDistance.FIVE_K,
            weekInPhase = 0,
            maxQualities = 2,
        )
        assertThat(peak.map { it.qualityKind }).containsExactly(QualityKind.RACE_PACE)
    }

    @Test
    fun `rotation keeps the executable structure and the citations`() {
        SessionLibrary.qualityRotation(
            phase = MesoPhase.BUILD,
            level = Experience.ADVANCED,
            distance = RaceDistance.TEN_K,
            weekInPhase = 1,
            maxQualities = 2,
        ).forEach { template ->
            assertThat(template.structure).isNotNull()
            assertThat(template.ruleIds).contains("R-PER-02")
            assertThat(template.evidenceIds).isNotEmpty()
        }
    }

    @Test
    fun `rotation skips a candidate that repeats the main kind`() {
        val catalog = SessionLibrary.qualityTemplates(MesoPhase.BUILD, Experience.INTERMEDIATE)
        val continuous = catalog.single { it.qualityKind == QualityKind.CONTINUOUS }
        val fartlek = catalog.single { it.qualityKind == QualityKind.FARTLEK }
        val duplicateKind = continuous.copy(title = "Ritmo forte repetido")

        val selection = SessionLibrary.rotateQualities(
            templates = listOf(continuous, duplicateKind, fartlek),
            weekInPhase = 0,
            maxQualities = 2,
        )

        assertThat(selection.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS, QualityKind.FARTLEK)
    }

    @Test
    fun `rotation returns a single quality when every kind repeats`() {
        val catalog = SessionLibrary.qualityTemplates(MesoPhase.BUILD, Experience.INTERMEDIATE)
        val continuous = catalog.single { it.qualityKind == QualityKind.CONTINUOUS }

        val selection = SessionLibrary.rotateQualities(
            templates = listOf(continuous, continuous.copy(title = "Outro contínuo")),
            weekInPhase = 1,
            maxQualities = 2,
        )

        assertThat(selection.map { it.qualityKind }).containsExactly(QualityKind.CONTINUOUS)
        assertThat(selection.single().role).isEqualTo(SessionRole.QUALITY_MAIN)
    }

    @Test
    fun `rotation rejects an invalid week quality count or phase length`() {
        assertThatThrownBy {
            SessionLibrary.qualityRotation(MesoPhase.BUILD, Experience.INTERMEDIATE, weekInPhase = -1)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("weekInPhase")

        assertThatThrownBy {
            SessionLibrary.qualityRotation(
                MesoPhase.BUILD,
                Experience.INTERMEDIATE,
                weekInPhase = 0,
                maxQualities = 0,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxQualities")

        assertThatThrownBy {
            SessionLibrary.qualityRotation(
                MesoPhase.BUILD,
                Experience.INTERMEDIATE,
                weekInPhase = 0,
                maxQualities = 3,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxQualities")

        assertThatThrownBy {
            SessionLibrary.qualityRotation(
                MesoPhase.BASE,
                Experience.INTERMEDIATE,
                weekInPhase = 0,
                phaseWeeks = 0,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("phaseWeeks")

        assertThatThrownBy {
            SessionLibrary.qualityRotation(
                MesoPhase.BASE,
                Experience.INTERMEDIATE,
                weekInPhase = 4,
                phaseWeeks = 4,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("weekInPhase")
    }

    private fun rotationCases(): List<RotationCase> = buildList {
        allPhases.forEach { phase ->
            allLevels.forEach { level ->
                add(RotationCase(phase, level, distance = null))
                allDistances.forEach { distance ->
                    add(RotationCase(phase, level, distance))
                }
            }
        }
    }

    private fun expectedRoles(size: Int): List<SessionRole> = when (size) {
        0 -> emptyList()
        1 -> listOf(SessionRole.QUALITY_MAIN)
        else -> listOf(SessionRole.QUALITY_MAIN, SessionRole.QUALITY_SECOND)
    }

    private data class RotationCase(val phase: MesoPhase, val level: Experience, val distance: RaceDistance?)
}
