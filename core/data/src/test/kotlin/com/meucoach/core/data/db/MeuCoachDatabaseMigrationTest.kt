package com.meucoach.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.model.ActivitySource
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
    fun `migration from 1 to 3 adds soft delete and alternates without losing data`() {
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
                    .addMigrations(MeuCoachDatabase.MIGRATION_1_2, MeuCoachDatabase.MIGRATION_2_3)
                    .allowMainThreadQueries()
                    .build()

            try {
                val activity = migrated.activityDao().findById("legacy")
                assertThat(activity).isNotNull()
                assertThat(activity?.deletedAt).isNull()
                assertThat(activity?.distanceM).isEqualTo(5_000.0)
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(3)

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
    fun `migration from 2 to 3 creates alternates without losing data`() {
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
                    .addMigrations(MeuCoachDatabase.MIGRATION_2_3)
                    .allowMainThreadQueries()
                    .build()

            try {
                val activity = migrated.activityDao().findById("legacy")
                assertThat(activity).isNotNull()
                assertThat(activity?.deletedAt).isNull()
                assertThat(migrated.openHelper.writableDatabase.version).isEqualTo(3)
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
