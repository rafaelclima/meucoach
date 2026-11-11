package com.meucoach.core.domain.golden

import com.meucoach.core.domain.readiness.HrvMetrics
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessSignals
import com.meucoach.core.domain.readiness.RestingHeartRateMetrics
import com.meucoach.core.domain.readiness.SleepMetrics
import com.meucoach.core.domain.readiness.SubjectiveCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test

class ReadinessGoldenTest {
    @Test
    fun `matches all readiness scenarios`() {
        listOf(
            "readiness-green",
            "readiness-no-data",
            "readiness-illness",
            "readiness-short-sleep",
        ).forEach { scenario -> assertScenario(scenario) }
    }

    private fun assertScenario(name: String) {
        val input = readGolden("/golden/$name/input.json")
        val expected = readGolden("/golden/$name/expected.json")

        val result = ReadinessCalculator.calculate(
            checkIn = input.optJSONObject("checkIn")?.toCheckIn(),
            sleep = input.optJSONObject("sleep")?.let { SleepMetrics(it.getInt("durationMin")) },
            hrv = input.optJSONObject("hrv")?.toHrv(),
            restingHeartRate = input.optJSONObject("restingHeartRate")?.toRestingHeartRate(),
            signals = input.optJSONObject("signals")?.toSignals() ?: ReadinessSignals(),
        )

        assertThat(result.score).`as`("$name score").isEqualTo(expected.getInt("score"))
        assertThat(result.level).`as`("$name level")
            .isEqualTo(ReadinessLevel.valueOf(expected.getString("level")))
        assertThat(result.confidence).`as`("$name confidence")
            .isEqualTo(DataConfidence.valueOf(expected.getString("confidence")))

        val expectedComponents = expected.getJSONObject("components")
        val componentMap = expectedComponents.keys().asSequence().associateWith { key ->
            expectedComponents.getInt(key)
        }
        assertThat(result.components.mapKeys { it.key.name })
            .`as`("$name components")
            .isEqualTo(componentMap)

        assertThat(result.missing.map { it.name }.toSortedSet())
            .`as`("$name missing")
            .isEqualTo(expected.getJSONArray("missing").toStringList().toSortedSet())
        assertThat(result.flags.map { it.name }.toSortedSet())
            .`as`("$name flags")
            .isEqualTo(expected.getJSONArray("flags").toStringList().toSortedSet())
    }

    private fun JSONObject.toCheckIn() = SubjectiveCheckIn(
        sleepQuality = getInt("sleepQuality"),
        fatigue = getInt("fatigue"),
        soreness = getInt("soreness"),
        stress = getInt("stress"),
    )

    private fun JSONObject.toHrv() = HrvMetrics(
        rmssd7dMs = getDouble("rmssd7dMs"),
        baseline60dMs = getDouble("baseline60dMs"),
        meanLog7d = getDouble("meanLog7d"),
        meanLog60d = getDouble("meanLog60d"),
        sdLnMs = getDouble("sdLnMs"),
        validNights = getInt("validNights"),
    )

    private fun JSONObject.toRestingHeartRate() = RestingHeartRateMetrics(
        average7dBpm = getDouble("average7dBpm"),
        baseline30dBpm = getDouble("baseline30dBpm"),
    )

    private fun JSONObject.toSignals() = ReadinessSignals(
        illnessOrInjury = optBoolean("illnessOrInjury", false),
        restingHeartRateElevatedTwoDays = optBoolean("restingHeartRateElevatedTwoDays", false),
    )
}
