package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.domain.today.TodayDecision
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.RaceDistance
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
class GetTodayDecisionTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var useCase: GetTodayDecision
    private val today = LocalDate.of(2026, 11, 4)
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prescriptions = DailyPrescriptionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        useCase = GetTodayDecision(raceEvents, GetTodayPrescription(prescriptions))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a race event wins and no prescription is persisted`(): Unit = runBlocking {
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, today, targetTimeSec = 3_000))

        val decision = useCase(request(date = today, profile = replanProfile()))

        assertThat(decision).isInstanceOf(TodayDecision.Competition::class.java)
        assertThat((decision as TodayDecision.Competition).event.distance).isEqualTo(RaceDistance.TEN_K)
        assertThat(prescriptions.getLatest(today)).isNull()
    }

    @Test
    fun `without an event the day is a persisted training prescription`(): Unit = runBlocking {
        val decision = useCase(request(date = today, profile = replanProfile()))

        assertThat(decision).isInstanceOf(TodayDecision.Training::class.java)
        val prescription = (decision as TodayDecision.Training).prescription
        assertThat(prescription.date).isEqualTo(today)
        assertThat(prescriptions.getLatest(today)?.id).isEqualTo(prescription.id)
    }

    @Test
    fun `there is no decision without a profile or an event`(): Unit = runBlocking {
        val decision = useCase(request(date = today, profile = null))

        assertThat(decision).isNull()
    }

    @Test
    fun `an event on another date does not hijack today`(): Unit = runBlocking {
        raceEvents.create(NewRaceEvent(RaceDistance.FIVE_K, today.plusDays(1)))

        val todayDecision = useCase(request(date = today, profile = replanProfile()))
        val tomorrowDecision = useCase(request(date = today.plusDays(1), profile = replanProfile()))

        assertThat(todayDecision).isInstanceOf(TodayDecision.Training::class.java)
        assertThat(tomorrowDecision).isInstanceOf(TodayDecision.Competition::class.java)
    }

    @Test
    fun `cancelled or no-show events still own the day without starting recovery`(): Unit = runBlocking {
        val event = raceEvents.create(NewRaceEvent(RaceDistance.HALF_MARATHON, today))
        val saved = (event as com.meucoach.core.data.repository.RaceEventOutcome.Saved).event
        raceEvents.cancel(saved.id)

        val decision = useCase(request(date = today, profile = replanProfile()))

        assertThat(decision).isInstanceOf(TodayDecision.Competition::class.java)
        assertThat((decision as TodayDecision.Competition).event.status)
            .isEqualTo(com.meucoach.core.model.RaceStatus.CANCELLED)
        assertThat(prescriptions.getLatest(today)).isNull()
    }

    private fun request(date: LocalDate, profile: com.meucoach.core.model.AthleteProfile?) = TodayDecisionRequest(
        date = date,
        profile = profile,
        readiness = readinessResult(
            DailyCheckIn(date, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
            emptyList(),
            date,
        ),
    )
}
