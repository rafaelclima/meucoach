package com.meucoach.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meucoach.core.data.db.dao.ActivityAlternateDao
import com.meucoach.core.data.db.dao.ActivityDao
import com.meucoach.core.data.db.dao.AthleteProfileDao
import com.meucoach.core.data.db.dao.DailyCheckInDao
import com.meucoach.core.data.db.dao.DailyMetricsDao
import com.meucoach.core.data.db.dao.ImportJobDao
import com.meucoach.core.data.db.dao.LapDao
import com.meucoach.core.data.db.dao.SampleBlobDao
import com.meucoach.core.data.db.dao.TrainingPlanDao
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.data.db.entity.DailyCheckInEntity
import com.meucoach.core.data.db.entity.DailyMetricsEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity
import com.meucoach.core.data.db.entity.WeeklyPlanEntity

@Database(
    entities = [
        ActivityEntity::class,
        LapEntity::class,
        SampleBlobEntity::class,
        ImportJobEntity::class,
        ActivityAlternateEntity::class,
        AthleteProfileEntity::class,
        DailyCheckInEntity::class,
        DailyMetricsEntity::class,
        WeeklyPlanEntity::class,
        PlannedSessionEntity::class,
    ],
    version = MeuCoachDatabase.VERSION,
    exportSchema = true,
)
abstract class MeuCoachDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    abstract fun activityAlternateDao(): ActivityAlternateDao

    abstract fun lapDao(): LapDao

    abstract fun sampleBlobDao(): SampleBlobDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun profileDao(): AthleteProfileDao

    abstract fun dailyCheckInDao(): DailyCheckInDao

    abstract fun dailyMetricsDao(): DailyMetricsDao

    abstract fun trainingPlanDao(): TrainingPlanDao

    companion object {
        const val VERSION = 8
        const val NAME = "meucoach.db"

        /**
         * Version 1 was the initial local schema without the soft-delete marker.
         * Keeping the migration explicit prevents destructive upgrades when the
         * app is installed over an early build.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN deletedAt INTEGER")
            }
        }

        /**
         * Version 3 records files that describe an already stored activity.
         * Keeping them separate lets the canonical row be replaced by a richer
         * format without losing provenance.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_alternates` (
                        `id` TEXT NOT NULL,
                        `activityId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `sourceFileSha256` TEXT,
                        `rawFilePath` TEXT,
                        `parserVersion` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `distanceM` REAL NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_alternates_activityId` " +
                        "ON `activity_alternates` (`activityId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_alternates_sourceFileSha256` " +
                        "ON `activity_alternates` (`sourceFileSha256`)",
                )
            }
        }

        /**
         * Version 4 adds the single local athlete profile. It has no foreign
         * keys so it can be created safely on any existing install.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `athlete_profile` (
                        `id` TEXT NOT NULL,
                        `birthDate` TEXT NOT NULL,
                        `sex` TEXT NOT NULL,
                        `heightCm` REAL,
                        `weightKg` REAL,
                        `restingHeartRate` INTEGER,
                        `maxHeartRateObserved` INTEGER,
                        `lactateThresholdHeartRate` INTEGER,
                        `thresholdPaceSecPerKm` INTEGER,
                        `vdot` REAL,
                        `experience` TEXT NOT NULL,
                        `timezone` TEXT NOT NULL,
                        `availabilityJson` TEXT NOT NULL,
                        `goalJson` TEXT,
                        `city` TEXT,
                        `latitude` REAL,
                        `longitude` REAL,
                        `units` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Version 5 stores the daily subjective check-in by local date.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_check_ins` (
                        `date` TEXT NOT NULL,
                        `sleepQuality` INTEGER NOT NULL,
                        `fatigue` INTEGER NOT NULL,
                        `soreness` INTEGER NOT NULL,
                        `stress` INTEGER NOT NULL,
                        `illnessOrInjury` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Version 6 stores daily sleep, RHR, HRV and SpO2 from CSV/Health Connect.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_metrics` (
                        `date` TEXT NOT NULL,
                        `sleepDurationMin` INTEGER,
                        `restingHeartRate` REAL,
                        `hrvRmssdMs` REAL,
                        `spo2Avg` REAL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Version 7 persists the generated weekly plan and its sessions.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weekly_plans` (
                        `weekStart` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `confidence` TEXT NOT NULL,
                        `rationale` TEXT NOT NULL,
                        `weeklyMinutes` INTEGER NOT NULL,
                        `longRunMinutes` INTEGER,
                        `qualityCount` INTEGER NOT NULL,
                        `ruleIdsJson` TEXT NOT NULL,
                        `evidenceIdsJson` TEXT NOT NULL,
                        PRIMARY KEY(`weekStart`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `planned_sessions` (
                        `id` TEXT NOT NULL,
                        `weekStart` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `durationMin` INTEGER,
                        `rationale` TEXT NOT NULL,
                        `ruleIdsJson` TEXT NOT NULL,
                        `evidenceIdsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`weekStart`) REFERENCES `weekly_plans`(`weekStart`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_planned_sessions_weekStart` " +
                        "ON `planned_sessions` (`weekStart`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_planned_sessions_date` " +
                        "ON `planned_sessions` (`date`)",
                )
            }
        }

        /**
         * Version 8 records the completion state of each planned session.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `planned_sessions` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'PLANNED'",
                )
                db.execSQL("ALTER TABLE `planned_sessions` ADD COLUMN `skipReason` TEXT")
                db.execSQL("ALTER TABLE `planned_sessions` ADD COLUMN `completedAt` INTEGER")
            }
        }

        fun create(context: Context): MeuCoachDatabase = Room
            .databaseBuilder(context, MeuCoachDatabase::class.java, NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
            )
            .build()
    }
}
