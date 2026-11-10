package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
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
    private lateinit var plans: TrainingPlanRepository
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
        plans = TrainingPlanRepository(database, clock)
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

    @Test
    fun `beginner quality today is downgraded and the persisted plan stays intact`(): Unit = runBlocking {
        val plan = qualityPlan()
        plans.saveWeek(plan)
        val before = plans.getWeek(plan.weekStart)

        val prescription = useCase(
            request(plan = plan, profile = profile(experience = Experience.BEGINNER)),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.plannedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.plannedDurationMin).isEqualTo(40)
        assertThat(prescription.prescribedDurationMin).isLessThanOrEqualTo(40)
        assertThat(prescription.rationale).contains("iniciante")
        assertThat(prescription.ruleIds).contains("R-PRESC-04", "R-PRESC-02")
        assertThat(prescription.evidenceIds).isNotEmpty
        assertThat(prescription.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(plans.getWeek(plan.weekStart)).isEqualTo(before)
    }

    @Test
    fun `non beginner quality today keeps the strong session`(): Unit = runBlocking {
        val plan = qualityPlan()

        val prescription = useCase(
            request(plan = plan, profile = profile(experience = Experience.INTERMEDIATE)),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).doesNotContain("R-PRESC-04")
    }

    @Test
    fun `a done day keeps the persisted prescription without applying the guard again`(): Unit = runBlocking {
        val plan = qualityPlan()
        val first = useCase(
            request(plan = plan, profile = profile(experience = Experience.BEGINNER)),
        )
        assertThat(first.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)

        val done = plan.copy(
            sessions = plan.sessions.map {
                it.copy(
                    status = SessionStatus.DONE,
                    completionEvidence = CompletionEvidence.MANUAL,
                    executionId = "execution-1",
                )
            },
        )
        val after = useCase(
            request(plan = done, profile = profile(experience = Experience.INTERMEDIATE)),
        )

        assertThat(after.id).isEqualTo(first.id)
        assertThat(after.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(after.ruleIds).contains("R-PRESC-04")
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `a closed day keeps the persisted prescription without applying the guard again`(): Unit = runBlocking {
        val plan = qualityPlan()
        val first = useCase(
            request(plan = plan, profile = profile(experience = Experience.BEGINNER)),
        )

        val closed = plan.copy(
            sessions = plan.sessions.map {
                it.copy(
                    status = SessionStatus.CLOSED,
                    closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
                    closedAt = Instant.EPOCH,
                )
            },
        )
        val after = useCase(
            request(plan = closed, profile = profile(experience = Experience.INTERMEDIATE)),
        )

        assertThat(after.id).isEqualTo(first.id)
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `a skipped day keeps the persisted prescription instead of becoming no plan`(): Unit = runBlocking {
        val plan = qualityPlan()
        val first = useCase(
            request(plan = plan, profile = profile(experience = Experience.BEGINNER)),
        )

        val skipped = plan.copy(
            sessions = plan.sessions.map {
                it.copy(
                    status = SessionStatus.CLOSED,
                    closureReason = SessionClosureReason.USER_SKIPPED,
                    closedAt = Instant.EPOCH,
                )
            },
        )
        val after = useCase(
            request(plan = skipped, profile = profile(experience = Experience.INTERMEDIATE)),
        )

        assertThat(after.id).isEqualTo(first.id)
        assertThat(after.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `a finished day without a persisted prescription is not written retrospectively`(): Unit = runBlocking {
        val plan = qualityPlan().copy(
            sessions = qualityPlan().sessions.map {
                it.copy(
                    status = SessionStatus.DONE,
                    completionEvidence = CompletionEvidence.MANUAL,
                )
            },
        )

        val prescription = useCase(
            request(plan = plan, profile = profile(experience = Experience.BEGINNER)),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.ruleIds).doesNotContain("R-PRESC-04")
        assertThat(repository.getVersions(today)).isEmpty()
    }

    private fun request(
        plan: WeeklyPlan?,
        readiness: ReadinessResult = readiness(ReadinessLevel.GREEN),
        profile: AthleteProfile = profile(),
    ) = TodayPrescriptionRequest(
        date = today,
        profile = profile,
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

    private fun qualityPlan() = WeeklyPlan(
        weekStart = today,
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.QUALITY,
                title = "Tempo",
                durationMin = 40,
                rationale = "Qualidade planejada.",
                ruleIds = listOf("R-PLAN-02", "R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                targets = PrescriptionTargets(zone = "Z3–Z4", rpe = 7),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana com qualidade.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02", "R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun profile(experience: Experience = Experience.BEGINNER) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = experience,
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
