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
    fun `later sync starts one day before the last success`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        store.markSynced(today)
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

    private fun service(source: HealthDailySource) = HealthSyncService(
        source = source,
        metricsRepository = metrics,
        profileRepository = profiles,
        syncStore = store,
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

        override val requiredPermissions: Set<String> = setOf("sleep", "hr")

        override suspend fun status(): HealthConnectStatus = status

        override suspend fun grantedPermissions(): Set<String> = requiredPermissions

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult {
            lastFrom = from
            lastTo = to
            return result
        }
    }
}
