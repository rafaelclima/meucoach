package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.db.testJob
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var userData: UserDataRepository
    private val fixedClock = Clock.fixed(Instant.parse("2023-11-14T13:00:00Z"), ZoneOffset.UTC)
    private var nextId = 0
    private var nextAlternateId = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ActivityRepository(
                database = database,
                clock = fixedClock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-${++nextId}" },
                alternateIdGenerator = { "alternate-${++nextAlternateId}" },
            )
        userData = UserDataRepository(
            database,
            HealthSyncStore(context, prefsName = "activity-test-${System.nanoTime()}"),
            fixedClock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves lists and opens an imported activity`() {
        runBlocking {
            val imported = importedActivity()

            val saved = repository.saveImportedActivity(imported, "sha", "imports/activity.fit")

            assertThat(saved).isEqualTo(SaveActivityResult.Saved("activity-1"))
            val summaries = repository.listActivitySummaries()
            assertThat(summaries).hasSize(1)
            val summary = summaries.single()
            assertThat(summary.id).isEqualTo("activity-1")
            assertThat(summary.distance.meters).isEqualTo(5_000.0)
            assertThat(summary.averagePace).isEqualTo(Pace.fromSecondsPerKm(360))
            assertThat(repository.observeActivitySummaries().first()).hasSize(1)

            val stored = repository.openActivity("activity-1")
            assertThat(stored).isNotNull()
            assertThat(stored?.imported).isEqualTo(imported)
            assertThat(stored?.importedAt).isEqualTo(fixedClock.instant())
            assertThat(stored?.zoneId).isEqualTo("America/Sao_Paulo")
            assertThat(stored?.localDate).isEqualTo(LocalDate.of(2023, 11, 14))
            assertThat(stored?.rawFilePath).isEqualTo("imports/activity.fit")
            assertThat(stored?.sourceFileSha256).isEqualTo("sha")
            assertThat(stored?.sampleWarning).isNull()
        }
    }

    @Test
    fun `duplicate file hash returns the existing activity`() {
        runBlocking {
            val imported = importedActivity()

            val first = repository.saveImportedActivity(imported, "same-hash")
            val second = repository.saveImportedActivity(imported, "same-hash")

            assertThat(first).isEqualTo(SaveActivityResult.Saved("activity-1"))
            assertThat(second).isEqualTo(SaveActivityResult.Duplicate("activity-1"))
            assertThat(repository.listActivitySummaries()).hasSize(1)
        }
    }

    @Test
    fun `replaces tcx with fit and keeps the previous file as alternate`() {
        runBlocking {
            val tcx = importedActivity(source = ActivitySource.TCX)
            val fit = importedActivity(source = ActivitySource.FIT, sampleCount = 10)

            assertThat(repository.saveImportedActivity(tcx, "tcx-sha", "imports/run.tcx"))
                .isEqualTo(SaveActivityResult.Saved("activity-1"))
            assertThat(repository.saveImportedActivity(fit, "fit-sha", "imports/run.fit"))
                .isEqualTo(SaveActivityResult.Replaced("activity-1", ActivitySource.TCX))

            assertThat(repository.listActivitySummaries()).hasSize(1)
            assertThat(repository.listActivitySummaries().single().sources)
                .containsExactly(ActivitySource.FIT, ActivitySource.TCX)
            assertThat(repository.observeActivitySummaries().first().single().sources)
                .containsExactly(ActivitySource.FIT, ActivitySource.TCX)
            val stored = repository.openActivity("activity-1")
            assertThat(stored?.imported?.source).isEqualTo(ActivitySource.FIT)
            assertThat(stored?.imported?.samples).hasSize(10)
            assertThat(stored?.sourceFileSha256).isEqualTo("fit-sha")
            assertThat(stored?.alternates).hasSize(1)
            val alternate = stored!!.alternates.single()
            assertThat(alternate.source).isEqualTo(ActivitySource.TCX)
            assertThat(alternate.sourceFileSha256).isEqualTo("tcx-sha")
            assertThat(alternate.rawFilePath).isEqualTo("imports/run.tcx")
            assertThat(alternate.sampleCount).isEqualTo(2)
            val exported = JSONObject(userData.exportJson()).getJSONArray("activities").getJSONObject(0)
            assertThat(exported.getJSONArray("alternates").length()).isEqualTo(1)
            assertThat(exported.getJSONArray("alternates").getJSONObject(0).getString("source"))
                .isEqualTo("TCX")
        }
    }

    @Test
    fun `keeps a lower priority file as alternate instead of replacing`() {
        runBlocking {
            val fit = importedActivity(source = ActivitySource.FIT, sampleCount = 10)
            val tcx = importedActivity(source = ActivitySource.TCX)

            assertThat(repository.saveImportedActivity(fit, "fit-sha", "imports/run.fit"))
                .isEqualTo(SaveActivityResult.Saved("activity-1"))
            assertThat(repository.saveImportedActivity(tcx, "tcx-sha", "imports/run.tcx"))
                .isEqualTo(SaveActivityResult.Duplicate("activity-1"))
            assertThat(repository.saveImportedActivity(tcx, "tcx-sha", "imports/run.tcx"))
                .isEqualTo(SaveActivityResult.Duplicate("activity-1"))

            val stored = repository.openActivity("activity-1")
            assertThat(stored?.imported?.source).isEqualTo(ActivitySource.FIT)
            assertThat(stored?.imported?.samples).hasSize(10)
            assertThat(stored?.alternates).hasSize(1)
            assertThat(stored!!.alternates.single().source).isEqualTo(ActivitySource.TCX)
            assertThat(repository.listActivitySummaries().single().sources)
                .containsExactly(ActivitySource.FIT, ActivitySource.TCX)
        }
    }

    @Test
    fun `replaces the same source when the new file has more samples`() {
        runBlocking {
            val sparse = importedActivity(source = ActivitySource.FIT, sampleCount = 2)
            val rich = importedActivity(source = ActivitySource.FIT, sampleCount = 5)

            repository.saveImportedActivity(sparse, "sparse-sha")
            assertThat(repository.saveImportedActivity(rich, "rich-sha"))
                .isEqualTo(SaveActivityResult.Replaced("activity-1", ActivitySource.FIT))

            val stored = repository.openActivity("activity-1")
            assertThat(stored?.imported?.samples).hasSize(5)
            assertThat(stored?.sourceFileSha256).isEqualTo("rich-sha")
            assertThat(stored?.alternates?.single()?.sampleCount).isEqualTo(2)
        }
    }

    @Test
    fun `keeps the same source with fewer samples as alternate`() {
        runBlocking {
            val rich = importedActivity(source = ActivitySource.FIT, sampleCount = 5)
            val sparse = importedActivity(source = ActivitySource.FIT, sampleCount = 2)

            repository.saveImportedActivity(rich, "rich-sha")
            assertThat(repository.saveImportedActivity(sparse, "sparse-sha"))
                .isEqualTo(SaveActivityResult.Duplicate("activity-1"))

            val stored = repository.openActivity("activity-1")
            assertThat(stored?.imported?.samples).hasSize(5)
            assertThat(stored?.sourceFileSha256).isEqualTo("rich-sha")
            assertThat(stored?.alternates?.single()?.sampleCount).isEqualTo(2)
            assertThat(repository.listActivitySummaries().single().sources)
                .containsExactly(ActivitySource.FIT)
        }
    }

    @Test
    fun `creates a new activity outside the dedup tolerance`() {
        runBlocking {
            val first = importedActivity()
            val later = importedActivity(start = Instant.parse("2023-11-14T10:00:02Z"))

            assertThat(repository.saveImportedActivity(first, "first-sha"))
                .isEqualTo(SaveActivityResult.Saved("activity-1"))
            assertThat(repository.saveImportedActivity(later, "later-sha"))
                .isEqualTo(SaveActivityResult.Saved("activity-2"))

            assertThat(repository.listActivitySummaries()).hasSize(2)
        }
    }

    @Test
    fun `reimporting a soft deleted file conflicts with the trash`() {
        runBlocking {
            val imported = importedActivity()
            val saved = repository.saveImportedActivity(imported, "sha") as SaveActivityResult.Saved
            assertThat(repository.deleteActivity(saved.id)).isTrue()
            assertThat(repository.listActivitySummaries()).isEmpty()
            assertThat(repository.observeTrash().first().single().id).isEqualTo(saved.id)

            assertThat(repository.saveImportedActivity(imported, "sha"))
                .isEqualTo(SaveActivityResult.ConflictingTrash(saved.id))
            assertThat(repository.listActivitySummaries()).isEmpty()

            assertThat(repository.restoreActivity(saved.id)).isTrue()
            assertThat(repository.listActivitySummaries()).hasSize(1)
            assertThat(repository.openActivity(saved.id)?.sourceFileSha256).isEqualTo("sha")
        }
    }

    @Test
    fun `purges only trashed activities older than the cutoff`() {
        runBlocking {
            val saved = repository.saveImportedActivity(importedActivity()) as SaveActivityResult.Saved
            repository.deleteActivity(saved.id, Instant.parse("2026-11-01T12:00:00Z"))
            assertThat(database.activityDao().findByIdIncludingDeleted(saved.id)?.deletedAt)
                .isEqualTo(Instant.parse("2026-11-01T12:00:00Z").toEpochMilli())

            val tooEarly = repository.purgeTrashBefore(Instant.parse("2026-11-08T11:59:59Z"))
            assertThat(tooEarly).isEmpty()
            assertThat(repository.openActivity(saved.id)).isNull()
            assertThat(database.sampleBlobDao().getForActivity(saved.id)).isNotNull()

            val purged = repository.purgeTrashBefore(Instant.parse("2026-11-08T12:00:00Z"))
            assertThat(purged).hasSize(1)
            assertThat(purged.single().id).isEqualTo(saved.id)
            assertThat(database.activityDao().findByIdIncludingDeleted(saved.id)).isNull()
            assertThat(database.lapDao().getForActivity(saved.id)).isEmpty()
            assertThat(database.sampleBlobDao().getForActivity(saved.id)).isNull()
        }
    }

    @Test
    fun `permanent delete removes a trashed activity and its children`() {
        runBlocking {
            val saved = repository.saveImportedActivity(importedActivity()) as SaveActivityResult.Saved
            repository.deleteActivity(saved.id)

            val purged = repository.purgeActivity(saved.id)

            assertThat(purged?.id).isEqualTo(saved.id)
            assertThat(database.activityDao().findByIdIncludingDeleted(saved.id)).isNull()
            assertThat(database.lapDao().getForActivity(saved.id)).isEmpty()
            assertThat(database.sampleBlobDao().getForActivity(saved.id)).isNull()
            assertThat(repository.purgeActivity(saved.id)).isNull()
        }
    }

    @Test
    fun `corrupt sample blob keeps the activity and returns a warning`() {
        runBlocking {
            val saved = repository.saveImportedActivity(importedActivity()) as SaveActivityResult.Saved
            val blob = database.sampleBlobDao().getForActivity(saved.id)
            assertThat(blob).isNotNull()
            database.sampleBlobDao().upsert(blob!!.copy(bytes = byteArrayOf(1, 2, 3)))

            val stored = repository.openActivity(saved.id)

            assertThat(stored).isNotNull()
            assertThat(stored?.imported?.samples).isEmpty()
            assertThat(stored?.sampleWarning).contains("séries temporais")
            assertThat(repository.listActivitySummaries()).hasSize(1)
        }
    }

    @Test
    fun `unsupported sample blob encoding is ignored with a warning`() {
        runBlocking {
            val saved = repository.saveImportedActivity(importedActivity()) as SaveActivityResult.Saved
            val blob = database.sampleBlobDao().getForActivity(saved.id)!!
            database.sampleBlobDao().upsert(blob.copy(version = 2, encoding = "future-codec"))

            val stored = repository.openActivity(saved.id)

            assertThat(stored?.imported?.samples).isEmpty()
            assertThat(stored?.sampleWarning).contains("formato")
        }
    }

    @Test
    fun `soft delete hides the activity and permanent delete removes children`() {
        runBlocking {
            val saved = repository.saveImportedActivity(importedActivity()) as SaveActivityResult.Saved

            assertThat(repository.deleteActivity(saved.id)).isTrue()
            assertThat(repository.deleteActivity(saved.id)).isFalse()
            assertThat(repository.listActivitySummaries()).isEmpty()
            assertThat(repository.openActivity(saved.id)).isNull()
            assertThat(database.lapDao().getForActivity(saved.id)).hasSize(1)
            assertThat(database.sampleBlobDao().getForActivity(saved.id)).isNotNull()

            assertThat(repository.permanentlyDeleteActivity(saved.id)).isTrue()
            assertThat(database.lapDao().getForActivity(saved.id)).isEmpty()
            assertThat(database.sampleBlobDao().getForActivity(saved.id)).isNull()
        }
    }

    @Test
    fun `observes import jobs as summaries`() {
        runBlocking {
            database.importJobDao().insert(testJob(activityId = null))

            val job = repository.observeImportJobs().first().single()

            assertThat(job.fileName).isEqualTo("activity.fit")
            assertThat(job.status).isEqualTo(ImportJobStatus.DONE)
            assertThat(job.activityId).isNull()
            assertThat(repository.listImportJobs()).hasSize(1)
        }
    }

    private fun importedActivity(
        source: ActivitySource = ActivitySource.FIT,
        start: Instant = Instant.parse("2023-11-14T10:00:00Z"),
        durationSec: Int = 1_800,
        distanceM: Double = 5_000.0,
        sampleCount: Int = 2,
    ): ImportedActivity = ImportedActivity(
        source = source,
        sport = Sport.RUNNING,
        startTime = start,
        durationSec = durationSec,
        distanceM = distanceM,
        sourceId = "${source.name.lowercase()}-source",
        subSport = "street",
        movingTimeSec = 1_750,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
        averageCadenceSpm = 170,
        maxCadenceSpm = 180,
        averagePowerW = 250,
        maxPowerW = 300,
        averageStepLengthM = 1.1,
        elevationGainM = 40.0,
        elevationLossM = 35.0,
        calories = 320,
        laps =
        listOf(
            ActivityLap(
                index = 0,
                durationSec = 900,
                distanceM = 2_500.0,
                startTime = start,
                averageHeartRate = 148,
                maxHeartRate = 160,
                averagePace = Pace.fromSecondsPerKm(360),
                averageCadenceSpm = 168,
                averagePowerW = 240,
                elevationGainM = 20.0,
            ),
        ),
        samples =
        List(sampleCount) { index ->
            ActivitySample(
                offsetSec = index,
                timestamp = start.plusSeconds(index.toLong()),
                latitude = if (index == 0) -5.79 else null,
                longitude = if (index == 0) -35.21 else null,
                altitudeM = if (index == 0) 30.0 else null,
                speedMps = 2.8 + index * 0.1,
                heartRate = 140 + index,
                cadenceSpm = 160 + index,
                powerW = if (index == 0) 220 else null,
                stepLengthM = if (index == 0) 1.1 else null,
                developerFields = if (index == 0) mapOf("temperature" to 27.5) else emptyMap(),
            )
        },
        deviceName = "Amazfit Balance",
        parserVersion = "1.0.0",
    )
}
