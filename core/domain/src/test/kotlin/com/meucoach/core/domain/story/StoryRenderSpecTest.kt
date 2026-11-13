package com.meucoach.core.domain.story

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

class StoryRenderSpecTest {
    @Test
    fun `essential recipe always closes with the three essentials`() {
        val spec = resolve()

        assertThat(spec).isNotNull
        assertThat(spec!!.header).hasSize(2)
        assertThat(spec.header.map { it.id }).containsExactly("DISTANCE", "TOTAL_TIME")
        assertThat(spec.closing).hasSize(3)
        assertThat(spec.closing.map { it.id }).containsExactly("DISTANCE", "AVG_PACE", "TOTAL_TIME")
        assertThat(spec.closing).allSatisfy { metric ->
            assertThat(metric.isAvailable).isTrue
            assertThat(metric.value).isNotEqualTo("0")
        }
    }

    @Test
    fun `cards always show pace and never calories`() {
        val spec = resolve()!!

        assertThat(spec.cards).isNotEmpty
        spec.cards.forEach { card ->
            assertThat(card.metrics.first().label).isEqualTo("RITMO")
            assertThat(card.metrics.map { it.id }).doesNotContain("CALORIES_TOTAL")
            assertThat(card.metrics.size).isBetween(1, 3)
        }
    }

    @Test
    fun `one extra adds one closing metric and the per-km slot when it has data`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR))

        val spec = resolve(recipe = recipe)!!

        assertThat(spec.closing).hasSize(4)
        assertThat(spec.closing.last().id).isEqualTo("AVG_HR")
        assertThat(spec.closing.last().value).endsWith(" bpm")
        spec.cards.forEach { card ->
            assertThat(card.metrics.map { it.id }).contains("AVG_HR")
        }
    }

    @Test
    fun `two extras add two closing metrics and never more than three per card`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE))

        val spec = resolve(recipe = recipe)!!

        assertThat(spec.closing).hasSize(5)
        assertThat(spec.closing.map { it.id }).contains("AVG_HR", "CADENCE")
        spec.cards.forEach { card ->
            assertThat(card.metrics.size).isEqualTo(3)
        }
    }

    @Test
    fun `date is formatted deterministically`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.DATE))

        val spec = resolve(recipe = recipe, localDate = LocalDate.parse("2026-11-13"))!!

        assertThat(spec.closing.map { it.id }).contains("DATE")
        assertThat(spec.closing.last().value).isEqualTo("13 nov")
    }

    @Test
    fun `unavailable extras are omitted instead of becoming zero`() {
        val recipe = StoryRecipe(
            extras = listOf(StoryExtra.AVG_HR, StoryExtra.CALORIES_TOTAL),
        )

        val spec = resolve(
            recipe = recipe,
            withHeartRate = false,
            calories = null,
        )!!

        assertThat(spec.closing).hasSize(3)
        assertThat(spec.closing.map { it.id })
            .doesNotContain("AVG_HR", "CALORIES_TOTAL")
        assertThat(spec.closing.map { it.value })
            .doesNotContain("0", "0 bpm", "0 kcal", "0 spm", "+0 m")
        assertThat(spec.cards).allSatisfy { card ->
            assertThat(card.metrics.map { it.id }).doesNotContain("AVG_HR")
        }
    }

    @Test
    fun `fastest km badge only appears when the extra is selected`() {
        val essential = resolve()!!
        val performance = resolve(
            recipe = StoryRecipe(extras = listOf(StoryExtra.FASTEST_KM)),
        )!!

        assertThat(essential.cards.none { it.showFastestBadge }).isTrue
        val fastest = performance.highlights.fastestKm
        assertThat(fastest).isNotNull
        assertThat(performance.cards.single { it.split.km == fastest }.showFastestBadge).isTrue
        assertThat(performance.cards.count { it.showFastestBadge }).isEqualTo(1)
    }

    @Test
    fun `elevation is available per km only when the segment has enough samples`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.ELEVATION_GAIN))
        val poorSampling = resolve(recipe = recipe, samplesPerKm = 2)!!
        val richSampling = resolve(recipe = recipe, samplesPerKm = 12)!!

        assertThat(poorSampling.closing.map { it.id }).contains("ELEVATION_GAIN")
        assertThat(poorSampling.cards.flatMap { it.metrics.map { metric -> metric.id } })
            .doesNotContain("ELEVATION_GAIN")
        assertThat(richSampling.cards.flatMap { it.metrics.map { metric -> metric.id } })
            .contains("ELEVATION_GAIN")
    }

    @Test
    fun `a ten kilometre run resolves eight cards and ten bar segments`() {
        val spec = resolve(distanceKm = 10.0)!!

        assertThat(spec.cards).hasSize(8)
        assertThat(spec.plan.splitBar.segments).hasSize(10)
        assertThat(spec.plan.allSplits).hasSize(10)
    }

    private fun resolve(
        recipe: StoryRecipe = StoryRecipe(),
        distanceKm: Double = 5.0,
        withHeartRate: Boolean = true,
        calories: Int? = 320,
        localDate: LocalDate? = LocalDate.parse("2026-11-13"),
        samplesPerKm: Int = 4,
    ): StoryRenderSpec? = StoryRenderSpecResolver.resolve(
        activity = activity(
            distanceKm = distanceKm,
            withHeartRate = withHeartRate,
            calories = calories,
            samplesPerKm = samplesPerKm,
        ),
        localDate = localDate,
        recipe = recipe,
    )

    private fun activity(
        distanceKm: Double,
        withHeartRate: Boolean,
        calories: Int?,
        samplesPerKm: Int,
    ): ImportedActivity {
        val count = ceil(distanceKm * samplesPerKm).toInt().coerceAtLeast(2) + 1
        val durationSec = (distanceKm * 360).roundToInt()
        val meters = distanceKm * 1_000.0
        val samples = (0 until count).map { index ->
            val fraction = index.toDouble() / (count - 1)
            ActivitySample(
                offsetSec = (durationSec * fraction).roundToInt(),
                latitude = -5.790 - (meters * fraction) / METERS_PER_DEGREE_LAT,
                longitude = -35.210,
                altitudeM = 100.0 + index * 1.5,
                heartRate = if (withHeartRate) 140 + (index % 20) else null,
                cadenceSpm = if (withHeartRate) 168 + (index % 6) else null,
            )
        }
        return ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-11-13T09:00:00Z"),
            durationSec = durationSec,
            distanceM = meters,
            calories = calories,
            elevationGainM = 84.0,
            averageCadenceSpm = 172,
            samples = samples,
        )
    }

    private companion object {
        const val METERS_PER_DEGREE_LAT = 111_195.0
    }
}
