package com.meucoach.app.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthConnectReadResult
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.Sex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthSyncCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 5)
    private lateinit var database: MeuCoachDatabase
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var store: HealthSyncStore
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        metrics = DailyMetricsRepository(database)
        profiles = ProfileRepository(database)
        store = HealthSyncStore(context, prefsName = "health-coordinator-${System.nanoTime()}")
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `resume syncs when the last success is stale`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        store.markSynced(now.minus(Duration.ofHours(7)), today.minusDays(1))
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = listOf(metrics(today)),
                missingPermissions = emptySet(),
            ),
        )
        val coordinator = coordinator(source)

        coordinator.syncOnResume()
        withTimeout(5_000) { coordinator.state.first { it.lastSyncedAt == now } }

        assertThat(source.reads).isEqualTo(1)
        assertThat(metrics.getAll()).hasSize(1)
        assertThat(coordinator.state.value.isSyncing).isFalse()
    }

    @Test
    fun `resume does not read when the last success is fresh`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        store.markSynced(now.minus(Duration.ofHours(1)), today)
        val source = FakeSource()
        val coordinator = coordinator(source)

        coordinator.syncOnResume()
        withTimeout(5_000) { coordinator.state.first { !it.isSyncing } }

        assertThat(source.reads).isZero()
    }

    @Test
    fun `resume reports missing permission without reading`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(granted = emptySet())
        val coordinator = coordinator(source)

        coordinator.syncOnResume()
        withTimeout(5_000) { coordinator.state.first { it.needsPermission } }

        assertThat(source.reads).isZero()
        assertThat(coordinator.state.value.isSyncing).isFalse()
    }

    @Test
    fun `resume reports an unavailable provider`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(status = HealthConnectStatus.UNAVAILABLE)
        val coordinator = coordinator(source)

        coordinator.syncOnResume()
        withTimeout(5_000) { coordinator.state.first { it.unavailable } }

        assertThat(source.reads).isZero()
    }

    @Test
    fun `resume keeps a read error passive`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = "Falha de leitura.",
            ),
        )
        val coordinator = coordinator(source)

        coordinator.syncOnResume()
        withTimeout(5_000) { coordinator.state.first { it.lastError != null } }

        assertThat(coordinator.state.value.lastError).isEqualTo("Falha de leitura.")
    }

    @Test
    fun `manual sync returns the result and updates the state`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = listOf(metrics(today)),
                missingPermissions = emptySet(),
            ),
        )
        val coordinator = coordinator(source)

        val result = coordinator.syncNow()

        assertThat(result.importedDays).isEqualTo(1)
        assertThat(coordinator.state.value.lastSyncedAt).isEqualTo(now)
        assertThat(coordinator.state.value.isSyncing).isFalse()
    }

    @Test
    fun `manual sync bypasses a fresh success`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        store.markSynced(now, today)
        val source = FakeSource()
        val coordinator = coordinator(source)

        coordinator.syncNow()

        assertThat(source.reads).isEqualTo(1)
    }

    private fun coordinator(source: HealthDailySource): HealthSyncCoordinator {
        val service = HealthSyncService(
            source = source,
            metricsRepository = metrics,
            profileRepository = profiles,
            syncStore = store,
            clock = clock,
        )
        return HealthSyncCoordinator(source, service, store, scope)
    }

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
        var granted: Set<String> = setOf("sleep", "hr"),
        var result: HealthConnectReadResult = HealthConnectReadResult(
            days = emptyList(),
            missingPermissions = emptySet(),
        ),
    ) : HealthDailySource {
        var reads: Int = 0

        override val requiredPermissions: Set<String> = setOf("sleep", "hr")

        override suspend fun status(): HealthConnectStatus = status

        override suspend fun grantedPermissions(): Set<String> = granted

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult {
            reads++
            return result
        }
    }
}
