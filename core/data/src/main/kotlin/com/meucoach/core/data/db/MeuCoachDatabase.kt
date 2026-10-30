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
import com.meucoach.core.data.db.dao.ImportJobDao
import com.meucoach.core.data.db.dao.LapDao
import com.meucoach.core.data.db.dao.SampleBlobDao
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity

@Database(
    entities = [
        ActivityEntity::class,
        LapEntity::class,
        SampleBlobEntity::class,
        ImportJobEntity::class,
        ActivityAlternateEntity::class,
        AthleteProfileEntity::class,
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

    companion object {
        const val VERSION = 4
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

        fun create(context: Context): MeuCoachDatabase = Room
            .databaseBuilder(context, MeuCoachDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
}
