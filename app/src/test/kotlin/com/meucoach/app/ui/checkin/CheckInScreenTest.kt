package com.meucoach.app.ui.checkin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
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
class CheckInScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the four questions and saves`() {
        var saved = false
        setContent(loadedState(), onSave = { saved = true })

        compose.onNodeWithText("Como você está hoje?").assertIsDisplayed()
        compose.onNodeWithText("Sono").assertIsDisplayed()
        compose.onNodeWithText("Fadiga").assertIsDisplayed()
        compose.onNodeWithTag("checkin-list").performScrollToNode(hasText("Dor muscular"))
        compose.onNodeWithText("Dor muscular").assertIsDisplayed()
        compose.onNodeWithTag("checkin-list").performScrollToNode(hasText("Estresse"))
        compose.onNodeWithText("Estresse").assertIsDisplayed()
        compose.onNodeWithTag("checkin-save").performClick()

        assertThat(saved).isTrue()
    }

    @Test
    fun `labels an existing check-in as update`() {
        setContent(loadedState().copy(existing = true))

        compose.onNodeWithText("Atualizar check-in").assertIsDisplayed()
    }

    @Test
    fun `updates a score`() {
        var sleep = 3
        compose.setContent {
            McTheme(darkTheme = true) {
                CheckInContent(
                    state = loadedState(),
                    onUpdate = { transform ->
                        sleep = transform(CheckInForm()).sleepQuality
                    },
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onAllNodesWithText("4")[0].performClick()

        assertThat(sleep).isEqualTo(4)
    }

    @Test
    fun `hides the form while loading`() {
        setContent(CheckInUiState())

        compose.onNodeWithText("Como você está hoje?").assertDoesNotExist()
    }

    private fun setContent(state: CheckInUiState, onSave: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                CheckInContent(
                    state = state,
                    onUpdate = {},
                    onSave = onSave,
                    onBack = {},
                )
            }
        }
    }

    private fun loadedState() = CheckInUiState(
        isLoading = false,
        form = CheckInForm(),
    )
}
