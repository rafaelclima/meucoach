package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.HeatLevel
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CheckInRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: CheckInRepository
    private val date = LocalDate.of(2026, 10, 30)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        clock = MutableClock(Instant.parse("2026-10-30T12:00:00Z"))
        repository = CheckInRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves and reads a check-in by date`(): Unit = runBlocking {
        repository.save(checkIn())

        assertThat(repository.get(date)).isEqualTo(checkIn())
        assertThat(repository.observe(date).first()).isEqualTo(checkIn())
        assertThat(repository.observeAll().first()).containsExactly(checkIn())
        assertThat(database.dailyCheckInDao().get(date.toString())?.createdAt).isEqualTo(clock.millis())
    }

    @Test
    fun `reads the full history ordered by date`(): Unit = runBlocking {
        repository.save(checkIn().copy(date = date.plusDays(1)))
        repository.save(checkIn())

        assertThat(repository.getAll()).containsExactly(
            checkIn(),
            checkIn().copy(date = date.plusDays(1)),
        )
    }

    @Test
    fun `updates the same date and preserves createdAt`(): Unit = runBlocking {
        repository.save(checkIn())
        clock.instant = Instant.parse("2026-10-30T18:00:00Z")

        repository.save(checkIn().copy(fatigue = 4))

        val entity = database.dailyCheckInDao().get(date.toString())
        assertThat(entity?.fatigue).isEqualTo(4)
        assertThat(entity?.createdAt).isEqualTo(Instant.parse("2026-10-30T12:00:00Z").toEpochMilli())
        assertThat(entity?.updatedAt).isEqualTo(Instant.parse("2026-10-30T18:00:00Z").toEpochMilli())
    }

    @Test
    fun `deletes a check-in`(): Unit = runBlocking {
        repository.save(checkIn())

        assertThat(repository.delete(date)).isTrue()
        assertThat(repository.get(date)).isNull()
        assertThat(repository.delete(date)).isFalse()
    }

    @Test
    fun `round-trips the self reported heat level`(): Unit = runBlocking {
        repository.save(checkIn().copy(heatLevel = HeatLevel.HOT))

        assertThat(repository.get(date)?.heatLevel).isEqualTo(HeatLevel.HOT)
        assertThat(database.dailyCheckInDao().get(date.toString())?.heatLevel).isEqualTo(HeatLevel.HOT)
    }

    @Test
    fun `starts empty`(): Unit = runBlocking {
        assertThat(repository.get(date)).isNull()
        assertThat(repository.observeAll().first()).isEmpty()
    }

    private fun checkIn() = DailyCheckIn(
        date = date,
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
