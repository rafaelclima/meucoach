package com.meucoach.app.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrendsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows loading`() {
        setContent(TrendsUiState())

        compose.onNodeWithTag("trends-loading").assertIsDisplayed()
    }

    @Test
    fun `shows an empty state without a profile`() {
        setContent(TrendsUiState(isLoading = false))

        compose.onNodeWithText("Perfil não configurado").assertIsDisplayed()
    }

    @Test
    fun `shows an empty state without load data`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = false),
            ),
        )

        compose.onNodeWithText("Sem carga para mostrar").assertIsDisplayed()
    }

    @Test
    fun `shows the load summary and PMC`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
            ),
        )

        compose.onNodeWithText("Carga de treino").assertIsDisplayed()
        compose.onNodeWithText("CTL").assertIsDisplayed()
        compose.onNodeWithText("ACWR").assertIsDisplayed()

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Sem alerta de ACWR"))
        compose.onNodeWithText("Sem alerta de ACWR").assertIsDisplayed()

        list.performScrollToNode(hasText("Como a carga acumulou"))
        compose.onNodeWithContentDescription("Gráfico de CTL e ATL dos últimos 42 dias")
            .assertExists()
    }

    private fun setContent(state: TrendsUiState) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(state = state)
            }
        }
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "America/Sao_Paulo",
    )

    private fun snapshot(hasData: Boolean): LoadSnapshot {
        val start = LocalDate.of(2026, 9, 19)
        val daily = List(42) { index ->
            DailyLoad(
                date = start.plusDays(index.toLong()),
                load = if (hasData && index >= 35) 50.0 else 0.0,
                metric = if (hasData && index >= 35) {
                    com.meucoach.core.domain.load.LoadMetric.RTSS
                } else {
                    null
                },
            )
        }
        return LoadSnapshot(
            daily = daily,
            ctlSeries = daily.map { if (hasData) it.load / 2 else 0.0 },
            atlSeries = daily.map { if (hasData) it.load else 0.0 },
            ctl = if (hasData) 10.0 else 0.0,
            atl = if (hasData) 20.0 else 0.0,
            tsb = if (hasData) -10.0 else 0.0,
            acuteAverage7d = if (hasData) 25.0 else 0.0,
            chronicAverage28d = if (hasData) 22.0 else 0.0,
            acwr = if (hasData) 1.1 else null,
            monotony = if (hasData) 0.8 else null,
            missingDays = if (hasData) 35 else 42,
        )
    }
}
