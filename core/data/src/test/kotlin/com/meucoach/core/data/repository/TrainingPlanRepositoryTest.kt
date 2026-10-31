package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainingPlanRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: TrainingPlanRepository
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = TrainingPlanRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves and reads a week`(): Unit = runBlocking {
        repository.saveWeek(plan())

        assertThat(repository.getWeek(monday)).isEqualTo(plan())
        assertThat(repository.observeWeek(monday).first()).isEqualTo(plan())
    }

    @Test
    fun `replaces sessions when the week is saved again`(): Unit = runBlocking {
        repository.saveWeek(plan())
        val updated = plan().copy(
            sessions = plan().sessions.take(1).map { it.copy(durationMin = 30) },
            weeklyMinutes = 30,
            longRunMinutes = null,
        )

        repository.saveWeek(updated)

        val stored = repository.getWeek(monday)
        assertThat(stored?.sessions).hasSize(1)
        assertThat(stored?.sessions?.single()?.durationMin).isEqualTo(30)
        assertThat(stored?.weeklyMinutes).isEqualTo(30)
    }

    @Test
    fun `updates session status and skip reason`(): Unit = runBlocking {
        repository.saveWeek(plan())

        assertThat(repository.updateStatus(monday, monday, SessionStatus.DONE)).isTrue()
        assertThat(repository.getWeek(monday)?.sessions?.first()?.status).isEqualTo(SessionStatus.DONE)

        assertThat(
            repository.updateStatus(
                monday,
                monday.plusDays(5),
                SessionStatus.SKIPPED,
                skipReason = "dor no joelho",
            ),
        ).isTrue()
        val skipped = repository.getWeek(monday)?.sessions?.last()
        assertThat(skipped?.status).isEqualTo(SessionStatus.SKIPPED)
        assertThat(skipped?.skipReason).isEqualTo("dor no joelho")
    }

    @Test
    fun `deletes the week and its sessions`(): Unit = runBlocking {
        repository.saveWeek(plan())

        assertThat(repository.deleteWeek(monday)).isEqualTo(1)
        assertThat(repository.getWeek(monday)).isNull()
        assertThat(database.trainingPlanDao().getSessions(monday.toString())).isEmpty()
    }

    @Test
    fun `counts only consecutive weeks before the target week`(): Unit = runBlocking {
        repository.saveWeek(plan(monday.minusWeeks(1)))
        repository.saveWeek(plan(monday.minusWeeks(2)))
        repository.saveWeek(plan(monday.minusWeeks(4)))

        assertThat(repository.consecutiveWeeksBefore(monday)).isEqualTo(2)
    }

    @Test
    fun `loads multiple weeks and skips missing ones`(): Unit = runBlocking {
        repository.saveWeek(plan(monday.minusWeeks(1)))
        repository.saveWeek(plan(monday))

        val weeks = repository.getWeeks(
            listOf(monday.minusWeeks(2), monday.minusWeeks(1), monday),
        )

        assertThat(weeks.map { it.weekStart })
            .containsExactly(monday.minusWeeks(1), monday)
    }

    @Test
    fun `starts empty`(): Unit = runBlocking {
        assertThat(repository.getWeek(monday)).isNull()
        assertThat(repository.observeWeek(monday).first()).isNull()
        assertThat(repository.consecutiveWeeksBefore(monday)).isZero()
    }

    private fun plan(weekStart: LocalDate = monday) = WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            PlannedSession(
                date = weekStart,
                type = WorkoutType.EASY,
                title = "Fácil em Z2",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01", "R-PLAN-06"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
            PlannedSession(
                date = weekStart.plusDays(5),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Longo.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
            ),
        ),
        weeklyMinutes = 100,
        longRunMinutes = 60,
        qualityCount = 0,
        rationale = "Sem progressão até existir histórico.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )
}
