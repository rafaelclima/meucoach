package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class CoachNextSourceTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var source: CoachNextSource

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
        prescriptions = DailyPrescriptionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        source = RoomCoachNextSource(
            plans = plans,
            prescriptions = prescriptions,
            raceEvents = raceEvents,
            time = FakeTimeProvider(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `devolve hoje pendente com a prescricao vigente`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))
        prescriptions.append(prescription(monday))

        val next = source.observeNext().first { it != null }!!

        assertThat(next.session.date).isEqualTo(monday)
        assertThat(next.isToday).isTrue()
        assertThat(next.prescription?.date).isEqualTo(monday)
        assertThat(next.raceEvent).isNull()
    }

    @Test
    fun `sem pendencia hoje devolve a proxima futura sem prescricao`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))

        val next = source.observeNext().first { it != null }!!

        assertThat(next.session.date).isEqualTo(monday.plusDays(2))
        assertThat(next.isToday).isFalse()
        assertThat(next.prescription).isNull()
    }

    @Test
    fun `sem plano nao ha proximo`(): Unit = runBlocking {
        val next = source.observeNext().first()

        assertThat(next).isNull()
    }

    @Test
    fun `evento de prova na data aparece no contexto`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))
        raceEvents.create(NewRaceEvent(RaceDistance.FIVE_K, monday.plusDays(2)))

        val next = source.observeNext().first { it != null }!!

        assertThat(next.raceEvent?.targetDate).isEqualTo(monday.plusDays(2))
    }

    private fun plan(todayStatus: SessionStatus) = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            PlannedSession(
                date = monday,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = todayStatus,
                completionEvidence = if (todayStatus == SessionStatus.DONE) {
                    CompletionEvidence.MANUAL
                } else {
                    null
                },
            ),
            PlannedSession(
                date = monday.plusDays(2),
                type = WorkoutType.QUALITY,
                title = "Ritmo forte",
                durationMin = 35,
                rationale = "Estímulo.",
                ruleIds = listOf("R-PER-03"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 65,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = monday,
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    private fun prescription(date: LocalDate) = DailyPrescription(
        date = date,
        plannedSessionId = null,
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil",
        plannedDurationMin = 30,
        prescribedType = WorkoutType.EASY,
        title = "Fácil",
        prescribedDurationMin = 30,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Dia verde.",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "test-hash",
    )
}
