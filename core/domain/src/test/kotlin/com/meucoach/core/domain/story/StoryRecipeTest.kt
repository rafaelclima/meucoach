package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class StoryRecipeTest {
    @Test
    fun `the three essentials are fixed and cannot be removed`() {
        val recipe = StoryRecipe()

        assertThat(recipe.essentials).containsExactly(
            StoryEssential.DISTANCE,
            StoryEssential.AVG_PACE,
            StoryEssential.TOTAL_TIME,
        )
        assertThat(StoryRecipe.ESSENTIALS).isEqualTo(StoryEssential.entries)
    }

    @Test
    fun `default recipe is essential auto dark and standard`() {
        val recipe = StoryRecipe()

        assertThat(recipe.preset).isEqualTo(StoryPreset.ESSENTIAL)
        assertThat(recipe.extras).isEmpty()
        assertThat(recipe.duration).isEqualTo(StoryDuration.AUTO)
        assertThat(recipe.mapStyle).isEqualTo(StoryMapStyle.MEUCOACH_DARK)
        assertThat(recipe.quality).isEqualTo(StoryQuality.STANDARD_1080P30)
    }

    @Test
    fun `rejects a third extra and duplicates`() {
        assertThatThrownBy {
            StoryRecipe(
                extras = listOf(
                    StoryExtra.AVG_HR,
                    StoryExtra.CADENCE,
                    StoryExtra.DATE,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.AVG_HR))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `accepts zero one or two distinct extras`() {
        assertThat(StoryRecipe(extras = emptyList()).extras).isEmpty()
        assertThat(StoryRecipe(extras = listOf(StoryExtra.DATE)).extras).hasSize(1)
        assertThat(
            StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)).extras,
        ).hasSize(2)
    }

    @Test
    fun `presets request the documented extras`() {
        assertThat(StoryPreset.ESSENTIAL.requestedExtras()).isEmpty()
        assertThat(StoryPreset.PERFORMANCE.requestedExtras())
            .containsExactly(StoryExtra.FASTEST_KM, StoryExtra.ELEVATION_GAIN)
        assertThat(StoryPreset.PHYSIOLOGICAL.requestedExtras())
            .containsExactly(StoryExtra.AVG_HR, StoryExtra.CADENCE)
        assertThat(StoryPreset.COMPLETE.requestedExtras())
            .containsExactly(StoryExtra.AVG_HR, StoryExtra.CALORIES_TOTAL)
        assertThat(StoryPreset.CUSTOM.requestedExtras()).isEmpty()
    }

    @Test
    fun `a preset never turns on an unavailable extra`() {
        val onlyElevation = StoryMetricValues(elevationGainM = 84.0)

        val physiological = StoryRecipe.fromPreset(StoryPreset.PHYSIOLOGICAL, onlyElevation)
        val complete = StoryRecipe.fromPreset(StoryPreset.COMPLETE, onlyElevation)

        assertThat(physiological.extras).isEmpty()
        assertThat(complete.extras).isEmpty()
        assertThat(physiological.preset).isEqualTo(StoryPreset.PHYSIOLOGICAL)
    }

    @Test
    fun `a preset keeps the available subset and leaves the slot free`() {
        val values = allValues().copy(fastestKm = null, fastestPace = null)

        val performance = StoryRecipe.fromPreset(StoryPreset.PERFORMANCE, values)

        assertThat(performance.extras).containsExactly(StoryExtra.ELEVATION_GAIN)
        assertThat(performance.extras).hasSizeLessThan(StoryRecipe.MAX_EXTRAS)
    }

    @Test
    fun `resolved extras drop metrics whose data disappeared`() {
        val recipe = StoryRecipe(
            preset = StoryPreset.CUSTOM,
            extras = listOf(StoryExtra.AVG_HR, StoryExtra.CALORIES_TOTAL),
        )

        assertThat(recipe.resolvedExtras(allValues())).hasSize(2)
        assertThat(recipe.resolvedExtras(StoryMetricValues())).isEmpty()
    }

    @Test
    fun `auto duration follows the plan clamp`() {
        assertThat(StoryDuration.AUTO.targetSeconds(0.0)).isEqualTo(15)
        assertThat(StoryDuration.AUTO.targetSeconds(5.0)).isEqualTo(18)
        assertThat(StoryDuration.AUTO.targetSeconds(10.0)).isEqualTo(28)
        assertThat(StoryDuration.AUTO.targetSeconds(21.0)).isEqualTo(32)
        assertThat(StoryDuration.AUTO.targetSeconds(60.0)).isEqualTo(32)
        assertThat(StoryDuration.AUTO.targetSeconds(null)).isEqualTo(15)
    }

    @Test
    fun `explicit durations are honored`() {
        assertThat(StoryDuration.SEC_15.targetSeconds(10.0)).isEqualTo(15)
        assertThat(StoryDuration.SEC_30.targetSeconds(10.0)).isEqualTo(30)
        assertThat(StoryDuration.SEC_45.targetSeconds(10.0)).isEqualTo(45)
        assertThat(StoryDuration.SEC_60.targetSeconds(10.0)).isEqualTo(60)
    }

    @Test
    fun `recipe duration resolves against the run distance`() {
        assertThat(StoryRecipe(duration = StoryDuration.AUTO).targetDurationSec(10.0)).isEqualTo(28)
        assertThat(StoryRecipe(duration = StoryDuration.SEC_45).targetDurationSec(10.0)).isEqualTo(45)
    }

    @Test
    fun `legacy map style ordinals stay stable for old jobs`() {
        assertThat(StoryMapStyle.CLASSIC.ordinal).isEqualTo(0)
        assertThat(StoryMapStyle.SATELLITE.ordinal).isEqualTo(1)
        assertThat(StoryMapStyle.TERRAIN.ordinal).isEqualTo(2)
        assertThat(StoryMapStyle.MEUCOACH_DARK.ordinal).isEqualTo(3)
        assertThat(StoryMapStyle.MEUCOACH_LIGHT.ordinal).isEqualTo(4)
    }

    @Test
    fun `quality exposes the 1080p30 master and the future 60 fps profile`() {
        assertThat(StoryQuality.STANDARD_1080P30.width).isEqualTo(1080)
        assertThat(StoryQuality.STANDARD_1080P30.height).isEqualTo(1920)
        assertThat(StoryQuality.STANDARD_1080P30.fps).isEqualTo(30)
        assertThat(StoryQuality.STANDARD_1080P30.bitrate).isEqualTo(16_000_000)
        assertThat(StoryQuality.SMOOTH_1080P60.fps).isEqualTo(60)
    }

    @Test
    fun `changing extras moves the recipe to custom`() {
        val recipe = StoryRecipe().withExtras(listOf(StoryExtra.DATE))

        assertThat(recipe.preset).isEqualTo(StoryPreset.CUSTOM)
        assertThat(recipe.extras).containsExactly(StoryExtra.DATE)
    }

    @Test
    fun `toggling an extra adds it in selection order and marks the recipe custom`() {
        val first = StoryRecipe().toggleExtra(StoryExtra.AVG_HR)
        val second = first.toggleExtra(StoryExtra.CADENCE)

        assertThat(first.preset).isEqualTo(StoryPreset.CUSTOM)
        assertThat(first.extras).containsExactly(StoryExtra.AVG_HR)
        assertThat(second.extras).containsExactly(StoryExtra.AVG_HR, StoryExtra.CADENCE)
    }

    @Test
    fun `toggling a selected extra removes it`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE))

        val withoutHr = recipe.toggleExtra(StoryExtra.AVG_HR)

        assertThat(withoutHr.extras).containsExactly(StoryExtra.CADENCE)
        assertThat(withoutHr.preset).isEqualTo(StoryPreset.CUSTOM)
    }

    @Test
    fun `toggling a third extra is ignored instead of throwing`() {
        val full = StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE))

        val unchanged = full.toggleExtra(StoryExtra.DATE)

        assertThat(unchanged).isEqualTo(full)
    }

    @Test
    fun `toggling preserves duration map and quality`() {
        val base = StoryRecipe(
            duration = StoryDuration.SEC_45,
            mapStyle = StoryMapStyle.TERRAIN,
            quality = StoryQuality.STANDARD_1080P30,
        )

        val toggled = base.toggleExtra(StoryExtra.DATE)

        assertThat(toggled.duration).isEqualTo(StoryDuration.SEC_45)
        assertThat(toggled.mapStyle).isEqualTo(StoryMapStyle.TERRAIN)
        assertThat(toggled.quality).isEqualTo(StoryQuality.STANDARD_1080P30)
    }

    private fun allValues() = StoryMetricValues(
        distanceKm = 10.0,
        totalTimeSec = 3_000,
        averagePace = Pace.fromSecondsPerKm(300),
        calories = 640,
        elevationGainM = 84.0,
        averageHeartRate = 152,
        maxHeartRate = 178,
        averageCadenceSpm = 172,
        completedSplits = 10,
        fastestKm = 4,
        fastestPace = Pace.fromSecondsPerKm(290),
        localDate = LocalDate.parse("2026-11-13"),
    )
}
