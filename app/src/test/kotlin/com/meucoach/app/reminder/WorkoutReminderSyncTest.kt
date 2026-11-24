package com.meucoach.app.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutReminderSyncTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var races: RaceEventRepository
    private lateinit var notifier: ReminderNotifier
    private val today = LocalDate.of(2026, 11, 4)
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        prescriptions = DailyPrescriptionRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        races = RaceEventRepository(database, clock)
        notifier = ReminderNotifier(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rewrites an active workout reminder when the day is adjusted`(): Unit = runBlocking {
        notifier.notify(
            ReminderMessage(
                id = ReminderContent.WORKOUT_ID,
                kind = ReminderKind.WORKOUT,
                title = "Treino de hoje: Intervalado",
                body = "40 min · Plano forte",
                deepLink = DEEP_LINK_TODAY,
            ),
        )
        val adjusted = prescription(
            plannedType = WorkoutType.QUALITY,
            plannedDurationMin = 40,
            prescribedType = WorkoutType.EASY,
            title = "Fácil — prontidão moderada",
            prescribedDurationMin = 24,
            adjustment = PrescriptionAdjustment.DOWNGRADE,
            rationale = "Planejado: Intervalado (40 min). Hoje: fácil, 24 min.",
        )
        prescriptions.append(adjusted)

        val scope = CoroutineScope(Dispatchers.Default)
        WorkoutReminderSync(
            prescriptions = prescriptions,
            plans = plans,
            races = races,
            time = FakeTimeProvider(clock.instant(), ZoneOffset.UTC),
            notifier = notifier,
        ).start(scope)
        try {
            withTimeout(2_000) {
                while (notificationTitle() != "Treino de hoje: Fácil — prontidão moderada") {
                    delay(10)
                }
            }
            assertThat(notificationBody()).contains("Ajustado para hoje")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancels the reminder when today is completed or skipped`(): Unit = runBlocking {
        notifier.notify(
            ReminderMessage(
                id = ReminderContent.WORKOUT_ID,
                kind = ReminderKind.WORKOUT,
                title = "Treino de hoje: Rodagem",
                body = "40 min",
                deepLink = DEEP_LINK_TODAY,
            ),
        )
        plans.saveWeek(
            plan(
                PlannedSession(
                    date = today,
                    type = WorkoutType.EASY,
                    title = "Rodagem",
                    durationMin = 40,
                    rationale = "Base.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                    status = SessionStatus.CLOSED,
                    closureReason = SessionClosureReason.USER_SKIPPED,
                    closedAt = Instant.EPOCH,
                ),
            ),
        )

        val scope = CoroutineScope(Dispatchers.Default)
        WorkoutReminderSync(
            prescriptions = prescriptions,
            plans = plans,
            races = races,
            time = FakeTimeProvider(clock.instant(), ZoneOffset.UTC),
            notifier = notifier,
        ).start(scope)
        try {
            withTimeout(2_000) {
                while (notificationTitleOrNull() != null) delay(10)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun notificationTitle(): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        return Shadows.shadowOf(notification).contentTitle.toString()
    }

    private fun notificationTitleOrNull(): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).allNotifications.firstOrNull() ?: return null
        return Shadows.shadowOf(notification).contentTitle.toString()
    }

    private fun notificationBody(): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        return Shadows.shadowOf(notification).contentText.toString()
    }

    private fun plan(session: PlannedSession) = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 2),
        sessions = listOf(session),
        weeklyMinutes = session.durationMin ?: 0,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 2),
    )

    private fun prescription(
        plannedType: WorkoutType,
        plannedDurationMin: Int?,
        prescribedType: WorkoutType,
        title: String,
        prescribedDurationMin: Int?,
        adjustment: PrescriptionAdjustment,
        rationale: String,
    ) = DailyPrescription(
        date = today,
        plannedSessionId = "session-1",
        plannedType = plannedType,
        plannedTitle = "Planejado",
        plannedDurationMin = plannedDurationMin,
        prescribedType = prescribedType,
        title = title,
        prescribedDurationMin = prescribedDurationMin,
        targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
        adjustment = adjustment,
        rationale = rationale,
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
    )
}
