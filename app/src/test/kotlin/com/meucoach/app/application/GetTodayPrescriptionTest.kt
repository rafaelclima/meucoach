package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GetTodayPrescriptionTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: DailyPrescriptionRepository
    private lateinit var useCase: GetTodayPrescription
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = DailyPrescriptionRepository(database, clock)
        useCase = GetTodayPrescription(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the same inputs are persisted once`(): Unit = runBlocking {
        val first = useCase(request(plan = plan()))
        val second = useCase(request(plan = plan()))

        assertThat(second.id).isEqualTo(first.id)
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `a changed check-in appends a version that supersedes the previous`(): Unit = runBlocking {
        val first = useCase(request(plan = plan(), readiness = readiness(ReadinessLevel.GREEN)))
        val second = useCase(
            request(
                plan = plan(),
                readiness = readiness(ReadinessLevel.AMBER, flags = setOf(ReadinessFlag.NO_QUALITY)),
            ),
        )

        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(second.supersedesId).isEqualTo(first.id)
        assertThat(repository.getVersions(today)).hasSize(2)
        assertThat(repository.getLatest(today)?.id).isEqualTo(second.id)
    }

    @Test
    fun `a green day with a planned easy keeps the plan intent`(): Unit = runBlocking {
        val plan = plan()
        val prescription: DailyPrescription = useCase(request(plan = plan))

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.plannedSessionId).isEqualTo("2026-11-02-2026-11-02")
        assertThat(prescription.plannedTitle).isEqualTo("Fácil e conversável")
        assertThat(prescription.targets).isEqualTo(plan.sessions.single().targets)
    }

    @Test
    fun `a closed session is treated as no plan`(): Unit = runBlocking {
        val closed = plan().let { current ->
            current.copy(
                sessions = current.sessions.map {
                    it.copy(
                        status = SessionStatus.CLOSED,
                        closureReason = com.meucoach.core.domain.prescription.SessionClosureReason.USER_SKIPPED,
                        closedAt = java.time.Instant.EPOCH,
                    )
                },
            )
        }

        val prescription = useCase(request(plan = closed))

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `a rest closure keeps the day prescription as rest`(): Unit = runBlocking {
        val closed = plan().let { current ->
            current.copy(
                sessions = current.sessions.map {
                    it.copy(
                        status = SessionStatus.CLOSED,
                        closureReason = com.meucoach.core.domain.prescription.SessionClosureReason.REST_PRESCRIBED,
                        closedAt = java.time.Instant.EPOCH,
                    )
                },
            )
        }

        val prescription = useCase(request(plan = closed, readiness = readiness(ReadinessLevel.RED)))

        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.REST)
        assertThat(prescription.plannedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.REST)
    }

    @Test
    fun `no plan still produces a conservative easy day`(): Unit = runBlocking {
        val prescription = useCase(request(plan = null))

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.plannedType).isNull()
        assertThat(prescription.ruleIds).isNotEmpty
        assertThat(prescription.evidenceIds).isNotEmpty
    }

    private fun request(plan: WeeklyPlan?, readiness: ReadinessResult = readiness(ReadinessLevel.GREEN)) =
        TodayPrescriptionRequest(
            date = today,
            profile = profile(),
            readiness = readiness,
            load = load(acwr = 1.0),
            plan = plan,
        )

    private fun plan() = WeeklyPlan(
        weekStart = today,
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                targets = com.meucoach.core.domain.prescription.PrescriptionTargets(
                    zone = "Z2",
                    rpe = 4,
                ),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 40)),
    )

    private fun readiness(
        level: ReadinessLevel,
        flags: Set<ReadinessFlag> = emptySet(),
        score: Int = if (level == ReadinessLevel.RED) 20 else 80,
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to score),
        missing = emptySet(),
        flags = flags,
    )

    private fun load(acwr: Double?) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
        monotony = null,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = null,
            sourceFresh = true,
        ),
    )
}
