package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.json.JSONObject
import org.junit.Test
import java.time.LocalDate

class LoadEngineGoldenTest {
    @Test
    fun `matches the 28-day load engine scenario`() {
        val input = readGolden("/golden/load-engine/input.json")
        val expected = readGolden("/golden/load-engine/expected.json")
        val today = LocalDate.parse(input.getString("today"))
        val profileJson = input.getJSONObject("profile")
        val profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.parse(profileJson.getString("dateOfBirth")),
            sex = Sex.valueOf(profileJson.getString("sex")),
            restingHeartRate = profileJson.getInt("restingHeartRate"),
            maxHeartRateObserved = profileJson.getInt("maxHeartRateObserved"),
            thresholdPace = Pace.fromSecondsPerKm(profileJson.getInt("thresholdPaceSecPerKm")),
            timezone = "UTC",
        )
        val activitiesJson = input.getJSONArray("activities")
        val activities = List(activitiesJson.length()) { index ->
            val activity = activitiesJson.getJSONObject(index)
            LoadActivity(
                date = LocalDate.parse(activity.getString("date")),
                durationSec = activity.getInt("durationSec"),
                averageHeartRate = if (activity.has("averageHeartRate")) {
                    activity.getInt("averageHeartRate")
                } else {
                    null
                },
                averagePace = if (activity.has("averagePaceSecPerKm")) {
                    Pace.fromSecondsPerKm(activity.getInt("averagePaceSecPerKm"))
                } else {
                    null
                },
            )
        }

        val snapshot = TrainingLoadEngine().calculate(
            activities = activities,
            profile = profile,
            today = today,
            days = input.getInt("days"),
        )

        assertThat(snapshot.ctl).isCloseTo(expected.getDouble("ctl"), within(1e-6))
        assertThat(snapshot.atl).isCloseTo(expected.getDouble("atl"), within(1e-6))
        assertThat(snapshot.tsb).isCloseTo(expected.getDouble("tsb"), within(1e-6))
        assertThat(snapshot.acwr!!).isCloseTo(expected.getDouble("acwr"), within(1e-6))
        assertThat(snapshot.monotony!!).isCloseTo(expected.getDouble("monotony"), within(1e-6))
        assertThat(snapshot.missingDays).isEqualTo(expected.getInt("missingDays"))
        assertThat(snapshot.daily).hasSize(28)

        val days = expected.getJSONObject("days")
        days.keys().forEach { date ->
            val dayExpected = days.getJSONObject(date)
            val day = snapshot.daily.first { it.date.toString() == date }
            assertThat(day.load).isCloseTo(dayExpected.getDouble("load"), within(1e-6))
            assertThat(day.metric).isEqualTo(LoadMetric.valueOf(dayExpected.getString("metric")))
        }
    }
}
