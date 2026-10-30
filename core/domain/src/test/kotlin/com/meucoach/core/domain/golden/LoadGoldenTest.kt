package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.TrainingLoadCalculator
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class LoadGoldenTest {
    @Test
    fun `matches the baseline load scenario`() {
        val input = readGolden("/golden/load-baseline/input.json")
        val expected = readGolden("/golden/load-baseline/expected.json")

        val trimp = input.getJSONObject("trimp")
        val actualTrimp = TrainingLoadCalculator.trimp(
            durationSec = trimp.getInt("durationSec"),
            averageHeartRateBpm = trimp.getInt("averageHeartRateBpm"),
            restingHeartRateBpm = trimp.getInt("restingHeartRateBpm"),
            maxHeartRateBpm = trimp.getInt("maxHeartRateBpm"),
            sex = Sex.valueOf(trimp.getString("sex")),
        )
        assertClose(expected.getDouble("trimp"), actualTrimp, "trimp")

        val rtss = input.getJSONObject("rTss")
        assertClose(
            expected.getDouble("rTss"),
            TrainingLoadCalculator.rTss(
                durationSec = rtss.getInt("durationSec"),
                normalizedPaceSecPerKm = rtss.getInt("normalizedPaceSecPerKm"),
                thresholdPaceSecPerKm = rtss.getInt("thresholdPaceSecPerKm"),
            ),
            "rTss",
        )

        val srpe = input.getJSONObject("srpe")
        assertClose(
            expected.getDouble("srpe"),
            TrainingLoadCalculator.srpe(
                rpe = srpe.getInt("rpe"),
                durationMin = srpe.getInt("durationMin"),
            ),
            "srpe",
        )

        val dailyLoads = List(input.getJSONArray("dailyLoads").length()) { index ->
            input.getJSONArray("dailyLoads").getDouble(index)
        }
        assertCloseList(expected.getJSONArray("ctl"), TrainingLoadCalculator.ctl(dailyLoads), "ctl")
        assertCloseList(expected.getJSONArray("atl"), TrainingLoadCalculator.atl(dailyLoads), "atl")

        val ctl = TrainingLoadCalculator.ctl(dailyLoads).last()
        val atl = TrainingLoadCalculator.atl(dailyLoads).last()
        assertClose(expected.getDouble("tsb"), TrainingLoadCalculator.tsb(ctl, atl), "tsb")

        val acwr = input.getJSONObject("acwr")
        assertClose(
            expected.getDouble("acwr"),
            checkNotNull(
                TrainingLoadCalculator.acwr(
                    acuteAverage7d = acwr.getDouble("acuteAverage7d"),
                    chronicAverage28d = acwr.getDouble("chronicAverage28d"),
                ),
            ),
            "acwr",
        )
        assertClose(
            expected.getDouble("monotony"),
            checkNotNull(TrainingLoadCalculator.monotony(dailyLoads)),
            "monotony",
        )
    }

    private fun assertCloseList(expected: org.json.JSONArray, actual: List<Double>, path: String) {
        assertThat(actual).hasSameSizeAs(expected.toList())
        expected.toList().forEachIndexed { index, value ->
            assertClose(value as Number, actual[index], "$path[$index]")
        }
    }

    private fun assertClose(expected: Number, actual: Double, path: String) {
        assertThat(actual).`as`(path).isCloseTo(expected.toDouble(), within(1e-6))
    }
}
