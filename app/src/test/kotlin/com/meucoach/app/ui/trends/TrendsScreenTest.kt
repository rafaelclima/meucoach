package com.meucoach.app.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlanCompletion
import com.meucoach.core.domain.prescription.PlanCompletionWeek
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.trends.ActivityTrends
import com.meucoach.core.domain.trends.ConsistencyDay
import com.meucoach.core.domain.trends.ConsistencySummary
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.PaceHeartRatePoint
import com.meucoach.core.domain.trends.PersonalRecords
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.domain.trends.WeeklyVolume
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
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

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Base", ignoreCase = true))
        compose.onNodeWithText("Base", ignoreCase = true).assertIsDisplayed()
        list.performScrollToNode(hasText("Aumento", ignoreCase = true))
        compose.onNodeWithText("Aumento", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("ACWR", ignoreCase = true).assertDoesNotExist()

        list.performScrollToNode(hasText("Sem alerta de carga"))
        compose.onNodeWithText("Sem alerta de carga").assertIsDisplayed()

        list.performScrollToNode(hasText("Como a carga acumulou"))
        compose.onNodeWithContentDescription("Gráfico de carga de base e fadiga recente dos últimos 42 dias")
            .assertExists()
    }

    @Test
    fun `shows readiness history`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(hasData = true),
                    ),
                    readinessHistory = listOf(
                        ReadinessHistoryDay(
                            date = LocalDate.of(2026, 10, 29),
                            score = 48,
                            level = ReadinessLevel.AMBER,
                            confidence = DataConfidence.LOW,
                            hasCheckIn = true,
                        ),
                    ),
                )
            }
        }

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Prontidão · 14 dias"))
        compose.onNodeWithText("Prontidão · 14 dias").assertIsDisplayed()
        list.performScrollToNode(hasText("48"))
        compose.onNodeWithText("48").assertIsDisplayed()
        compose.onNodeWithText("Moderada").assertIsDisplayed()
    }

    @Test
    fun `shows volume, consistency and pace heart rate`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(hasData = true),
                        activityTrends = trends(),
                    ),
                )
            }
        }

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Volume · 2 semanas"))
        compose.onNodeWithText("Volume · 2 semanas").assertIsDisplayed()
        list.performScrollToNode(hasText("Hábito · Dias ativos"))
        list.performScrollToNode(hasText("DIAS ATIVOS"))
        compose.onNodeWithText("DIAS ATIVOS").assertIsDisplayed()
        list.performScrollToNode(hasText("Economia · Ritmo vs FC"))
        list.performScrollToNode(hasText("Metros por batimento"))
        compose.onNodeWithText("Metros por batimento").assertIsDisplayed()
        list.performScrollToNode(hasText("Escala ampliada", substring = true))
        compose
            .onNodeWithText("Escala ampliada", substring = true)
            .assertIsDisplayed()
        compose
            .onNodeWithText("para destacar diferenças pequenas entre corridas", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Eixo de", substring = true).assertDoesNotExist()
        list.performScrollToNode(hasText("6:00 /km"))
        compose.onNodeWithText("6:00 /km").assertIsDisplayed()
    }

    @Test
    fun `selects a different activity period`() {
        var selected: TrendsPeriod? = null
        setContent(
            state = TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
                activityTrends = trends(),
            ),
            onPeriodSelected = { selected = it },
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("12 sem"))
        compose.onNodeWithText("12 sem").performClick()

        assertThat(selected).isEqualTo(TrendsPeriod.WEEKS_12)
    }

    @Test
    fun `shows the intensity split with weekly rows`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile().copy(lactateThresholdHeartRate = 160),
                        snapshot = snapshot(hasData = true),
                        activityTrends = trends(),
                        intensity = IntensityDistribution(
                            windowDays = 28,
                            easySec = 1_800,
                            moderateSec = 300,
                            hardSec = 300,
                            sessions = 4,
                            weeks = listOf(
                                WeeklyIntensity(LocalDate.of(2026, 10, 19), 1_200, 300, 0),
                                WeeklyIntensity(LocalDate.of(2026, 10, 26), 600, 0, 300),
                            ),
                            confidence = DataConfidence.MEDIUM,
                        ),
                    ),
                )
            }
        }

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(
            hasContentDescription("Distribuição por tempo: 75% fácil, 13% moderado e 13% forte"),
        )
        compose
            .onNodeWithContentDescription("Distribuição por tempo: 75% fácil, 13% moderado e 13% forte")
            .assertExists()
        list.performScrollToNode(hasText("Fácil · Z1–Z2"))
        compose.onNodeWithText("Fácil · Z1–Z2").assertIsDisplayed()
        compose.onNodeWithText("Confiança média").assertIsDisplayed()
        list.performScrollToNode(hasText("67% fácil"))
        compose.onNodeWithText("67% fácil").assertIsDisplayed()
    }

    @Test
    fun `explains the intensity split without a calibrated threshold`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
                activityTrends = trends(),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Sem limite de esforço calibrado"))
        compose.onNodeWithText("Sem limite de esforço calibrado").assertIsDisplayed()
    }

    @Test
    fun `shows the consistency heatmap`() {
        val start = LocalDate.of(2026, 10, 5)
        val heatmap = (0 until 26).map { offset ->
            val date = start.plusDays(offset.toLong())
            val active = date.dayOfWeek == java.time.DayOfWeek.MONDAY
            ConsistencyDay(
                date = date,
                kilometers = if (active) 5.0 else 0.0,
                runs = if (active) 1 else 0,
            )
        }
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
                activityTrends = trends().copy(heatmap = heatmap),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Dias que você correu"))
        compose.onNodeWithText("Dias que você correu").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Mapa de consistência de 26 dias: 4 dias ativos")
            .assertExists()
        compose.onNodeWithText("4 dias ativos").assertIsDisplayed()
    }

    @Test
    fun `shows plan completion`() {
        val weeks = (0 until 8).map { index ->
            PlanCompletionWeek(
                weekStart = LocalDate.of(2026, 9, 7).plusWeeks(index.toLong()),
                planned = when (index) {
                    0 -> 2
                    1 -> 3
                    else -> 0
                },
                done = when (index) {
                    0 -> 1
                    1 -> 2
                    else -> 0
                },
                skipped = if (index == 0) 1 else 0,
            )
        }
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
                activityTrends = trends(),
                planCompletion = PlanCompletion(weeks = weeks, windowWeeks = 8),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("3 de 5 sessões concluídas · 1 pulada"))
        compose.onNodeWithText("Adesão ao plano").assertIsDisplayed()
        compose.onNodeWithText("3 de 5 sessões concluídas · 1 pulada").assertIsDisplayed()
    }

    @Test
    fun `shows personal records from imported runs`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
                activityTrends = trends().copy(
                    records = PersonalRecords(
                        longestDistanceKm = 8.4,
                        longestDistanceDate = LocalDate.of(2026, 10, 12),
                        bestPace = Pace.fromSecondsPerKm(348),
                        bestPaceDate = LocalDate.of(2026, 10, 8),
                        bestPaceDistanceKm = 5.0,
                        longestDurationSec = 3_640,
                        longestDurationDate = LocalDate.of(2026, 10, 12),
                    ),
                ),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Maior distância"))
        compose.onNodeWithText("Maior distância").assertIsDisplayed()
        compose.onNodeWithText("8,4 km").assertIsDisplayed()
        list.performScrollToNode(hasText("Melhor ritmo"))
        compose.onNodeWithText("Melhor ritmo").assertIsDisplayed()
        compose.onNodeWithText("5:48 /km").assertIsDisplayed()
    }

    @Test
    fun `explains missing heart rate in the intensity split`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile().copy(lactateThresholdHeartRate = 160),
                snapshot = snapshot(hasData = true),
                activityTrends = trends(),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Nenhuma corrida com FC suficiente nos últimos 28 dias."))
        compose
            .onNodeWithText("Nenhuma corrida com FC suficiente nos últimos 28 dias.")
            .assertIsDisplayed()
    }

    private fun trends() = ActivityTrends(
        weeks = listOf(
            WeeklyVolume(LocalDate.of(2026, 10, 19), 10.0, 2),
            WeeklyVolume(LocalDate.of(2026, 10, 26), 13.0, 3),
        ),
        consistency = ConsistencySummary(
            windowDays = 28,
            activeDays = 4,
            runs = 5,
            daysSinceLastRun = 1,
        ),
        paceHeartRate = listOf(
            PaceHeartRatePoint(
                date = LocalDate.of(2026, 10, 29),
                pace = Pace.fromSecondsPerKm(360),
                heartRate = 150,
            ),
        ),
        totalKilometers = 23.0,
        averagePace = Pace.fromSecondsPerKm(360),
        averageHeartRate = 150,
    )

    @Test
    fun `explains every load number on demand`() {
        setContent(
            TrendsUiState(
                isLoading = false,
                profile = profile(),
                snapshot = snapshot(hasData = true),
            ),
        )

        val list = compose.onNodeWithTag("trends-list")
        list.performScrollToNode(hasText("Entenda cada número"))
        compose.onNodeWithTag("load-help-button").performClick()

        compose.onNodeWithTag("load-help-content").assertIsDisplayed()
        compose.onNodeWithText("Como ler a carga").assertIsDisplayed()
        compose.onNodeWithText("Média diária de esforço das últimas 6 semanas", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Negativo significa que a fadiga recente", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Carga dos últimos 7 dias dividida", substring = true)
            .assertExists()
    }

    private fun setContent(state: TrendsUiState, onPeriodSelected: (TrendsPeriod) -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(state = state, onPeriodSelected = onPeriodSelected)
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
