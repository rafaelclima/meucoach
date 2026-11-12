package com.meucoach.core.domain.race

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
import com.meucoach.core.model.VdotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class RaceTimePredictionTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val calibratedAt = LocalDate.of(2026, 11, 8)

    @Test
    fun `predicts the 5k time that produced the calibrated vdot`() {
        val prediction = available(
            predict(vdot = 29.6477, confidence = DataConfidence.HIGH),
        )

        assertThat(prediction.distance).isEqualTo(RaceDistance.FIVE_K)
        assertThat(prediction.predictedSec).isCloseTo(1_859, org.assertj.core.data.Offset.offset(2))
        assertThat(prediction.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(prediction.ruleId).isEqualTo("R-RACE-03")
        assertThat(prediction.evidenceIds).containsExactly("EV-DANIELS-2014", "EV-RIEGEL-1981")
        assertThat(prediction.usedRiegel).isFalse()
    }

    @Test
    fun `predicts longer distances by inverting the vdot equation`() {
        val tenK = available(predict(vdot = 29.6477, confidence = DataConfidence.HIGH, distance = RaceDistance.TEN_K))
        val half =
            available(predict(vdot = 29.6477, confidence = DataConfidence.HIGH, distance = RaceDistance.HALF_MARATHON))
        val marathon =
            available(predict(vdot = 29.6477, confidence = DataConfidence.HIGH, distance = RaceDistance.MARATHON))

        assertThat(tenK.predictedSec).isCloseTo(3_867, org.assertj.core.data.Offset.offset(3))
        assertThat(half.predictedSec).isCloseTo(8_560, org.assertj.core.data.Offset.offset(5))
        assertThat(marathon.predictedSec).isCloseTo(17_552, org.assertj.core.data.Offset.offset(8))
    }

    @Test
    fun `uses a three percent range for a performance calibration`() {
        val prediction = available(predict(vdot = 29.6477, confidence = DataConfidence.HIGH))

        assertThat(prediction.predictedSec).isEqualTo(1_860)
        assertThat(prediction.rangeSec).isEqualTo(1_804..1_916)
    }

    @Test
    fun `uses a five percent range for a training estimate`() {
        val prediction = available(predict(vdot = 29.6477, confidence = DataConfidence.MEDIUM))

        assertThat(prediction.predictedSec).isEqualTo(1_860)
        assertThat(prediction.rangeSec).isEqualTo(1_767..1_953)
    }

    @Test
    fun `extends the range with riegel when a tier A reference exists`() {
        val reference = RaceReference(distanceM = 5_000.0, durationSec = 2_000)
        val prediction = available(
            predict(
                vdot = 29.6477,
                confidence = DataConfidence.HIGH,
                reference = reference,
                distance = RaceDistance.TEN_K,
            ),
        )

        assertThat(prediction.usedRiegel).isTrue()
        // Riegel: 2000 · (10/5)^1.06 = 4170 s, above the VDOT range, so it widens the high end.
        assertThat(prediction.rangeSec.first).isEqualTo(3_751)
        assertThat(prediction.rangeSec.last).isEqualTo(4_170)
    }

    @Test
    fun `does not use riegel for a marathon`() {
        val prediction = available(
            predict(
                vdot = 29.6477,
                confidence = DataConfidence.HIGH,
                reference = RaceReference(distanceM = 5_000.0, durationSec = 1_860),
                distance = RaceDistance.MARATHON,
            ),
        )

        assertThat(prediction.usedRiegel).isFalse()
    }

    @Test
    fun `does not use riegel from a reference outside the supported window`() {
        val prediction = available(
            predict(
                vdot = 29.6477,
                confidence = DataConfidence.HIGH,
                reference = RaceReference(distanceM = 3_000.0, durationSec = 1_020),
                distance = RaceDistance.TEN_K,
            ),
        )

        assertThat(prediction.usedRiegel).isFalse()
    }

    @Test
    fun `returns unavailable without a vdot`() {
        assertThat(predict(vdot = null, confidence = DataConfidence.HIGH))
            .isEqualTo(RacePredictionResult.Unavailable(RacePredictionUnavailable.NO_VDOT))
    }

    @Test
    fun `returns unavailable for low confidence`() {
        assertThat(predict(vdot = 30.0, confidence = DataConfidence.LOW))
            .isEqualTo(RacePredictionResult.Unavailable(RacePredictionUnavailable.LOW_CONFIDENCE))
    }

    @Test
    fun `returns unavailable when the calibration is stale or undated`() {
        assertThat(
            predict(
                vdot = 30.0,
                confidence = DataConfidence.HIGH,
                calibratedAt = today.minusDays(RaceTimePredictor.RECENT_DAYS + 1),
            ),
        ).isEqualTo(RacePredictionResult.Unavailable(RacePredictionUnavailable.STALE_CALIBRATION))

        assertThat(
            predict(vdot = 30.0, confidence = DataConfidence.HIGH, calibratedAt = null),
        ).isEqualTo(RacePredictionResult.Unavailable(RacePredictionUnavailable.STALE_CALIBRATION))
    }

    @Test
    fun `maps profile provenance to confidence`() {
        val performance = available(
            RaceTimePredictor.predict(
                profile = profile(vdotSource = VdotSource.PERFORMANCE),
                distance = RaceDistance.FIVE_K,
                today = today,
            ),
        )
        val training = available(
            RaceTimePredictor.predict(
                profile = profile(vdotSource = VdotSource.TRAINING_ESTIMATE),
                distance = RaceDistance.FIVE_K,
                today = today,
            ),
        )
        val legacy = RaceTimePredictor.predict(
            profile = profile(vdotSource = null).copy(vdot = 30.0),
            distance = RaceDistance.FIVE_K,
            today = today,
        )

        assertThat(performance.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(training.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(legacy).isEqualTo(RacePredictionResult.Unavailable(RacePredictionUnavailable.LOW_CONFIDENCE))
    }

    private fun predict(
        vdot: Double?,
        confidence: DataConfidence,
        calibratedAt: LocalDate? = this.calibratedAt,
        reference: RaceReference? = null,
        distance: RaceDistance = RaceDistance.FIVE_K,
    ) = RaceTimePredictor.predict(
        vdot = vdot,
        confidence = confidence,
        calibratedAt = calibratedAt,
        reference = reference,
        distance = distance,
        today = today,
    )

    private fun available(result: RacePredictionResult): RacePrediction =
        (result as RacePredictionResult.Available).prediction

    private fun profile(vdotSource: VdotSource?) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        vdot = 29.6477,
        vdotSource = vdotSource,
        vdotCalibratedAt = calibratedAt,
        vdotReferenceDistanceM = if (vdotSource == VdotSource.PERFORMANCE) 5_000.0 else null,
        vdotReferenceDurationSec = if (vdotSource == VdotSource.PERFORMANCE) 1_859 else null,
    )
}
