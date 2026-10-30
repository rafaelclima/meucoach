package com.meucoach.app.ui.metrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meucoach.core.data.health.HealthConnectStatus
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
class HealthConnectContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows unavailable status`() {
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.UNAVAILABLE,
            ),
        )

        compose.onNodeWithText("Indisponível neste aparelho").assertIsDisplayed()
    }

    @Test
    fun `requests permissions when they are missing`() {
        var authorized = false
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.AVAILABLE,
                requiredPermissions = setOf("sleep", "hr"),
                missingPermissions = setOf("sleep", "hr"),
            ),
            onAuthorize = { authorized = true },
        )

        compose.onNodeWithText("Autorizar leitura").performClick()

        assertThat(authorized).isTrue()
    }

    @Test
    fun `syncs when permissions are granted`() {
        var synced = false
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.AVAILABLE,
                requiredPermissions = setOf("sleep", "hr"),
                missingPermissions = emptySet(),
            ),
            onSync = { synced = true },
        )

        compose.onNodeWithText("Sincronizar agora").performClick()

        assertThat(synced).isTrue()
    }

    @Test
    fun `shows a sync message`() {
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.AVAILABLE,
                missingPermissions = emptySet(),
                message = "2 dias importados do Health Connect.",
            ),
        )

        compose.onNodeWithText("2 dias importados do Health Connect.").assertIsDisplayed()
    }

    private fun setContent(state: HealthConnectUiState, onAuthorize: () -> Unit = {}, onSync: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthConnectContent(state = state, onAuthorize = onAuthorize, onSync = onSync)
            }
        }
    }
}
