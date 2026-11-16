package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

/**
 * C4-15 — quality catalog by phase, level and distance (ADR 0047 §2.1–2.5).
 *
 * The catalog exposes every eligible stimulus; the weekly rotation (C4-16)
 * and the generator (C4-17) choose one or two. The invariants here are the
 * guardrails: beginner never receives structured quality outside the taper
 * micro-dose, intermediate `BUILD` is no longer continuous-only, `PEAK` is
 * specific to the race distance, and every quality template carries an
 * executable structure that fits its own duration.
 */
class QualityCatalogTest {
    private val structuredPhases = listOf(MesoPhase.BASE, MesoPhase.BUILD, MesoPhase.PEAK)
    private val allPhases = MesoPhase.entries.toList()
    private val allDistances = RaceDistance.entries.toList()

    @Test
    fun `beginner has no structured quality in base build or peak`() {
        structuredPhases.forEach { phase ->
            assertThat(SessionLibrary.qualityTemplates(phase, Experience.BEGINNER))
                .describedAs("phase $phase")
                .isEmpty()
            allDistances.forEach { distance ->
                assertThat(SessionLibrary.qualityTemplates(phase, Experience.BEGINNER, distance))
                    .describedAs("phase $phase, distance $distance")
                    .isEmpty()
            }
        }
    }

    @Test
    fun `beginner taper keeps only the strides micro dose`() {
        val templates = SessionLibrary.qualityTemplates(MesoPhase.TAPER, Experience.BEGINNER)

        assertThat(templates).hasSize(1)
        val strides = templates.single()
        assertThat(strides.role).isEqualTo(SessionRole.STRIDES)
        assertThat(strides.type).isEqualTo(WorkoutType.QUALITY)
        assertThat(strides.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        assertThat(strides.durationPolicy).isEqualTo(DurationPolicy.MICRO_DOSE_25)
        assertThat(strides.structure).isNotNull()
    }

    @Test
    fun `intermediate build exposes continuous intervals fartlek and hills`() {
        val kinds = SessionLibrary.qualityTemplates(MesoPhase.BUILD, Experience.INTERMEDIATE)
            .mapNotNull { it.qualityKind }

        assertThat(kinds).containsExactly(
            QualityKind.CONTINUOUS,
            QualityKind.INTERVALS,
            QualityKind.FARTLEK,
            QualityKind.HILLS,
        )
    }

    @Test
    fun `advanced build has more volume than intermediate`() {
        val intermediate = SessionLibrary.qualityTemplates(MesoPhase.BUILD, Experience.INTERMEDIATE)
        val advanced = SessionLibrary.qualityTemplates(MesoPhase.BUILD, Experience.ADVANCED)

        assertThat(advanced.mapNotNull { it.qualityKind })
            .contains(QualityKind.INTERVALS, QualityKind.HILLS, QualityKind.CONTINUOUS, QualityKind.FARTLEK)
        val intermediateVolume = intermediate.sumOf { requireNotNull(it.structure).totalMin }
        val advancedVolume = advanced.sumOf { requireNotNull(it.structure).totalMin }
        assertThat(advancedVolume).isGreaterThan(intermediateVolume)
    }

    @Test
    fun `base exposes a light fartlek only for intermediate and advanced`() {
        assertThat(SessionLibrary.qualityTemplates(MesoPhase.BASE, Experience.BEGINNER)).isEmpty()

        listOf(Experience.INTERMEDIATE, Experience.ADVANCED).forEach { level ->
            val template = SessionLibrary.qualityTemplates(MesoPhase.BASE, level).single()
            assertThat(template.qualityKind).isEqualTo(QualityKind.FARTLEK)
            assertThat(template.durationPolicy).isEqualTo(DurationPolicy.QUALITY_SIZED)
            val block = requireNotNull(template.structure).blocks.single()
            assertThat(block.repetitions).isEqualTo(6)
            assertThat(block.workSec).isEqualTo(30)
            assertThat(block.recoverySec).isEqualTo(90)
        }
    }

    @Test
    fun `peak 5k uses race pace with the current 5k target`() {
        val template = SessionLibrary.qualityTemplates(
            MesoPhase.PEAK,
            Experience.INTERMEDIATE,
            RaceDistance.FIVE_K,
        ).single()

        assertThat(template.qualityKind).isEqualTo(QualityKind.RACE_PACE)
        assertThat(template.title).contains("5K")
        val structure = requireNotNull(template.structure)
        assertThat(structure.blocks).anySatisfy { block ->
            assertThat(block.kind).isEqualTo(BlockKind.RACE_PACE)
            assertThat(block.target).isEqualTo("GOAL_5K")
            assertThat(block.repetitions).isEqualTo(5)
            assertThat(block.workSec).isEqualTo(240)
        }
        assertThat(template.blurb).contains("não na meta")
    }

    @Test
    fun `peak is specific for every race distance`() {
        val targets = allDistances.associateWith { distance ->
            val template = SessionLibrary.qualityTemplates(
                MesoPhase.PEAK,
                Experience.INTERMEDIATE,
                distance,
            ).single()
            requireNotNull(template.structure).blocks.single().target
        }

        assertThat(targets[RaceDistance.FIVE_K]).isEqualTo("GOAL_5K")
        assertThat(targets[RaceDistance.TEN_K]).isEqualTo("GOAL_10K")
        assertThat(targets[RaceDistance.HALF_MARATHON]).isEqualTo("GOAL_HALF")
        assertThat(targets[RaceDistance.MARATHON]).isEqualTo("GOAL_MARATHON")
        assertThat(targets.values).doesNotHaveDuplicates()
    }

    @Test
    fun `advanced peak combines race pace with a second specific quality`() {
        val fiveK = SessionLibrary.qualityTemplates(
            MesoPhase.PEAK,
            Experience.ADVANCED,
            RaceDistance.FIVE_K,
        )
        assertThat(fiveK.mapNotNull { it.qualityKind })
            .containsExactly(QualityKind.RACE_PACE, QualityKind.INTERVALS)

        val marathon = SessionLibrary.qualityTemplates(
            MesoPhase.PEAK,
            Experience.ADVANCED,
            RaceDistance.MARATHON,
        )
        assertThat(marathon.mapNotNull { it.qualityKind })
            .containsExactly(QualityKind.RACE_PACE, QualityKind.CONTINUOUS)
        assertThat(marathon.map { it.qualityKind }.distinct()).hasSize(2)
    }

    @Test
    fun `peak without a distance keeps a conservative generic quality`() {
        val intermediate = SessionLibrary.qualityTemplates(MesoPhase.PEAK, Experience.INTERMEDIATE).single()
        assertThat(intermediate.qualityKind).isEqualTo(QualityKind.CONTINUOUS)

        val advanced = SessionLibrary.qualityTemplates(MesoPhase.PEAK, Experience.ADVANCED).single()
        assertThat(advanced.qualityKind).isEqualTo(QualityKind.INTERVALS)
    }

    @Test
    fun `recovery has no quality and taper has only the micro dose for every level`() {
        Experience.entries.forEach { level ->
            assertThat(SessionLibrary.qualityTemplates(MesoPhase.RECOVERY, level))
                .describedAs("level $level")
                .isEmpty()

            val taper = SessionLibrary.qualityTemplates(MesoPhase.TAPER, level)
            assertThat(taper).describedAs("level $level").hasSize(1)
            assertThat(taper.single().qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
            assertThat(taper.single().durationPolicy).isEqualTo(DurationPolicy.MICRO_DOSE_25)
        }
    }

    @Test
    fun `every quality template carries a structure that fits its total`() {
        val templates = allPhases
            .flatMap { phase ->
                Experience.entries.flatMap { level ->
                    allDistances.flatMap { distance ->
                        SessionLibrary.qualityTemplates(phase, level, distance)
                    } + SessionLibrary.qualityTemplates(phase, level)
                }
            }
            .distinct()

        assertThat(templates).isNotEmpty()
        templates.forEach { template ->
            val structure = requireNotNull(template.structure) {
                "Quality template ${template.title} must carry a structure"
            }
            assertThat(structure.blocks)
                .describedAs(template.title)
                .isNotEmpty()
            assertThat(structure.plannedMinutes)
                .describedAs(template.title)
                .isLessThanOrEqualTo(structure.totalMin)
            assertThat(structure.totalMin)
                .describedAs(template.title)
                .isBetween(25, 45)
        }
    }

    @Test
    fun `strides and intro fartlek are easy sessions with structure`() {
        val beginnerBase = SessionLibrary.easyTemplates(MesoPhase.BASE, Experience.BEGINNER)
        val strides = beginnerBase.single { it.role == SessionRole.STRIDES }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.qualityKind).isNull()
        assertThat(strides.structure).isNotNull()

        val withIntro = SessionLibrary.easyTemplates(
            MesoPhase.BUILD,
            Experience.BEGINNER,
            includeIntroFartlek = true,
        )
        val intro = withIntro.single { it.role == SessionRole.INTRO_FARTLEK }
        assertThat(intro.type).isEqualTo(WorkoutType.EASY)
        assertThat(intro.qualityKind).isNull()
        val block = requireNotNull(intro.structure).blocks.single()
        assertThat(block.target).isEqualTo("RPE 7")
        assertThat(block.repetitions).isEqualTo(6)
        assertThat(block.workSec).isEqualTo(30)
        assertThat(block.recoverySec).isEqualTo(90)

        val withoutIntro = SessionLibrary.easyTemplates(MesoPhase.BUILD, Experience.BEGINNER)
        assertThat(withoutIntro.map { it.role }).doesNotContain(SessionRole.INTRO_FARTLEK)
    }

    @Test
    fun `intro fartlek is beginner only`() {
        val intermediate = SessionLibrary.easyTemplates(
            MesoPhase.BUILD,
            Experience.INTERMEDIATE,
            includeIntroFartlek = true,
        )

        assertThat(intermediate.map { it.role }).doesNotContain(SessionRole.INTRO_FARTLEK)
    }

    @Test
    fun `every quality template cites rules and evidence`() {
        val templates = allPhases
            .flatMap { phase ->
                Experience.entries.flatMap { level ->
                    allDistances.flatMap { distance ->
                        SessionLibrary.qualityTemplates(phase, level, distance)
                    } + SessionLibrary.qualityTemplates(phase, level)
                }
            }
            .distinct()

        templates.forEach { template ->
            assertThat(template.ruleIds)
                .describedAs(template.title)
                .contains("R-PER-02")
            assertThat(template.evidenceIds)
                .describedAs(template.title)
                .isNotEmpty()
        }
    }

    @Test
    fun `quality template without a structure is rejected`() {
        assertThatThrownBy {
            SessionTemplate(
                role = SessionRole.QUALITY_MAIN,
                type = WorkoutType.QUALITY,
                title = "Ritmo forte",
                qualityKind = QualityKind.CONTINUOUS,
                durationPolicy = DurationPolicy.QUALITY_SIZED,
                blurb = "Teste.",
                ruleIds = listOf("R-PER-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("structure")
    }

    @Test
    fun `legacy weekly projection keeps one main and one second with distinct kinds`() {
        val intermediate = SessionLibrary.templates(MesoPhase.BUILD, Experience.INTERMEDIATE)
        assertThat(intermediate.filter { it.role == SessionRole.QUALITY_MAIN }.map { it.qualityKind })
            .containsExactly(QualityKind.CONTINUOUS)
        assertThat(intermediate.filter { it.role == SessionRole.QUALITY_SECOND }.map { it.qualityKind })
            .containsExactly(QualityKind.INTERVALS)

        val advanced = SessionLibrary.templates(MesoPhase.BUILD, Experience.ADVANCED)
        assertThat(advanced.filter { it.role == SessionRole.QUALITY_MAIN }.map { it.qualityKind })
            .containsExactly(QualityKind.INTERVALS)
        assertThat(advanced.filter { it.role == SessionRole.QUALITY_SECOND }.map { it.qualityKind })
            .containsExactly(QualityKind.HILLS)
    }

    @Test
    fun `legacy weekly projection keeps base free of quality until the generator is wired`() {
        val base = SessionLibrary.templates(MesoPhase.BASE, Experience.INTERMEDIATE)

        assertThat(base.map { it.role }).doesNotContain(
            SessionRole.QUALITY_MAIN,
            SessionRole.QUALITY_SECOND,
        )
        assertThat(SessionLibrary.qualityTemplates(MesoPhase.BASE, Experience.INTERMEDIATE).map { it.qualityKind })
            .containsExactly(QualityKind.FARTLEK)
    }
}
