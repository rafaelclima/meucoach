package com.meucoach.app.ui.metrics

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
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthConnectViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var syncStore: HealthSyncStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
        metrics = DailyMetricsRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        syncStore = HealthSyncStore(context, prefsName = "health-view-model-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `reports missing permissions after refresh`(): Unit = runBlocking {
        val source = FakeSource(granted = emptySet())
        val state = HealthConnectViewModel(source, service(source)).uiState.first { !it.isLoading }

        assertThat(state.status).isEqualTo(HealthConnectStatus.AVAILABLE)
        assertThat(state.hasPermissions).isFalse()
        assertThat(state.missingPermissions).containsExactly("sleep", "hr")
    }

    @Test
    fun `syncs and saves daily metrics`(): Unit = runBlocking {
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = listOf(
                    metrics(LocalDate.now().minusDays(1)),
                    metrics(LocalDate.now()),
                ),
                missingPermissions = emptySet(),
            ),
        )
        val viewModel = HealthConnectViewModel(source, service(source))
        viewModel.uiState.first { !it.isLoading }

        viewModel.sync()
        withTimeout(5_000) { viewModel.uiState.first { !it.isSyncing && it.message != null } }

        assertThat(viewModel.uiState.value.importedDays).isEqualTo(2)
        assertThat(viewModel.uiState.value.message).contains("2 dias importados")
        assertThat(metrics.getAll()).hasSize(2)
    }

    @Test
    fun `does not sync without permissions`(): Unit = runBlocking {
        val source = FakeSource(
            granted = emptySet(),
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = setOf("sleep", "hr"),
            ),
        )
        val viewModel = HealthConnectViewModel(source, service(source))
        viewModel.uiState.first { !it.isLoading }

        viewModel.sync()
        withTimeout(5_000) { viewModel.uiState.first { it.message != null } }

        assertThat(viewModel.uiState.value.message).contains("Autorize")
        assertThat(metrics.getAll()).isEmpty()
    }

    @Test
    fun `reports an unavailable client`(): Unit = runBlocking {
        val source = FakeSource(status = HealthConnectStatus.UNAVAILABLE)
        val viewModel = HealthConnectViewModel(source, service(source))
        viewModel.uiState.first { !it.isLoading }

        viewModel.sync()
        withTimeout(5_000) { viewModel.uiState.first { it.message != null } }

        assertThat(viewModel.uiState.value.message).contains("indisponível")
    }

    @Test
    fun `reports a source error`(): Unit = runBlocking {
        val source = FakeSource(
            result = HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
                error = "Falha de leitura.",
            ),
        )
        val viewModel = HealthConnectViewModel(source, service(source))
        viewModel.uiState.first { !it.isLoading }

        viewModel.sync()
        withTimeout(5_000) { viewModel.uiState.first { it.message != null } }

        assertThat(viewModel.uiState.value.message).isEqualTo("Falha de leitura.")
    }

    private fun service(source: HealthDailySource) = HealthSyncService(
        source = source,
        metricsRepository = metrics,
        profileRepository = profiles,
        syncStore = syncStore,
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
        override val requiredPermissions: Set<String> = setOf("sleep", "hr")

        override suspend fun status(): HealthConnectStatus = status

        override suspend fun grantedPermissions(): Set<String> = granted

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult = result
    }
}
