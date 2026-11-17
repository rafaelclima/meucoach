package com.meucoach.core.data.prescription

import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C4-57 (FR-410): the targets JSON carries the precedence. Rows persisted
 * before the field decode from content instead of failing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrescriptionJsonTargetsTest {
    @Test
    fun `round-trips the primary target`() {
        val targets = PrescriptionTargets(
            pace = PaceTarget(Pace.fromSecondsPerKm(350), Pace.fromSecondsPerKm(390)),
            heartRate = HeartRateTarget(140, 155),
            zone = "Z2",
            distanceKm = 6.5,
            primary = TargetKind.HEART_RATE,
        )

        val loaded = PrescriptionJson.targetsFromJson(PrescriptionJson.targetsToJson(targets))

        assertThat(loaded).isEqualTo(targets)
        assertThat(loaded.primary).isEqualTo(TargetKind.HEART_RATE)
    }

    @Test
    fun `legacy rows without primary derive it from content`() {
        val easyLegacy = """
            {"paceFastSecPerKm":350,"paceSlowSecPerKm":390,
            "heartRateMinBpm":140,"heartRateMaxBpm":155,"zone":"Z2"}
        """.trimIndent().replace("\n", "")
        val paceLegacy = """{"paceFastSecPerKm":350,"paceSlowSecPerKm":390,"zone":"Z2"}"""
        val rpeLegacy = """{"zone":"Z2","rpe":4}"""

        assertThat(PrescriptionJson.targetsFromJson(easyLegacy).primary).isEqualTo(TargetKind.HEART_RATE)
        assertThat(PrescriptionJson.targetsFromJson(paceLegacy).primary).isEqualTo(TargetKind.PACE)
        assertThat(PrescriptionJson.targetsFromJson(rpeLegacy).primary).isEqualTo(TargetKind.RPE)
    }

    @Test
    fun `unknown primary falls back to content`() {
        val json = """{"paceFastSecPerKm":350,"paceSlowSecPerKm":390,"primary":"FLY"}"""

        assertThat(PrescriptionJson.targetsFromJson(json).primary).isEqualTo(TargetKind.PACE)
    }
}
