package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
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
class DailyMetricsRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: DailyMetricsRepository
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
        repository = DailyMetricsRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves a batch and reads it in date order`(): Unit = runBlocking {
        repository.saveAll(
            listOf(
                metrics(date = LocalDate.of(2026, 10, 30)),
                metrics(date = LocalDate.of(2026, 10, 29), hrv = 55.0),
            ),
        )

        assertThat(repository.getAll()).containsExactly(
            metrics(date = LocalDate.of(2026, 10, 29), hrv = 55.0),
            metrics(date = LocalDate.of(2026, 10, 30)),
        )
        assertThat(repository.observeAll().first()).hasSize(2)
        assertThat(repository.get(date)).isEqualTo(metrics(date = date))
    }

    @Test
    fun `updates a date and preserves createdAt`(): Unit = runBlocking {
        repository.saveAll(listOf(metrics(date = date)))
        clock.instant = Instant.parse("2026-10-31T12:00:00Z")

        repository.saveAll(listOf(metrics(date = date, hrv = 65.0)))

        val entity = database.dailyMetricsDao().get(date.toString())
        assertThat(entity?.hrvRmssdMs).isEqualTo(65.0)
        assertThat(entity?.createdAt).isEqualTo(Instant.parse("2026-10-30T12:00:00Z").toEpochMilli())
        assertThat(entity?.updatedAt).isEqualTo(Instant.parse("2026-10-31T12:00:00Z").toEpochMilli())
    }

    @Test
    fun `merges a health connect day without erasing csv fields`(): Unit = runBlocking {
        repository.saveAll(
            listOf(
                metrics(
                    date = date,
                    sleep = null,
                    restingHeartRate = null,
                    hrv = 55.0,
                    spo2 = 97.0,
                    source = DailyMetricsSource.CSV,
                ),
            ),
        )
        clock.instant = Instant.parse("2026-10-31T12:00:00Z")

        repository.saveAll(
            listOf(
                metrics(
                    date = date,
                    sleep = 460,
                    restingHeartRate = 58.0,
                    hrv = null,
                    spo2 = null,
                    source = DailyMetricsSource.HEALTH_CONNECT,
                ),
            ),
        )

        val merged = checkNotNull(repository.get(date))
        assertThat(merged.sleepDurationMin).isEqualTo(460)
        assertThat(merged.restingHeartRate).isEqualTo(58.0)
        assertThat(merged.hrvRmssdMs).isEqualTo(55.0)
        assertThat(merged.spo2Avg).isEqualTo(97.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `does not let a csv import overwrite health connect values`(): Unit = runBlocking {
        repository.saveAll(
            listOf(
                metrics(
                    date = date,
                    sleep = 460,
                    restingHeartRate = null,
                    hrv = 60.0,
                    spo2 = null,
                    source = DailyMetricsSource.HEALTH_CONNECT,
                ),
            ),
        )

        repository.saveAll(
            listOf(
                metrics(
                    date = date,
                    sleep = 400,
                    restingHeartRate = null,
                    hrv = 50.0,
                    spo2 = 98.0,
                    source = DailyMetricsSource.CSV,
                ),
            ),
        )

        val merged = checkNotNull(repository.get(date))
        assertThat(merged.sleepDurationMin).isEqualTo(460)
        assertThat(merged.hrvRmssdMs).isEqualTo(60.0)
        assertThat(merged.spo2Avg).isEqualTo(98.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `deletes a date`(): Unit = runBlocking {
        repository.saveAll(listOf(metrics(date = date)))

        assertThat(repository.delete(date)).isTrue()
        assertThat(repository.get(date)).isNull()
        assertThat(repository.delete(date)).isFalse()
    }

    private fun metrics(
        date: LocalDate,
        sleep: Int? = 450,
        restingHeartRate: Double? = 58.0,
        hrv: Double? = 60.0,
        spo2: Double? = 97.0,
        source: DailyMetricsSource = DailyMetricsSource.CSV,
    ) = DailyMetrics(
        date = date,
        sleepDurationMin = sleep,
        restingHeartRate = restingHeartRate,
        hrvRmssdMs = hrv,
        spo2Avg = spo2,
        source = source,
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
