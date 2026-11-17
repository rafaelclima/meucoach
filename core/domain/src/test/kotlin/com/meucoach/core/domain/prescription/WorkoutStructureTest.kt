package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

/**
 * C4-14 — executable workout structure (ADR 0047 §2.1, PRD §5.3).
 *
 * The structure is the contract that prevents promising more work than the
 * planned session duration. It is pure: no pace, no clock, no persistence.
 */
class WorkoutStructureTest {
    @Test
    fun `interval structure fits the session duration`() {
        val structure = WorkoutStructure(
            warmupMin = 10,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    repetitions = 5,
                    workSec = 180,
                    recoverySec = 120,
                    target = "I",
                    note = "3 min fortes, 2 min leves",
                ),
            ),
            cooldownMin = 5,
            totalMin = 40,
        )

        assertThat(structure.plannedSeconds).isEqualTo(2_400L)
        assertThat(structure.plannedMinutes).isEqualTo(40)
        assertThat(structure.version).isEqualTo(1)
    }

    @Test
    fun `structure rejects more work than the session duration`() {
        assertThatThrownBy {
            WorkoutStructure(
                warmupMin = 10,
                blocks = listOf(
                    WorkoutBlock(kind = BlockKind.WORK, repetitions = 6, workSec = 180, recoverySec = 120),
                ),
                cooldownMin = 5,
                totalMin = 40,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("fit")
    }

    @Test
    fun `planned time counts work and recovery after every repetition`() {
        val structure = WorkoutStructure(
            warmupMin = 5,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, repetitions = 3, workSec = 60, recoverySec = 30),
            ),
            cooldownMin = 2,
            totalMin = 12,
        )

        // 5 + 2 min of warmup/cooldown plus 3 * (60 + 30) seconds of work.
        assertThat(structure.plannedSeconds).isEqualTo(690L)
    }

    @Test
    fun `continuous and recovery blocks count once`() {
        val structure = WorkoutStructure(
            warmupMin = 0,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, workSec = 600, target = "T"),
                WorkoutBlock(kind = BlockKind.RECOVERY, workSec = 120),
            ),
            cooldownMin = 0,
            totalMin = 12,
        )

        assertThat(structure.plannedSeconds).isEqualTo(720L)
    }

    @Test
    fun `planned minutes round up partial minutes`() {
        val structure = WorkoutStructure(
            warmupMin = 0,
            blocks = listOf(WorkoutBlock(kind = BlockKind.STRIDES, repetitions = 6, workSec = 20, recoverySec = 65)),
            cooldownMin = 0,
            totalMin = 9,
        )

        assertThat(structure.plannedSeconds).isEqualTo(510L)
        assertThat(structure.plannedMinutes).isEqualTo(9)
    }

    @Test
    fun `structure requires blocks and a positive total`() {
        assertThatThrownBy {
            WorkoutStructure(warmupMin = 0, blocks = emptyList(), cooldownMin = 0, totalMin = 20)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutStructure(
                warmupMin = 0,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 60)),
                cooldownMin = 0,
                totalMin = 0,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `structure rejects negative warmup and cooldown`() {
        assertThatThrownBy {
            WorkoutStructure(
                warmupMin = -1,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 60)),
                cooldownMin = 0,
                totalMin = 10,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutStructure(
                warmupMin = 0,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 60)),
                cooldownMin = -1,
                totalMin = 10,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `structure version must be positive`() {
        assertThatThrownBy {
            WorkoutStructure(
                version = 0,
                warmupMin = 0,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 60)),
                cooldownMin = 0,
                totalMin = 10,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `work block requires positive work and valid repetitions`() {
        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, repetitions = 0, workSec = 60)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, workSec = null)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, workSec = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `recovery block cannot nest repetitions or another recovery`() {
        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.RECOVERY, repetitions = 2, workSec = 60)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.RECOVERY, workSec = 60, recoverySec = 30)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `block rejects a non positive recovery and blank text`() {
        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, workSec = 60, recoverySec = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, workSec = 60, target = " ")
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WorkoutBlock(kind = BlockKind.WORK, workSec = 60, note = "")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `legacy planned session keeps a null structure`() {
        val session = PlannedSession(
            date = LocalDate.of(2026, 11, 16),
            type = WorkoutType.EASY,
            title = "Fácil e conversável",
            durationMin = 40,
            rationale = "Base aeróbica",
            ruleIds = listOf("R-PER-02"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

        assertThat(session.structure).isNull()
    }

    @Test
    fun `fitted structure shrinks repetitions to the real duration`() {
        val structure = WorkoutStructure(
            warmupMin = 10,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, repetitions = 5, workSec = 180, recoverySec = 120),
            ),
            cooldownMin = 5,
            totalMin = 40,
        )

        val fitted = structure.fittedTo(25)

        assertThat(fitted.totalMin).isEqualTo(25)
        assertThat(fitted.blocks.single().repetitions).isEqualTo(2)
        assertThat(fitted.plannedSeconds).isEqualTo(1_500L)
        assertThat(fitted.plannedSeconds).isLessThanOrEqualTo(25L * 60L)
    }

    @Test
    fun `fitted structure shortens a continuous block`() {
        val structure = WorkoutStructure(
            warmupMin = 10,
            blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 1_200, target = "T")),
            cooldownMin = 5,
            totalMin = 35,
        )

        val fitted = structure.fittedTo(25)

        assertThat(fitted.blocks.single().workSec).isEqualTo(600)
        assertThat(fitted.plannedMinutes).isEqualTo(25)
    }

    @Test
    fun `fitted structure grows the session total without inventing work`() {
        val structure = WorkoutStructure(
            warmupMin = 10,
            blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 1_200, target = "T")),
            cooldownMin = 5,
            totalMin = 35,
        )

        val fitted = structure.fittedTo(45)

        assertThat(fitted.totalMin).isEqualTo(45)
        assertThat(fitted.blocks).isEqualTo(structure.blocks)
        assertThat(fitted.plannedSeconds).isEqualTo(structure.plannedSeconds)
    }

    @Test
    fun `fitted structure keeps a single interval repetition when that is all that fits`() {
        val structure = WorkoutStructure(
            warmupMin = 10,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, repetitions = 5, workSec = 180, recoverySec = 120),
            ),
            cooldownMin = 5,
            totalMin = 40,
        )

        val fitted = structure.fittedTo(20)

        assertThat(fitted.blocks.single().repetitions).isEqualTo(1)
        assertThat(fitted.plannedSeconds).isEqualTo(1_200L)
        assertThat(fitted.plannedMinutes).isEqualTo(20)
    }

    @Test
    fun `fitted structure shrinks a single repetition recovery when needed`() {
        val structure = WorkoutStructure(
            warmupMin = 0,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, repetitions = 5, workSec = 300, recoverySec = 120),
            ),
            cooldownMin = 0,
            totalMin = 35,
        )

        val fitted = structure.fittedTo(2)

        val block = fitted.blocks.single()
        assertThat(block.repetitions).isEqualTo(1)
        assertThat(block.workSec).isEqualTo(1)
        assertThat(block.recoverySec).isEqualTo(119)
        assertThat(fitted.plannedSeconds).isEqualTo(120L)
    }

    @Test
    fun `fitted structure distributes the budget across multiple blocks`() {
        val structure = WorkoutStructure(
            warmupMin = 0,
            blocks = listOf(
                WorkoutBlock(kind = BlockKind.WORK, workSec = 600),
                WorkoutBlock(kind = BlockKind.RECOVERY, workSec = 300),
            ),
            cooldownMin = 0,
            totalMin = 15,
        )

        val fitted = structure.fittedTo(10)

        assertThat(fitted.totalMin).isEqualTo(10)
        assertThat(fitted.blocks.map { it.workSec }).containsExactly(400, 200)
        assertThat(fitted.plannedSeconds).isEqualTo(600L)
    }

    @Test
    fun `fitted structure rejects a duration that cannot hold warmup and cooldown`() {
        val structure = WorkoutStructure(
            warmupMin = 20,
            blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 600)),
            cooldownMin = 10,
            totalMin = 40,
        )

        assertThatThrownBy { structure.fittedTo(25) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `structure can describe an easy session with strides`() {
        val session = PlannedSession(
            date = LocalDate.of(2026, 11, 16),
            type = WorkoutType.EASY,
            title = "Fácil com strides",
            durationMin = 40,
            rationale = "Fácil com 6 surtos curtos",
            ruleIds = listOf("R-PER-02"),
            evidenceIds = listOf("EV-SEILER-2010"),
            structure = WorkoutStructure(
                warmupMin = 0,
                blocks = listOf(
                    WorkoutBlock(kind = BlockKind.WORK, workSec = 1_800, target = "E"),
                    WorkoutBlock(kind = BlockKind.STRIDES, repetitions = 6, workSec = 20, recoverySec = 60),
                ),
                cooldownMin = 0,
                totalMin = 40,
            ),
        )

        val structure = requireNotNull(session.structure)
        assertThat(structure.plannedMinutes).isEqualTo(38)
    }
}
