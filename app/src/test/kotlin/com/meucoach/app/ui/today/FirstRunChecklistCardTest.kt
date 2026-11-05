package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meucoach.core.designsystem.theme.McTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstRunChecklistCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the progress and triggers each action`() {
        var imported = false
        var health = false
        var dismissed = false
        setContent(
            state = FirstRunChecklistState(
                isLoading = false,
                hasCheckedIn = true,
                hasActivities = false,
                healthConnected = false,
            ),
            onImport = { imported = true },
            onHealth = { health = true },
            onDismiss = { dismissed = true },
        )

        compose.onNodeWithText("COMPLETE SEU SETUP").assertIsDisplayed()
        compose.onNodeWithText("1 de 3").assertIsDisplayed()
        compose.onNodeWithText("Check-in de hoje").assertIsDisplayed()

        compose.onNodeWithTag("checklist-import").performClick()
        compose.onNodeWithTag("checklist-health").performClick()
        compose.onNodeWithTag("checklist-dismiss").performClick()

        assertThat(imported).isTrue()
        assertThat(health).isTrue()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun `hides when dismissed`() {
        setContent(state = FirstRunChecklistState(isLoading = false, dismissed = true))

        compose.onNodeWithTag("first-run-checklist").assertDoesNotExist()
    }

    @Test
    fun `hides when complete`() {
        setContent(
            state = FirstRunChecklistState(
                isLoading = false,
                hasCheckedIn = true,
                hasActivities = true,
                healthConnected = true,
            ),
        )

        compose.onNodeWithTag("first-run-checklist").assertDoesNotExist()
    }

    private fun setContent(
        state: FirstRunChecklistState,
        onImport: () -> Unit = {},
        onHealth: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                FirstRunChecklistCard(
                    state = state,
                    onCheckIn = {},
                    onImport = onImport,
                    onHealth = onHealth,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
