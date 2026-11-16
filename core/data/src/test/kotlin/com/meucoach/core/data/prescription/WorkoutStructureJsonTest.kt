package com.meucoach.core.data.prescription

import com.meucoach.core.domain.prescription.BlockKind
import com.meucoach.core.domain.prescription.WorkoutBlock
import com.meucoach.core.domain.prescription.WorkoutStructure
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C4-14 — JSON codec for the executable structure (ADR 0047 §2.1, ADR 0048).
 *
 * `structureJson` arrives with schema v18; old rows and old exports must decode
 * as `null`, never as an empty structure. Unknown/future payloads are also
 * ignored so a newer writer cannot make an older app prescribe the wrong work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutStructureJsonTest {
    private val intervalStructure = WorkoutStructure(
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
            WorkoutBlock(kind = BlockKind.RECOVERY, workSec = 120, target = "E"),
            WorkoutBlock(kind = BlockKind.STRIDES, repetitions = 4, workSec = 20, recoverySec = 60, target = "RPE"),
        ),
        cooldownMin = 5,
        totalMin = 50,
    )

    @Test
    fun `round-trips the full structure`() {
        val encoded = WorkoutStructureJson.toJson(intervalStructure)
        val decoded = WorkoutStructureJson.fromJson(encoded)

        assertThat(decoded).isEqualTo(intervalStructure)
    }

    @Test
    fun `encoded payload is versioned`() {
        val encoded = WorkoutStructureJson.toJson(intervalStructure)

        assertThat(encoded).contains("\"version\":1")
    }

    @Test
    fun `round-trips blocks without optional fields`() {
        val structure = WorkoutStructure(
            warmupMin = 0,
            blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 600)),
            cooldownMin = 0,
            totalMin = 10,
        )

        val decoded = WorkoutStructureJson.fromJson(WorkoutStructureJson.toJson(structure))

        assertThat(decoded).isEqualTo(structure)
    }

    @Test
    fun `legacy absent or empty structure decodes as null`() {
        assertThat(WorkoutStructureJson.fromJson(null)).isNull()
        assertThat(WorkoutStructureJson.fromJson("")).isNull()
        assertThat(WorkoutStructureJson.fromJson("   ")).isNull()
        assertThat(WorkoutStructureJson.fromJson("null")).isNull()
    }

    @Test
    fun `malformed json decodes as null`() {
        assertThat(WorkoutStructureJson.fromJson("not-json")).isNull()
        assertThat(WorkoutStructureJson.fromJson("{}")).isNull()
    }

    @Test
    fun `missing required fields decode as null`() {
        assertThat(
            WorkoutStructureJson.fromJson(
                """{"version":1,"warmupMin":0,"blocks":[{"kind":"WORK","workSec":600}],"cooldownMin":0}""",
            ),
        ).isNull()

        assertThat(
            WorkoutStructureJson.fromJson(
                """{"version":1,"warmupMin":0,"blocks":[{"kind":"WORK"}],"cooldownMin":0,"totalMin":10}""",
            ),
        ).isNull()
    }

    @Test
    fun `hand-written payload without optional keys decodes`() {
        val decoded = WorkoutStructureJson.fromJson(
            """{"version":1,"warmupMin":0,"blocks":[{"kind":"WORK","workSec":600}],"cooldownMin":0,"totalMin":10}""",
        )

        assertThat(decoded).isEqualTo(
            WorkoutStructure(
                warmupMin = 0,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 600)),
                cooldownMin = 0,
                totalMin = 10,
            ),
        )
    }

    @Test
    fun `unsupported or missing version decodes as null`() {
        assertThat(
            WorkoutStructureJson.fromJson(
                """
                {"version":2,"warmupMin":0,
                 "blocks":[{"kind":"WORK","workSec":600}],
                 "cooldownMin":0,"totalMin":10}
                """.trimIndent(),
            ),
        ).isNull()

        assertThat(
            WorkoutStructureJson.fromJson(
                """
                {"warmupMin":0,
                 "blocks":[{"kind":"WORK","workSec":600}],
                 "cooldownMin":0,"totalMin":10}
                """.trimIndent(),
            ),
        ).isNull()
    }

    @Test
    fun `unknown block kind decodes as null`() {
        assertThat(
            WorkoutStructureJson.fromJson(
                """
                {"version":1,"warmupMin":0,
                 "blocks":[{"kind":"SPRINT","workSec":60}],
                 "cooldownMin":0,"totalMin":10}
                """.trimIndent(),
            ),
        ).isNull()
    }

    @Test
    fun `structure that promises more than the session decodes as null`() {
        assertThat(
            WorkoutStructureJson.fromJson(
                """
                {"version":1,"warmupMin":0,
                 "blocks":[{"kind":"WORK","repetitions":5,"workSec":180}],
                 "cooldownMin":0,"totalMin":10}
                """.trimIndent(),
            ),
        ).isNull()
    }

    @Test
    fun `unknown keys are ignored`() {
        val decoded = WorkoutStructureJson.fromJson(
            """
            {"version":1,"warmupMin":0,
             "blocks":[{"kind":"WORK","workSec":600,"future":true}],
             "cooldownMin":0,"totalMin":10,"extra":"ignored"}
            """.trimIndent(),
        )

        assertThat(decoded).isEqualTo(
            WorkoutStructure(
                warmupMin = 0,
                blocks = listOf(WorkoutBlock(kind = BlockKind.WORK, workSec = 600)),
                cooldownMin = 0,
                totalMin = 10,
            ),
        )
    }
}
