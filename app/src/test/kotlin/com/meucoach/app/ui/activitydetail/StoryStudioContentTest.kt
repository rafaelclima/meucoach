package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryStudioContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the fixed essentials and the extra counter`() {
        setContent()

        compose.onNodeWithText("OBRIGATÓRIAS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Distância · Ritmo médio · Tempo total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("EXTRAS (0/2)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `disables an extra without data and explains why`() {
        setContent(extraOptions = extraOptions(unavailable = setOf(StoryExtra.AVG_HR)))

        compose.onNodeWithTag("story-extra-avg_hr").performScrollTo()
        compose.onNodeWithTag("story-extra-avg_hr").assertIsNotEnabled()
        compose.onNodeWithText("FC média · sem FC").assertIsDisplayed()
    }

    @Test
    fun `toggles an available extra`() {
        var toggled: StoryExtra? = null
        setContent(onToggleExtra = { toggled = it })

        compose.onNodeWithTag("story-extra-cadence").performScrollTo().performClick()

        assertThat(toggled).isEqualTo(StoryExtra.CADENCE)
    }

    @Test
    fun `blocks a third extra`() {
        setContent(
            recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)),
        )

        compose.onNodeWithTag("story-extra-date").performScrollTo()
        compose.onNodeWithTag("story-extra-date").assertIsNotEnabled()
        compose.onNodeWithText("EXTRAS (2/2)").assertIsDisplayed()
    }

    @Test
    fun `keeps a selected extra removable even when its data disappeared`() {
        var toggled: StoryExtra? = null
        setContent(
            recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR)),
            extraOptions = extraOptions(unavailable = setOf(StoryExtra.AVG_HR)),
            onToggleExtra = { toggled = it },
        )

        compose.onNodeWithTag("story-extra-avg_hr").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("story-extra-avg_hr").performClick()

        assertThat(toggled).isEqualTo(StoryExtra.AVG_HR)
    }

    @Test
    fun `selects a preset`() {
        var preset: StoryPreset? = null
        setContent(onPreset = { preset = it })

        compose.onNodeWithTag("story-preset-physiological").performScrollTo().performClick()

        assertThat(preset).isEqualTo(StoryPreset.PHYSIOLOGICAL)
    }

    @Test
    fun `selects duration and map style`() {
        var duration: StoryDuration? = null
        var style: StoryMapStyle? = null
        setContent(onDuration = { duration = it }, onMapStyle = { style = it })

        compose.onNodeWithTag("story-duration-sec_45").performScrollTo().performClick()
        compose.onNodeWithTag("story-map-meucoach_light").performScrollTo().performClick()

        assertThat(duration).isEqualTo(StoryDuration.SEC_45)
        assertThat(style).isEqualTo(StoryMapStyle.MEUCOACH_LIGHT)
    }

    @Test
    fun `shows the map options without satellite`() {
        setContent()

        compose.onNodeWithTag("story-map-meucoach_dark").performScrollTo()
        compose.onNodeWithText("Escuro").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Terreno").assertIsDisplayed()
        compose.onNodeWithText("Satélite").assertDoesNotExist()
    }

    @Test
    fun `shows the first-run map notice and dismisses it`() {
        var dismissed = false
        setContent(introSeen = false, onDismissIntro = { dismissed = true })

        compose.onNodeWithTag("story-intro-dismiss").performScrollTo().performClick()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun `explains the terrain download and the route-only preview`() {
        setContent(recipe = StoryRecipe(mapStyle = StoryMapStyle.TERRAIN))

        compose.onNodeWithText("Relevo do terreno via Mapzen · baixa na primeira vez.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("A prévia desenha a rota; o mapa escolhido entra no vídeo.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `toggles save as default and resets the recipe`() {
        var save: Boolean? = null
        var reset = false
        setContent(onSaveAsDefault = { save = it }, onReset = { reset = true })

        compose.onNodeWithTag("story-save-default").performScrollTo().performClick()
        compose.onNodeWithTag("story-reset-recipe").performScrollTo().performClick()

        assertThat(save).isFalse()
        assertThat(reset).isTrue()
    }

    @Test
    fun `generates when the activity has a plan`() {
        var generated = false
        setContent(onGenerate = { generated = true })

        compose.onNodeWithTag("story-generate").performScrollTo().performClick()

        assertThat(generated).isTrue()
    }

    @Test
    fun `disables generation without a gps plan`() {
        setContent(plan = null)

        compose.onNodeWithTag("story-generate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Esta atividade não tem rota GPS suficiente para um story.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun setContent(
        recipe: StoryRecipe = StoryRecipe(),
        extraOptions: List<StoryExtraOption> = extraOptions(),
        plan: com.meucoach.core.domain.story.StoryPlan? = plan(),
        introSeen: Boolean = true,
        onDismissIntro: () -> Unit = {},
        onPreset: (StoryPreset) -> Unit = {},
        onToggleExtra: (StoryExtra) -> Unit = {},
        onDuration: (StoryDuration) -> Unit = {},
        onMapStyle: (StoryMapStyle) -> Unit = {},
        onSaveAsDefault: (Boolean) -> Unit = {},
        onReset: () -> Unit = {},
        onGenerate: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                StoryStudioContent(
                    state = StoryStudioUiState(
                        recipe = recipe,
                        plan = plan,
                        extras = extraOptions,
                        saveAsDefault = true,
                    ),
                    avatar = null,
                    introSeen = introSeen,
                    onClose = {},
                    onDismissIntro = onDismissIntro,
                    onPreset = onPreset,
                    onToggleExtra = onToggleExtra,
                    onDuration = onDuration,
                    onMapStyle = onMapStyle,
                    onSaveAsDefault = onSaveAsDefault,
                    onReset = onReset,
                    onGenerate = onGenerate,
                )
            }
        }
    }

    private fun extraOptions(unavailable: Set<StoryExtra> = emptySet()): List<StoryExtraOption> =
        StoryExtra.entries.map { extra ->
            if (extra in unavailable) {
                StoryExtraOption(
                    extra = extra,
                    label = StoryMetricCatalog.label(extra),
                    isAvailable = false,
                    reason = "sem FC",
                )
            } else {
                StoryExtraOption(
                    extra = extra,
                    label = StoryMetricCatalog.label(extra),
                    isAvailable = true,
                )
            }
        }

    private fun plan() = StoryPlanCalculator.calculate(ActivityDetailTestData.activity().imported)
}
