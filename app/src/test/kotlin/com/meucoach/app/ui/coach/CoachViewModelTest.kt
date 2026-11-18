package com.meucoach.app.ui.coach

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachPlanContext
import com.meucoach.app.coach.CoachPlanSource
import com.meucoach.app.coach.CoachReviewSource
import com.meucoach.core.data.coach.FakeCoachApi
import com.meucoach.core.domain.coach.CoachRawResponse
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.DataConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachViewModelTest {
    private lateinit var consent: CoachConsentStore
    private lateinit var plans: MutableStateFlow<CoachPlanContext?>
    private lateinit var reviews: MutableStateFlow<WeeklyReview?>

    private fun plan(): WeeklyPlan = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 16),
        sessions = listOf(
            PlannedSession(
                date = LocalDate.of(2026, 11, 17),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 30,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 16),
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        consent = CoachConsentStore(context, "coach-vm-${System.nanoTime()}")
        plans = MutableStateFlow(null)
        reviews = MutableStateFlow(null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeCoachApi) = CoachViewModel(
        consent = consent,
        plans = object : CoachPlanSource {
            override fun observeCurrent() = plans
        },
        reviews = object : CoachReviewSource {
            override fun observeLastClosedWeek() = reviews
        },
        api = api,
    )

    private fun weeklyReview() = WeeklyReview(
        weekStart = LocalDate.of(2026, 11, 9),
        weekEnd = LocalDate.of(2026, 11, 15),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = WeeklyAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
        ),
        volume = WeeklyVolume(plannedMin = 120, executedMin = 90, percentOfPlan = 75, extraMin = 10),
        quality = null,
        intensity = null,
        intensitySessions = 0,
        readiness = null,
        sleep = null,
        observations = listOf(
            WeeklyReviewObservation(
                kind = WeeklyReviewObservationKind.ADHERENCE_PARTIAL,
                ruleId = "R-REVIEW-01",
                evidenceIds = emptyList(),
                confidence = DataConfidence.HIGH,
            ),
        ),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.HIGH,
    )

    private fun awaitSettled(vm: CoachViewModel): CoachExplanationState = runBlocking {
        withTimeout(2000) {
            var state = vm.explanation.value
            while (state is CoachExplanationState.Loading ||
                (state is CoachExplanationState.Unavailable && consent.isConsented() && plans.value != null)
            ) {
                kotlinx.coroutines.delay(1)
                state = vm.explanation.value
            }
            state
        }
    }

    @Test
    fun `resposta valida vira Ready`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min.")),
        )

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
    }

    @Test
    fun `numero inventado vira Fallback com rationale do motor`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "Corra a 6:10/km")))

        val state = awaitSettled(vm)
        assertThat(state).isInstanceOf(CoachExplanationState.Fallback::class.java)
        assertThat((state as CoachExplanationState.Fallback).planRationale).isEqualTo("Semana base")
    }

    @Test
    fun `falha de rede vira Fallback`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(error = IOException("down")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Fallback::class.java)
    }

    @Test
    fun `total semanal e valido mesmo fora das sessoes`() {
        consent.grant()
        plans.value = CoachPlanContext(plan().copy(weeklyMinutes = 114), "hash1")
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Foco", body = "114 min no total, mantidos.")),
        )

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
    }

    @Test
    fun `sem consentimento nao chama a api`() {
        plans.value = CoachPlanContext(plan(), "hash1")
        val api = FakeCoachApi(error = IOException("nao deveria chamar"))
        val vm = viewModel(api)

        assertThat(vm.explanation.value).isInstanceOf(CoachExplanationState.Unavailable::class.java)
        assertThat(api.calls).isEqualTo(0)
    }

    @Test
    fun `retry dispara nova chamada`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val api = FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min."))
        val vm = viewModel(api)
        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
        assertThat(api.calls).isEqualTo(1)

        vm.retry()

        runBlocking {
            withTimeout(2000) {
                while (api.calls < 2) {
                    kotlinx.coroutines.delay(1)
                }
            }
        }
        assertThat(api.calls).isEqualTo(2)
    }

    @Test
    fun `revisao valida vira Ready com o resumo deterministico exposto`() {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Semana passada", body = "3 de 4 sessões, 75% do volume.")),
        )

        val state = awaitReviewSettled(vm)
        assertThat(state).isInstanceOf(CoachReviewState.Ready::class.java)
        assertThat((state as CoachReviewState.Ready).title).isEqualTo("Semana passada")
        assertThat(vm.review.value?.adherence?.done).isEqualTo(3)
    }

    @Test
    fun `revisao com porcentagem inventada cai para o resumo do motor`() {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "O volume ficou em 140% do plano")))

        assertThat(awaitReviewSettled(vm)).isInstanceOf(CoachReviewState.Fallback::class.java)
        assertThat(vm.review.value).isNotNull
    }

    @Test
    fun `sem consentimento a revisao local aparece mas nao chama a api`() {
        reviews.value = weeklyReview()
        val api = FakeCoachApi(error = IOException("nao deveria chamar"))
        val vm = viewModel(api)

        assertThat(vm.review.value).isNotNull
        assertThat(vm.reviewState.value).isInstanceOf(CoachReviewState.Unavailable::class.java)
        assertThat(api.reviewCalls).isEqualTo(0)
    }

    private fun awaitReviewSettled(vm: CoachViewModel): CoachReviewState = runBlocking {
        withTimeout(2000) {
            var state = vm.reviewState.value
            while (state is CoachReviewState.Loading ||
                (state is CoachReviewState.Unavailable && consent.isConsented() && reviews.value != null)
            ) {
                kotlinx.coroutines.delay(1)
                state = vm.reviewState.value
            }
            state
        }
    }
}
