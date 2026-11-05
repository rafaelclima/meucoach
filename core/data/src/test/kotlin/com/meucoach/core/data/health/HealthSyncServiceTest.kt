package com.meucoach.core.data.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.Sex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthSyncServiceTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var store: HealthSyncStore
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.now(zone)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
        metrics = DailyMetricsRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        store = HealthSyncStore(context, prefsName = "health-sync-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `first sync reads thirty days and marks the date`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = listOf(metrics(today.minusDays(1)), metrics(today)),
                missingPermissions = emptySet(),
            ),
        )
        val result = service(source).sync()

        assertThat(result.incremental).isFalse()
        assertThat(result.from).isEqualTo(today.minusDays(29))
        assertThat(source.lastFrom).isEqualTo(today.minusDays(29))
        assertThat(source.lastTo).isEqualTo(today)
        assertThat(metrics.getAll()).hasSize(2)
        assertThat(store.lastSyncedDate()).isEqualTo(today)
    }

    @Test
    fun `keeps csv-only fields when health connect covers the same day`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        metrics.saveAll(
            listOf(
                DailyMetrics(
                    date = today,
                    hrvRmssdMs = 55.0,
                    spo2Avg = 98.0,
                    source = DailyMetricsSource.CSV,
                ),
            ),
        )
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = listOf(
                    DailyMetrics(
                        date = today,
                        sleepDurationMin = 450,
                        restingHeartRate = 58.0,
                        source = DailyMetricsSource.HEALTH_CONNECT,
                    ),
                ),
                missingPermissions = emptySet(),
            ),
        )

        service(source).sync()

        val merged = checkNotNull(metrics.get(today))
        assertThat(merged.sleepDurationMin).isEqualTo(450)
        assertThat(merged.restingHeartRate).isEqualTo(58.0)
        assertThat(merged.hrvRmssdMs).isEqualTo(55.0)
        assertThat(merged.spo2Avg).isEqualTo(98.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `later sync starts one day before the last success`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        store.markSynced(Instant.parse("2026-10-30T12:00:00Z"), today)
        val source = FakeSource()

        val result = service(source).sync()

        assertThat(result.incremental).isTrue()
        assertThat(source.lastFrom).isEqualTo(today.minusDays(1))
        assertThat(source.lastTo).isEqualTo(today)
    }

    @Test
    fun `does not mark the date without permissions`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = setOf("sleep"),
            ),
        )

        val result = service(source).sync()

        assertThat(result.missingPermissions).contains("sleep")
        assertThat(store.lastSyncedDate()).isNull()
    }

    @Test
    fun `does not mark the date on a read error`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = "Falha de leitura.",
            ),
        )

        val result = service(source).sync()

        assertThat(result.error).isEqualTo("Falha de leitura.")
        assertThat(store.lastSyncedDate()).isNull()
    }

    @Test
    fun `reports an unavailable client without marking the date`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(status = HealthConnectStatus.UNAVAILABLE)

        val result = service(source).sync()

        assertThat(result.error).contains("indisponível")
        assertThat(store.lastSyncedDate()).isNull()
    }

    @Test
    fun `skips a fresh success on resume`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        store.markSynced(now.minus(Duration.ofHours(1)), LocalDate.of(2026, 10, 30))
        val source = FakeSource()

        val result = service(source, Clock.fixed(now, ZoneOffset.UTC)).syncIfStale()

        assertThat(result).isNull()
        assertThat(source.reads).isZero()
    }

    @Test
    fun `reads when the last success is older than six hours`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val today = LocalDate.of(2026, 10, 30)
        store.markSynced(now.minus(Duration.ofHours(7)), today)
        val source = FakeSource()

        val result = service(source, Clock.fixed(now, ZoneOffset.UTC)).syncIfStale()

        assertThat(result).isNotNull
        assertThat(source.lastFrom).isEqualTo(today.minusDays(1))
        assertThat(source.reads).isEqualTo(1)
        assertThat(store.lastSyncedAt()).isEqualTo(now)
    }

    @Test
    fun `backs off after a read error`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = "Falha de leitura.",
            ),
        )

        val first = service(source, clock).syncIfStale()
        val second = service(source, clock).syncIfStale()

        assertThat(first?.error).isEqualTo("Falha de leitura.")
        assertThat(second).isNull()
        assertThat(source.reads).isEqualTo(1)
        assertThat(store.lastAttemptAt()).isEqualTo(now)
    }

    @Test
    fun `retries after the error backoff`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = "Falha de leitura.",
            ),
        )
        service(source, Clock.fixed(now, ZoneOffset.UTC)).syncIfStale()

        val later = service(source, Clock.fixed(now.plus(Duration.ofMinutes(16)), ZoneOffset.UTC)).syncIfStale()

        assertThat(later?.error).isEqualTo("Falha de leitura.")
        assertThat(source.reads).isEqualTo(2)
    }

    @Test
    fun `manual sync ignores a fresh success`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        store.markSynced(now, LocalDate.of(2026, 10, 30))
        val source = FakeSource()

        service(source, Clock.fixed(now, ZoneOffset.UTC)).sync()

        assertThat(source.reads).isEqualTo(1)
    }

    @Test
    fun `missing permissions do not start an error backoff`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = setOf("sleep"),
            ),
        )

        val result = service(source, Clock.fixed(now, ZoneOffset.UTC)).syncIfStale()

        assertThat(result?.missingPermissions).contains("sleep")
        assertThat(store.lastAttemptAt()).isNull()
        assertThat(store.lastSyncedAt()).isNull()
    }

    @Test
    fun `concurrent resume calls read only once`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val source = FakeSource()
        val syncService = service(source, Clock.fixed(now, ZoneOffset.UTC))

        val results = coroutineScope {
            List(2) { async { syncService.syncIfStale() } }.awaitAll()
        }

        assertThat(results.count { it != null }).isEqualTo(1)
        assertThat(source.reads).isEqualTo(1)
    }

    private fun service(source: HealthDailySource, clock: Clock = Clock.systemDefaultZone()) = HealthSyncService(
        source = source,
        metricsRepository = metrics,
        profileRepository = profiles,
        syncStore = store,
        clock = clock,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "UTC",
    )

    private fun metrics(date: LocalDate) = DailyMetrics(
        date = date,
        sleepDurationMin = 450,
        restingHeartRate = 58.0,
        hrvRmssdMs = 60.0,
        spo2Avg = 97.0,
        source = DailyMetricsSource.HEALTH_CONNECT,
    )

    private class FakeSource(
        var status: HealthConnectStatus = HealthConnectStatus.AVAILABLE,
        var result: HealthConnectReadResult = HealthConnectReadResult(
            days = emptyList(),
            missingPermissions = emptySet(),
        ),
    ) : HealthDailySource {
        var lastFrom: LocalDate? = null
        var lastTo: LocalDate? = null
        var reads: Int = 0

        override val requiredPermissions: Set<String> = setOf("sleep", "hr")

        override suspend fun status(): HealthConnectStatus = status

        override suspend fun grantedPermissions(): Set<String> = requiredPermissions

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult {
            reads++
            lastFrom = from
            lastTo = to
            return result
        }
    }
}
