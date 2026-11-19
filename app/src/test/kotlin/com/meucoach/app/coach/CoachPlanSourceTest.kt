package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanningFingerprints
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachPlanSourceTest {
    private val today = LocalDate.of(2026, 11, 16)
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var source: CoachPlanSource

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
        plans = TrainingPlanRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        source = RoomCoachPlanSource(
            plans = plans,
            time = FakeTimeProvider(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `sem plano devolve null`(): Unit = runBlocking {
        val context = source.observeCurrent().first()

        assertThat(context).isNull()
    }

    @Test
    fun `emite o plano com a versao e o hash de fallback`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val context = source.observeCurrent().first { it != null }!!

        assertThat(context.version).isEqualTo(1)
        assertThat(context.inputsHash)
            .isEqualTo("$today|${WeeklyPlanGenerator.ENGINE_VERSION}|${plan().weeklyMinutes}")
        assertThat(context.plan.sessions).hasSize(2)
    }

    @Test
    fun `reemite quando o conteudo da semana muda`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val changed = plan().sessions.map { it.copy(title = "Fácil ajustado", durationMin = 25) }

        val context = source.observeCurrent().awaitChange(
            initial = { it?.plan?.sessions?.firstOrNull()?.title == "Fácil" },
            changed = { it?.plan?.sessions?.all { session -> session.title == "Fácil ajustado" } == true },
        ) {
            assertThat(plans.updateSessions(today, changed)).isTrue()
        }

        assertThat(context!!.version).isEqualTo(1)
        assertThat(context.plan.sessions.map { it.durationMin }).containsOnly(25)
    }

    @Test
    fun `reemite com nova versao e hash quando um replanejamento e aceito`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(plan())
        val record = plans.getWeekRecord(today)!!
        val fingerprint = PlanningFingerprints.of(profile())
        val candidate = plan().copy(
            sessions = plan().sessions.map { session ->
                if (session.date.isAfter(today)) {
                    session.copy(title = "Fácil replanejado", durationMin = 20)
                } else {
                    session
                }
            },
        )
        val suggestion = ReplanSuggestion(
            weekStart = today,
            trigger = ReplanTrigger.USER_REQUEST,
            causes = listOf(ReplanCause.USER_REQUEST),
            missedTrainableCount = 0,
            expectedVersion = record.version,
            expectedInputsHash = "hash-replanejado",
            expectedAvailabilityHash = record.availabilityHash,
            availabilityHash = "availability-replanejada",
            expectedFingerprintVersion = fingerprint.version,
            expectedFingerprint = fingerprint.hash,
            profileSnapshot = fingerprint.snapshot,
            expectedTemporalContextHash = PlanningFingerprints.temporalContextHash(profile()),
            rationale = "Ajuste aceito.",
            ruleIds = listOf("R-PLAN-02"),
            evidenceIds = listOf("EV-SEILER-2010"),
            confidence = DataConfidence.MEDIUM,
        )

        val context = source.observeCurrent().awaitChange(
            initial = { it?.version == 1 },
            changed = {
                it?.version == 2 &&
                    it.plan.sessions.first { session -> session.date.isAfter(today) }.title == "Fácil replanejado"
            },
        ) {
            val outcome = plans.applyReplan(suggestion, candidate, today)
            assertThat(outcome).isInstanceOf(
                com.meucoach.core.data.repository.ReplanApplyOutcome.Applied::class.java,
            )
        }

        assertThat(context!!.inputsHash).isEqualTo("hash-replanejado")
        assertThat(context.plan.sessions.first { it.date.isAfter(today) }.title).isEqualTo("Fácil replanejado")
    }

    private suspend fun <T> Flow<T>.awaitChange(
        initial: (T) -> Boolean,
        changed: (T) -> Boolean,
        mutate: suspend () -> Unit,
    ): T? = kotlinx.coroutines.coroutineScope {
        val channel = Channel<T>(Channel.UNLIMITED)
        val job = launch { collect { channel.send(it) } }
        try {
            withTimeout(2_000) {
                while (!initial(channel.receive())) {
                    // Wait for the first meaningful emission before mutating.
                }
            }
            mutate()
            var result: T? = null
            withTimeout(5_000) {
                while (result == null) {
                    val value = channel.receive()
                    if (changed(value)) result = value
                }
            }
            result
        } finally {
            job.cancel()
        }
    }

    private fun plan() = WeeklyPlan(
        weekStart = today,
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
            PlannedSession(
                date = today.plusDays(1),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 60,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = today,
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "UTC",
    )
}
