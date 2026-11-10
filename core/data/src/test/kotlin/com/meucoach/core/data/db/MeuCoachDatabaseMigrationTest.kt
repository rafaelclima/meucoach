package com.meucoach.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.data.db.entity.DailyCheckInEntity
import com.meucoach.core.data.db.entity.DailyMetricsEntity
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Units
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeuCoachDatabaseMigrationTest {
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MeuCoachDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `migration from 1 to 14 adds soft delete, alternates, profile and check-ins without losing data`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 1)
                .apply {
                    insertLegacyActivity(includeDeletedAt = false)
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_1_2,
                        MeuCoachDatabase.MIGRATION_2_3,
                        MeuCoachDatabase.MIGRATION_3_4,
                        MeuCoachDatabase.MIGRATION_4_5,
                        MeuCoachDatabase.MIGRATION_5_6,
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                val activity = migrated.activityDao().findById("legacy")
                assertThat(activity).isNotNull()
                assertThat(activity?.deletedAt).isNull()
                assertThat(activity?.distanceM).isEqualTo(5_000.0)
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.profileDao().get()).isNull()

                assertThat(migrated.activityDao().softDelete("legacy", 99L)).isEqualTo(1)
                assertThat(migrated.activityDao().findByIdIncludingDeleted("legacy")?.deletedAt).isEqualTo(99L)
                assertThat(migrated.activityAlternateDao().getForActivity("legacy")).isEmpty()
                migrated.activityAlternateDao().insert(alternate())
                assertThat(migrated.activityAlternateDao().getForActivity("legacy")).hasSize(1)
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 2 to 14 creates alternates, profile and check-ins without losing data`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 2)
                .apply {
                    insertLegacyActivity(includeDeletedAt = true)
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_2_3,
                        MeuCoachDatabase.MIGRATION_3_4,
                        MeuCoachDatabase.MIGRATION_4_5,
                        MeuCoachDatabase.MIGRATION_5_6,
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                val activity = migrated.activityDao().findById("legacy")
                assertThat(activity).isNotNull()
                assertThat(activity?.deletedAt).isNull()
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.activityAlternateDao().getForActivity("legacy")).isEmpty()

                migrated.activityAlternateDao().insert(alternate())
                assertThat(migrated.activityAlternateDao().findBySha256("alternate-sha")?.source)
                    .isEqualTo(ActivitySource.TCX)
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 3 to 14 creates the profile and check-in tables`() {
        runBlocking {
            helper.createDatabase(databaseName, 3).close()

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_3_4,
                        MeuCoachDatabase.MIGRATION_4_5,
                        MeuCoachDatabase.MIGRATION_5_6,
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.profileDao().get()).isNull()
                assertThat(migrated.dailyCheckInDao().get("2026-10-30")).isNull()
                migrated.profileDao().upsert(profile())
                assertThat(migrated.profileDao().get()?.id).isEqualTo("athlete-1")
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 4 to 14 creates the check-in and metrics tables`() {
        runBlocking {
            helper.createDatabase(databaseName, 4).close()

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_4_5,
                        MeuCoachDatabase.MIGRATION_5_6,
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.dailyCheckInDao().get("2026-10-30")).isNull()
                assertThat(migrated.dailyMetricsDao().get("2026-10-30")).isNull()
                migrated.dailyCheckInDao().upsert(checkIn())
                assertThat(migrated.dailyCheckInDao().get("2026-10-30")?.fatigue).isEqualTo(2)
                migrated.dailyMetricsDao().upsert(metrics())
                assertThat(migrated.dailyMetricsDao().get("2026-10-30")?.hrvRmssdMs).isEqualTo(60.0)
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 5 to 14 creates the metrics and plan tables`() {
        runBlocking {
            helper.createDatabase(databaseName, 5).close()

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_5_6,
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.dailyMetricsDao().get("2026-10-30")).isNull()
                migrated.dailyMetricsDao().upsert(metrics())
                assertThat(migrated.dailyMetricsDao().get("2026-10-30")?.restingHeartRate).isEqualTo(58.0)
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 6 to 14 creates the plan and status tables`() {
        runBlocking {
            helper.createDatabase(databaseName, 6).close()

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_6_7,
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.trainingPlanDao().getPlan("2026-11-02")).isNull()
                assertThat(migrated.trainingPlanDao().getSessions("2026-11-02")).isEmpty()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 7 to 14 adds session status and heat columns`() {
        runBlocking {
            helper.createDatabase(databaseName, 7).close()

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_7_8,
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.trainingPlanDao().getSessions("2026-11-02")).isEmpty()
                migrated.openHelper.writableDatabase.execSQL(
                    "INSERT INTO weekly_plans (weekStart, createdAt, createdOn, confidence, rationale, " +
                        "weeklyMinutes, longRunMinutes, qualityCount, ruleIdsJson, evidenceIdsJson) " +
                        "VALUES ('2026-11-02', 1, '2026-11-02', 'MEDIUM', 'plano', 40, NULL, 0, '[]', '[]')",
                )
                migrated.openHelper.writableDatabase.execSQL(
                    "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                        "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, completedAt) " +
                        "VALUES ('2026-11-02-2026-11-02', '2026-11-02', '2026-11-02', 'EASY', " +
                        "'Fácil', 40, 'base', '[]', '[]', '{}', 'PLANNED', NULL, NULL)",
                )
                assertThat(migrated.trainingPlanDao().getSessions("2026-11-02").single().status.name)
                    .isEqualTo("PLANNED")
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 8 to 14 adds heat and treadmill columns with safe defaults`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 8)
                .apply {
                    execSQL(
                        "INSERT INTO daily_check_ins (date, sleepQuality, fatigue, soreness, stress, " +
                            "illnessOrInjury, createdAt, updatedAt) " +
                            "VALUES ('2026-10-30', 4, 2, 1, 2, 0, 1, 1)",
                    )
                    execSQL(
                        "INSERT INTO athlete_profile (id, birthDate, sex, heightCm, weightKg, " +
                            "restingHeartRate, maxHeartRateObserved, lactateThresholdHeartRate, " +
                            "thresholdPaceSecPerKm, vdot, experience, timezone, availabilityJson, " +
                            "goalJson, city, latitude, longitude, units, createdAt, updatedAt) " +
                            "VALUES ('athlete-1', '1988-01-01', 'MALE', NULL, NULL, 58, 187, NULL, " +
                            "NULL, NULL, 'BEGINNER', 'UTC', '[]', NULL, NULL, NULL, NULL, " +
                            "'METRIC', 1, 1)",
                    )
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_8_9,
                        MeuCoachDatabase.MIGRATION_9_10,
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                assertThat(migrated.dailyCheckInDao().get("2026-10-30")?.heatLevel)
                    .isEqualTo(com.meucoach.core.model.HeatLevel.NONE)
                assertThat(migrated.profileDao().get()?.treadmillAccess).isFalse()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 10 to 14 adds prescriptions and preserves plan status`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 10)
                .apply {
                    execSQL(
                        "INSERT INTO weekly_plans (weekStart, createdAt, confidence, rationale, " +
                            "weeklyMinutes, longRunMinutes, qualityCount, ruleIdsJson, evidenceIdsJson) " +
                            "VALUES ('2026-11-02', 1, 'MEDIUM', 'plano', 40, NULL, 0, '[]', '[]')",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, status, skipReason, completedAt) " +
                            "VALUES ('2026-11-02-2026-11-02', '2026-11-02', '2026-11-02', 'EASY', " +
                            "'Fácil', 40, 'base', '[]', '[]', 'DONE', NULL, 123)",
                    )
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_10_11,
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                val plan = migrated.trainingPlanDao().getPlan("2026-11-02")
                assertThat(plan).isNotNull()
                assertThat(plan?.createdOn).isEqualTo("2026-11-02")
                val session = migrated.trainingPlanDao().getSessions("2026-11-02").single()
                assertThat(session.status.name).isEqualTo("DONE")
                assertThat(session.completedAt).isEqualTo(123)
                assertThat(session.targetsJson).isEqualTo("{}")
                assertThat(migrated.dailyPrescriptionDao().getAll()).isEmpty()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 11 to 12 adds executions and marks legacy completions`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 11)
                .apply {
                    execSQL(
                        "INSERT INTO weekly_plans (weekStart, createdAt, createdOn, confidence, rationale, " +
                            "weeklyMinutes, longRunMinutes, qualityCount, ruleIdsJson, evidenceIdsJson) " +
                            "VALUES ('2026-11-02', 1, '2026-11-02', 'MEDIUM', 'plano', 40, NULL, 0, '[]', '[]')",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, completedAt) " +
                            "VALUES ('2026-11-02-2026-11-02', '2026-11-02', '2026-11-02', 'EASY', " +
                            "'Fácil', 40, 'base', '[]', '[]', '{}', 'DONE', NULL, 123)",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, completedAt) " +
                            "VALUES ('2026-11-02-2026-11-03', '2026-11-02', '2026-11-03', 'EASY', " +
                            "'Fácil', 40, 'base', '[]', '[]', '{}', 'SKIPPED', 'dor', NULL)",
                    )
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(
                        MeuCoachDatabase.MIGRATION_11_12,
                        MeuCoachDatabase.MIGRATION_12_13,
                        MeuCoachDatabase.MIGRATION_13_14,
                    )
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                val sessions = migrated.trainingPlanDao().getSessions("2026-11-02")
                val done = sessions.first { it.date == "2026-11-02" }
                assertThat(done.status.name).isEqualTo("DONE")
                assertThat(done.completionEvidence?.name).isEqualTo("LEGACY_UNVERIFIED")
                assertThat(done.completedAt).isEqualTo(123)
                assertThat(done.activityId).isNull()
                val closed = sessions.first { it.date == "2026-11-03" }
                assertThat(closed.status.name).isEqualTo("CLOSED")
                assertThat(closed.closureReason?.name).isEqualTo("USER_SKIPPED")
                assertThat(closed.skipReason).isEqualTo("dor")
                assertThat(closed.closedAt).isNotNull()
                assertThat(migrated.sessionExecutionDao().getAll()).isEmpty()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 12 to 14 adds version and profile metadata without rewriting plans`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 12)
                .apply {
                    execSQL(
                        "INSERT INTO weekly_plans (weekStart, createdAt, createdOn, confidence, rationale, " +
                            "weeklyMinutes, longRunMinutes, qualityCount, ruleIdsJson, evidenceIdsJson) " +
                            "VALUES ('2026-11-02', 1, '2026-11-04', 'MEDIUM', 'plano', 40, NULL, 0, '[]', '[]')",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, " +
                            "completedAt, completionEvidence) " +
                            "VALUES ('2026-11-02-2026-11-05', '2026-11-02', '2026-11-05', 'EASY', " +
                            "'Fácil', 40, 'base', '[]', '[]', '{\"zone\":\"Z2\"}', 'PLANNED', NULL, NULL, NULL)",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, " +
                            "completedAt, completionEvidence) " +
                            "VALUES ('2026-11-02-2026-11-03', '2026-11-02', '2026-11-03', 'EASY', " +
                            "'Fácil', 30, 'base', '[]', '[]', '{}', 'DONE', NULL, 123, 'LEGACY_UNVERIFIED')",
                    )
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(MeuCoachDatabase.MIGRATION_12_13, MeuCoachDatabase.MIGRATION_13_14)
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                val plan = migrated.trainingPlanDao().getPlan("2026-11-02")
                assertThat(plan).isNotNull()
                assertThat(plan?.version).isEqualTo(1)
                assertThat(plan?.revisionReason).isNull()
                assertThat(plan?.revisedAt).isNull()
                assertThat(plan?.inputsHash).isNull()
                assertThat(plan?.availabilityHash).isNull()
                assertThat(plan?.generatedFromFingerprintVersion).isNull()
                assertThat(plan?.generatedFromFingerprint).isNull()
                assertThat(plan?.generatedFromProfileSnapshotJson).isNull()
                assertThat(plan?.lastEvaluatedFingerprintVersion).isNull()
                assertThat(plan?.lastEvaluatedFingerprint).isNull()
                assertThat(plan?.temporalContextHash).isNull()
                assertThat(plan?.createdOn).isEqualTo("2026-11-04")
                assertThat(plan?.weeklyMinutes).isEqualTo(40)

                val sessions = migrated.trainingPlanDao().getSessions("2026-11-02")
                assertThat(sessions).hasSize(2)
                val planned = sessions.first { it.status.name == "PLANNED" }
                assertThat(planned.date).isEqualTo("2026-11-05")
                assertThat(planned.targetsJson).isEqualTo("{\"zone\":\"Z2\"}")
                val done = sessions.first { it.status.name == "DONE" }
                assertThat(done.completedAt).isEqualTo(123)
                assertThat(done.completionEvidence?.name).isEqualTo("LEGACY_UNVERIFIED")
                assertThat(migrated.weeklyPlanRevisionDao().getAll()).isEmpty()

                migrated.weeklyPlanRevisionDao().insert(revision())
                assertThat(migrated.weeklyPlanRevisionDao().getForWeek("2026-11-02")).hasSize(1)
                assertThat(migrated.trainingPlanDao().deletePlan("2026-11-02")).isEqualTo(1)
                assertThat(migrated.weeklyPlanRevisionDao().getAll()).isEmpty()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun `migration from 13 to 14 preserves plans and revisions with unknown profile metadata`() {
        runBlocking {
            helper
                .createDatabase(databaseName, 13)
                .apply {
                    execSQL(
                        "INSERT INTO weekly_plans (weekStart, createdAt, createdOn, confidence, rationale, " +
                            "weeklyMinutes, longRunMinutes, qualityCount, ruleIdsJson, evidenceIdsJson, " +
                            "version, revisionReason, revisedAt, inputsHash, availabilityHash) " +
                            "VALUES ('2026-11-02', 1, '2026-11-04', 'MEDIUM', 'plano', 40, NULL, 0, " +
                            "'[]', '[]', 2, 'revisão', 99, 'inputs-2', 'availability-2')",
                    )
                    execSQL(
                        "INSERT INTO planned_sessions (id, weekStart, date, type, title, durationMin, " +
                            "rationale, ruleIdsJson, evidenceIdsJson, targetsJson, status, skipReason, " +
                            "completedAt, completionEvidence, executionId, activityId, closureReason, closedAt) " +
                            "VALUES ('2026-11-02-2026-11-05', '2026-11-02', '2026-11-05', 'EASY', " +
                            "'Fácil', 40, 'base', '[]', '[]', '{\"zone\":\"Z2\"}', 'PLANNED', NULL, NULL, " +
                            "NULL, NULL, NULL, NULL, NULL)",
                    )
                    execSQL(
                        "INSERT INTO weekly_plan_revisions (id, weekStart, fromVersion, toVersion, " +
                            "fromInputsHash, toInputsHash, fromAvailabilityHash, toAvailabilityHash, " +
                            "generatorVersion, previousPlanJson, replacedSessionsJson, trigger, reason, " +
                            "ruleIdsJson, evidenceIdsJson, confidence, createdAt) " +
                            "VALUES ('2026-11-02-v2', '2026-11-02', 1, 2, NULL, 'inputs-2', NULL, " +
                            "'availability-2', 'weekly-plan-v1', '{}', '[]', 'USER_REQUEST', 'revisão', " +
                            "'[]', '[]', 'MEDIUM', 1)",
                    )
                    close()
                }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val migrated =
                Room
                    .databaseBuilder(context, MeuCoachDatabase::class.java, databaseName)
                    .addMigrations(MeuCoachDatabase.MIGRATION_13_14)
                    .allowMainThreadQueries()
                    .build()

            try {
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(14)
                val plan = migrated.trainingPlanDao().getPlan("2026-11-02")
                assertThat(plan?.version).isEqualTo(2)
                assertThat(plan?.inputsHash).isEqualTo("inputs-2")
                assertThat(plan?.availabilityHash).isEqualTo("availability-2")
                assertThat(plan?.generatedFromFingerprintVersion).isNull()
                assertThat(plan?.generatedFromFingerprint).isNull()
                assertThat(plan?.generatedFromProfileSnapshotJson).isNull()
                assertThat(plan?.lastEvaluatedFingerprintVersion).isNull()
                assertThat(plan?.lastEvaluatedFingerprint).isNull()
                assertThat(plan?.temporalContextHash).isNull()
                assertThat(migrated.trainingPlanDao().getSessions("2026-11-02")).hasSize(1)

                val revision = migrated.weeklyPlanRevisionDao().get("2026-11-02", 2)!!
                assertThat(revision.fromFingerprintVersion).isNull()
                assertThat(revision.fromFingerprint).isNull()
                assertThat(revision.fromProfileSnapshotJson).isNull()
                assertThat(revision.fromTemporalContextHash).isNull()
                assertThat(revision.toFingerprintVersion).isNull()
                assertThat(revision.toFingerprint).isNull()
                assertThat(revision.toProfileSnapshotJson).isNull()
                assertThat(revision.toTemporalContextHash).isNull()
            } finally {
                migrated.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLegacyActivity(includeDeletedAt: Boolean) {
        val deletedAtColumn = if (includeDeletedAt) ", deletedAt" else ""
        val deletedAtValue = if (includeDeletedAt) ", NULL" else ""
        execSQL(
            """
            INSERT INTO activities (
                id, source, sourceId, sourceFileSha256, sport, subSport,
                startTimeUtc, zoneId, localDate, durationSec, movingTimeSec,
                distanceM, averagePaceSecPerKm, bestPaceSecPerKm, averageHeartRate,
                maxHeartRate, averageCadenceSpm, maxCadenceSpm, averagePowerW,
                maxPowerW, averageStepLengthM, elevationGainM, elevationLossM,
                calories, trainingEffectAerobic, trainingEffectAnaerobic,
                trainingLoadPeak, deviceName, rawFilePath, parserVersion, importedAt$deletedAtColumn
            ) VALUES (
                'legacy', 'FIT', NULL, NULL, 'RUNNING', NULL,
                1700000000000, 'UTC', '2023-11-14', 1800, 1750,
                5000.0, 360.0, 320.0, 150,
                170, 170, 180, 250,
                300, 1.1, 40.0, 35.0,
                320, 3.2, 0.4,
                120.0, NULL, NULL, '1.0.0', 1700000100000$deletedAtValue
            )
            """.trimIndent(),
        )
    }

    private fun metrics() = DailyMetricsEntity(
        date = "2026-10-30",
        sleepDurationMin = 450,
        restingHeartRate = 58.0,
        hrvRmssdMs = 60.0,
        spo2Avg = 97.0,
        source = DailyMetricsSource.CSV,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun checkIn() = DailyCheckInEntity(
        date = "2026-10-30",
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
        illnessOrInjury = false,
        heatLevel = com.meucoach.core.model.HeatLevel.NONE,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun profile() = AthleteProfileEntity(
        id = "athlete-1",
        birthDate = "1988-01-01",
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = null,
        thresholdPaceSecPerKm = null,
        vdot = null,
        experience = Experience.BEGINNER,
        timezone = "America/Sao_Paulo",
        availabilityJson = "{}",
        goalJson = null,
        city = "Natal",
        latitude = -5.79,
        longitude = -35.21,
        units = Units.METRIC,
        treadmillAccess = false,
        name = "Rafael",
        avatarPath = null,
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun revision() = com.meucoach.core.data.db.entity.WeeklyPlanRevisionEntity(
        id = "2026-11-02-v2",
        weekStart = "2026-11-02",
        fromVersion = 1,
        toVersion = 2,
        fromInputsHash = null,
        toInputsHash = "inputs-2",
        fromAvailabilityHash = null,
        toAvailabilityHash = "availability-2",
        generatorVersion = "weekly-plan-v1",
        previousPlanJson = "{}",
        replacedSessionsJson = "[]",
        trigger = "USER_REQUEST",
        reason = "revisão",
        ruleIdsJson = "[]",
        evidenceIdsJson = "[]",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        createdAt = 1L,
    )

    private fun alternate() = ActivityAlternateEntity(
        id = "alternate-1",
        activityId = "legacy",
        source = ActivitySource.TCX,
        sourceId = "tcx-source",
        sourceFileSha256 = "alternate-sha",
        rawFilePath = "imports/legacy.tcx",
        parserVersion = "1.0.0",
        importedAt = 1_700_000_200_000,
        sampleCount = 100,
        durationSec = 1_800,
        distanceM = 5_000.0,
    )
}
