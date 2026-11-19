package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.domain.coach.CoachGuardKind
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
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
class CoachGuardsSourceTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var signals: TrainingSignalSource
    private lateinit var source: CoachGuardsSource

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC)
        profiles = ProfileRepository(database, clock)
        val checkIns = CheckInRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneOffset.UTC,
            idGenerator = { "activity-${System.nanoTime()}" },
        )
        val executions = SessionExecutionRepository(database, clock)
        signals = TrainingSignalSource(checkIns, metrics, activities, executions)
        raceEvents = RaceEventRepository(database, clock)
        source = RoomCoachGuardsSource(
            profiles = profiles,
            raceEvents = raceEvents,
            signals = signals,
            time = FakeTimeProvider(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `sem perfil nao ha guardas`(): Unit = runBlocking {
        val context = source.observeGuards().first()

        assertThat(context.guards).isEmpty()
    }

    @Test
    fun `sem dados a calibracao aparece`(): Unit = runBlocking {
        profiles.saveProfile(profile())

        val context = source.observeGuards().first { it.guards.isNotEmpty() }

        assertThat(context.guards.map { it.kind }).containsExactly(
            CoachGuardKind.CALIBRATION,
            CoachGuardKind.NO_AVAILABILITY,
        )
    }

    @Test
    fun `janela pos-prova aparece`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val created = raceEvents.create(NewRaceEvent(RaceDistance.FIVE_K, LocalDate.of(2026, 11, 17)))
        val id = (created as RaceEventOutcome.Saved).event.id
        raceEvents.complete(id)

        val context = source.observeGuards().first { guards ->
            guards.guards.any { it.kind == CoachGuardKind.RECOVERY }
        }

        assertThat(context.guards.map { it.kind }).contains(CoachGuardKind.CALIBRATION)
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        timezone = "UTC",
    )
}
