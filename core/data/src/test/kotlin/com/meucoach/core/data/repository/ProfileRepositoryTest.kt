package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.Units
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        clock = MutableClock(Instant.parse("2026-10-30T12:00:00Z"))
        repository = ProfileRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves and reads the full profile`(): Unit = runBlocking {
        repository.saveProfile(profile())

        val stored = repository.getProfile()

        assertThat(stored).isEqualTo(profile())
        assertThat(repository.observeProfile().first()).isEqualTo(profile())
        assertThat(database.profileDao().get()?.createdAt).isEqualTo(clock.millis())
        assertThat(database.profileDao().get()?.updatedAt).isEqualTo(clock.millis())
    }

    @Test
    fun `updates the singleton and preserves createdAt`(): Unit = runBlocking {
        repository.saveProfile(profile())
        clock.instant = Instant.parse("2026-11-01T12:00:00Z")

        repository.saveProfile(profile().copy(city = "Recife"))

        val entity = database.profileDao().get()
        assertThat(entity?.id).isEqualTo("athlete-1")
        assertThat(entity?.city).isEqualTo("Recife")
        assertThat(entity?.createdAt).isEqualTo(Instant.parse("2026-10-30T12:00:00Z").toEpochMilli())
        assertThat(entity?.updatedAt).isEqualTo(Instant.parse("2026-11-01T12:00:00Z").toEpochMilli())
        assertThat(repository.observeProfile().first()?.city).isEqualTo("Recife")
    }

    @Test
    fun `saving a different id still keeps a single profile`(): Unit = runBlocking {
        repository.saveProfile(profile())

        repository.saveProfile(profile().copy(id = "athlete-2"))

        assertThat(database.profileDao().get()?.id).isEqualTo("athlete-2")
        assertThat(database.profileDao().get()?.createdAt).isEqualTo(clock.millis())
    }

    @Test
    fun `deletes the profile`(): Unit = runBlocking {
        repository.saveProfile(profile())

        assertThat(repository.deleteProfile()).isEqualTo(1)
        assertThat(repository.getProfile()).isNull()
        assertThat(repository.observeProfile().first()).isNull()
    }

    @Test
    fun `starts empty`(): Unit = runBlocking {
        assertThat(repository.getProfile()).isNull()
        assertThat(repository.observeProfile().first()).isNull()
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(364),
        vdot = 31.0,
        experience = Experience.BEGINNER,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
        goal = TrainingGoal(
            type = GoalType.FIVE_K,
            targetDate = LocalDate.of(2027, 3, 14),
            targetDistanceM = 5_000.0,
            targetTimeSec = 1_800,
        ),
        city = "Natal",
        latitude = -5.79,
        longitude = -35.21,
        units = Units.METRIC,
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
