package com.meucoach.app.ui.metrics

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyMetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetricsImportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: DailyMetricsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = DailyMetricsRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `imports valid rows and reports duplicates`() {
        val viewModel = MetricsImportViewModel(context, repository)
        viewModel.import(
            file(
                """
                date,sleep_minutes,resting_heart_rate
                2026-10-29,450,58
                2026-10-29,480,59
                2026-10-30,420,57
                """.trimIndent(),
            ),
        )
        val state = awaitIdle(viewModel)

        assertThat(state.importedDays).isEqualTo(2)
        assertThat(state.duplicateDates).isEqualTo(1)
        assertThat(state.message).contains("2 dias importados")
    }

    @Test
    fun `keeps valid rows when some are invalid`() {
        val viewModel = MetricsImportViewModel(context, repository)
        viewModel.import(
            file(
                """
                date,sleep_minutes
                2026-10-29,450
                bad-date,420
                """.trimIndent(),
            ),
        )
        val state = awaitIdle(viewModel)

        assertThat(state.importedDays).isEqualTo(1)
        assertThat(state.errors).hasSize(1)
        assertThat(state.errors.single().line).isEqualTo(3)
    }

    @Test
    fun `reports a missing date column`() {
        val viewModel = MetricsImportViewModel(context, repository)
        viewModel.import(file("sleep_minutes\n450"))

        val state = awaitIdle(viewModel)

        assertThat(state.importedDays).isZero()
        assertThat(state.message).contains("data")
    }

    @Test
    fun `reports an unreadable file`() {
        val viewModel = MetricsImportViewModel(context, repository)
        viewModel.import(Uri.fromFile(File(context.cacheDir, "missing.csv")))

        val state = awaitIdle(viewModel)

        assertThat(state.importedDays).isZero()
        assertThat(state.message).isNotNull
    }

    private fun awaitIdle(viewModel: MetricsImportViewModel): MetricsImportUiState = kotlinx.coroutines.runBlocking {
        withTimeout(5_000) {
            viewModel.uiState.first { !it.isImporting }
        }
    }

    private fun file(content: String): Uri {
        val file = File(context.cacheDir, "metrics-${System.nanoTime()}.csv")
        file.writeText(content)
        return Uri.fromFile(file)
    }
}
