package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.replan.PlanningFingerprints
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.Experience
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EvaluateReplanTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var evaluate: EvaluateReplan
    private lateinit var contexts: ReplanContextBuilder
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
        plans = TrainingPlanRepository(database, clock)
        contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            raceEvents = RaceEventRepository(database, clock),
            timeProvider = timeProvider,
        )
        evaluate = EvaluateReplan(contexts, plans)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `two missed sessions suggest a revision and preserve today and the past`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan())
        val before = plans.getWeek(REPLAN_WEEK_START)!!

        val result = evaluate(explicit = false)

        assertThat(result).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        val suggestion = result as EvaluateReplanResult.Suggestion
        assertThat(suggestion.suggestion.trigger).isEqualTo(ReplanTrigger.MISSED_SESSIONS)
        assertThat(suggestion.suggestion.missedTrainableCount).isEqualTo(2)
        assertThat(suggestion.diff).hasSize(1)
        assertThat(suggestion.diff.single().date).isEqualTo(REPLAN_WEEK_START.plusDays(4))
        assertThat(suggestion.diff.single().kind).isEqualTo(ReplanChangeKind.MODIFIED)

        val untouched = before.sessions.filter { !it.date.isAfter(REPLAN_TODAY) }
        assertThat(suggestion.candidate.sessions.filter { !it.date.isAfter(REPLAN_TODAY) })
            .isEqualTo(untouched)
        assertThat(plans.getWeek(REPLAN_WEEK_START)).isEqualTo(before)
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(1)
    }

    @Test
    fun `derives today and week start from one instant and the persisted profile timezone`(): Unit = runBlocking {
        val instant = Instant.parse("2026-11-09T02:00:00Z")
        val utcProvider = FakeTimeProvider(instant, ZoneOffset.UTC)
        val localContexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            metrics = DailyMetricsRepository(database, clock),
            activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC),
            raceEvents = RaceEventRepository(database, clock),
            timeProvider = utcProvider,
        )
        val profile = replanProfile().copy(timezone = "America/Sao_Paulo")
        profiles.saveProfile(profile)
        checkIns.save(replanCheckIn(date = REPLAN_WEEK_START.plusDays(6)))
        plans.saveWeek(replanPlan(createdOn = REPLAN_WEEK_START))

        val context = localContexts.build(explicit = false)!!

        assertThat(context.today).isEqualTo(LocalDate.of(2026, 11, 8))
        assertThat(context.record.plan.weekStart).isEqualTo(REPLAN_WEEK_START)
        assertThat(context.input.temporalContextHash)
            .isEqualTo(PlanningFingerprints.temporalContextHash(profile))
        assertThat(context.input.today).isEqualTo(context.today)
    }

    @Test
    fun `captures the availability baseline without infirming a change`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan(createdOn = REPLAN_TODAY))
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.availabilityHash).isNull()

        val result = evaluate(explicit = false)

        assertThat(result).isInstanceOf(EvaluateReplanResult.NoChange::class.java)
        val stored = plans.getWeekRecord(REPLAN_WEEK_START)!!
        assertThat(stored.availabilityHash).isNotNull()
        assertThat(stored.version).isEqualTo(1)
        assertThat(stored.generatedFromFingerprint).isNull()
        assertThat(stored.lastEvaluatedFingerprint)
            .isEqualTo(PlanningFingerprints.of(replanProfile()).hash)
        assertThat(stored.temporalContextHash)
            .isEqualTo(PlanningFingerprints.temporalContextHash(replanProfile()))
    }

    @Test
    fun `a changed profile triggers T4 without claiming the old plan was generated by it`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(
            replanPlan(
                createdOn = REPLAN_TODAY,
                sessions = listOf(
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        // First evaluation captures the checkpoint for the original profile.
        evaluate(explicit = false)
        val before = plans.getWeekRecord(REPLAN_WEEK_START)!!
        assertThat(before.generatedFromFingerprint).isNull()

        profiles.saveProfile(replanProfile().copy(experience = Experience.BEGINNER))
        val result = evaluate(explicit = false)

        assertThat(result).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        val suggestion = result as EvaluateReplanResult.Suggestion
        assertThat(suggestion.suggestion.causes).contains(ReplanCause.PROFILE_CHANGED)
        assertThat(suggestion.suggestion.ruleIds).contains("R-PLAN-09")
        assertThat(suggestion.candidate.qualityCount).isZero()
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(1)
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.generatedFromFingerprint).isNull()
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.lastEvaluatedFingerprint)
            .isEqualTo(PlanningFingerprints.of(replanProfile()).hash)
    }

    @Test
    fun `a plan with no deviation only reappears when the user asks`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(
            replanPlan(
                createdOn = REPLAN_TODAY,
                sessions = listOf(
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        plans.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(replanProfile().weeklyAvailability),
        )

        val automatic = evaluate(explicit = false)
        val explicit = evaluate(explicit = true)

        assertThat(automatic).isInstanceOf(EvaluateReplanResult.NoChange::class.java)
        assertThat(explicit).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        val suggestion = explicit as EvaluateReplanResult.Suggestion
        assertThat(suggestion.suggestion.trigger).isEqualTo(ReplanTrigger.USER_REQUEST)
        assertThat(suggestion.suggestion.missedTrainableCount).isZero()
    }

    @Test
    fun `an illness check-in never counts as a missed session`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn(REPLAN_WEEK_START, illness = true))
        checkIns.save(replanCheckIn())
        plans.saveWeek(
            replanPlan(
                sessions = listOf(
                    easySession(REPLAN_WEEK_START),
                    easySession(REPLAN_WEEK_START.plusDays(1), durationMin = 35),
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        plans.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(replanProfile().weeklyAvailability),
        )

        val automatic = evaluate(explicit = false)
        val explicit = evaluate(explicit = true)

        assertThat(automatic).isInstanceOf(EvaluateReplanResult.NoChange::class.java)
        assertThat(explicit).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        assertThat((explicit as EvaluateReplanResult.Suggestion).suggestion.missedTrainableCount)
            .isEqualTo(1)
    }

    @Test
    fun `a changed availability triggers a revision without missed sessions`(): Unit = runBlocking {
        val oldProfile = replanProfile(fridayMinutes = 45)
        profiles.saveProfile(oldProfile)
        checkIns.save(replanCheckIn())
        plans.saveWeek(
            replanPlan(
                createdOn = REPLAN_TODAY,
                sessions = listOf(
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        plans.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(oldProfile.weeklyAvailability),
        )
        profiles.saveProfile(replanProfile(fridayMinutes = 30))

        val result = evaluate(explicit = false)

        assertThat(result).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        val suggestion = result as EvaluateReplanResult.Suggestion
        assertThat(suggestion.suggestion.trigger).isEqualTo(ReplanTrigger.AVAILABILITY_CHANGED)
        assertThat(suggestion.suggestion.missedTrainableCount).isZero()
    }

    @Test
    fun `the same input produces the same suggestion and inputs hash`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan())
        val first = evaluate(explicit = true) as EvaluateReplanResult.Suggestion

        plans.saveWeek(replanPlan())
        val second = evaluate(explicit = true) as EvaluateReplanResult.Suggestion

        assertThat(second.suggestion.expectedInputsHash)
            .isEqualTo(first.suggestion.expectedInputsHash)
        assertThat(second.candidate).isEqualTo(first.candidate)
        assertThat(second.diff).isEqualTo(first.diff)
    }

    @Test
    fun `reports an unavailable week when only a past week exists`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        val otherWeek = REPLAN_WEEK_START.minusWeeks(1)
        plans.saveWeek(
            replanPlan(
                weekStart = otherWeek,
                createdOn = otherWeek,
                sessions = listOf(
                    easySession(otherWeek, durationMin = 30),
                    qualitySession(otherWeek.plusDays(4)),
                ),
            ),
        )

        val result = evaluate(explicit = true)

        assertThat(result).isInstanceOf(EvaluateReplanResult.Unavailable::class.java)
        assertThat((result as EvaluateReplanResult.Unavailable).reason)
            .contains("semana planejada")
    }
}
