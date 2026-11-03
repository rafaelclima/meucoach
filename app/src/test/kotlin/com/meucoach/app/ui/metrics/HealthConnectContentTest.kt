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
    fun `explains what health connect is and shows unavailable status`() {
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.UNAVAILABLE,
            ),
        )

        compose.onNodeWithText("Indisponível neste aparelho").assertIsDisplayed()
        compose.onNodeWithText("É o app de saúde do Android", substring = true).assertIsDisplayed()
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

        compose.onNodeWithText("Conectar Health Connect").performClick()

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
            storedDays = 12,
            onSync = { synced = true },
        )

        compose.onNodeWithText("Conectado · 12 dias").assertIsDisplayed()
        compose.onNodeWithText("Atualizar agora").performClick()

        assertThat(synced).isTrue()
    }

    @Test
    fun `offers installation when the provider is missing`() {
        var installed = false
        setContent(
            HealthConnectUiState(
                isLoading = false,
                status = HealthConnectStatus.UNAVAILABLE,
            ),
            onInstall = { installed = true },
        )

        compose.onNodeWithText("Instalar o Health Connect").performClick()

        assertThat(installed).isTrue()
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

    private fun setContent(
        state: HealthConnectUiState,
        storedDays: Int = 0,
        onAuthorize: () -> Unit = {},
        onSync: () -> Unit = {},
        onInstall: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthConnectContent(
                    state = state,
                    storedDays = storedDays,
                    onAuthorize = onAuthorize,
                    onSync = onSync,
                    onInstall = onInstall,
                )
            }
        }
    }
}
