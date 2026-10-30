package com.meucoach.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meucoach.core.data.db.dao.ActivityDao
import com.meucoach.core.data.db.dao.ImportJobDao
import com.meucoach.core.data.db.dao.LapDao
import com.meucoach.core.data.db.dao.SampleBlobDao
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity

@Database(
    entities = [
        ActivityEntity::class,
        LapEntity::class,
        SampleBlobEntity::class,
        ImportJobEntity::class,
    ],
    version = MeuCoachDatabase.VERSION,
    exportSchema = true,
)
abstract class MeuCoachDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    abstract fun lapDao(): LapDao

    abstract fun sampleBlobDao(): SampleBlobDao

    abstract fun importJobDao(): ImportJobDao

    companion object {
        const val VERSION = 2
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

        fun create(context: Context): MeuCoachDatabase = Room
            .databaseBuilder(context, MeuCoachDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
