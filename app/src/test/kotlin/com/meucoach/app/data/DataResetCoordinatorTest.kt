package com.meucoach.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.firstrun.FirstRunTipStore
import com.meucoach.app.health.HealthSyncCoordinator
import com.meucoach.app.importer.ImportNotifier
import com.meucoach.app.importer.ImportStaging
import com.meucoach.app.importer.ImportWorker
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.importer.RaceAutoCompletionStore
import com.meucoach.app.profile.ProfilePhotoStore
import com.meucoach.app.reminder.ReminderNotifier
import com.meucoach.app.replan.ReplanSuggestionStore
import com.meucoach.app.story.ArtPhotoRecord
import com.meucoach.app.story.ArtPhotoStore
import com.meucoach.app.story.ArtPngExport
import com.meucoach.app.story.ArtPngExporter
import com.meucoach.app.story.ArtRecipeStore
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryNotifier
import com.meucoach.app.story.StoryRecipeStore
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.epoch.DataWriteMutex
import com.meucoach.core.data.health.HealthConnectReadResult
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.data.repository.UserDataRepository
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataResetCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var userData: UserDataRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var activities: ActivityRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var healthStore: HealthSyncStore
    private lateinit var epochStore: DataEpochStore
    private lateinit var tutorial: TutorialStore
    private lateinit var firstRunMetrics: FirstRunMetricsStore
    private lateinit var firstRunTips: FirstRunTipStore
    private lateinit var storyFailures: StoryExportFailureStore
    private lateinit var storyIntro: StoryIntroStore
    private lateinit var storyRecipe: StoryRecipeStore
    private lateinit var artRecipeStore: ArtRecipeStore
    private lateinit var artPhotoStore: ArtPhotoStore
    private lateinit var artExporter: FakeArtExporter
    private lateinit var importedRunPrompts: ImportedRunPromptStore
    private lateinit var raceAutoCompletions: RaceAutoCompletionStore
    private lateinit var replanSuggestions: ReplanSuggestionStore
    private lateinit var levelGraduations: com.meucoach.app.level.LevelGraduationSuggestionStore
    private lateinit var healthSync: HealthSyncCoordinator
    private lateinit var coordinator: DataResetCoordinator
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(database, clock)
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        healthStore = HealthSyncStore(context, prefsName = "reset-health-${System.nanoTime()}")
        epochStore = DataEpochStore(context, clock, prefsName = "reset-epoch-${System.nanoTime()}")
        tutorial = TutorialStore(context, clock, prefsName = "reset-tutorial-${System.nanoTime()}")
        firstRunMetrics = FirstRunMetricsStore(
            context,
            clock,
            prefsName = "reset-metrics-${System.nanoTime()}",
        )
        firstRunTips = FirstRunTipStore(context, prefsName = "reset-tips-${System.nanoTime()}")
        storyFailures = StoryExportFailureStore(context)
        storyIntro = StoryIntroStore(context)
        storyRecipe = StoryRecipeStore(context)
        artRecipeStore = ArtRecipeStore(context)
        artPhotoStore = ArtPhotoStore(context)
        artExporter = FakeArtExporter()
        importedRunPrompts = ImportedRunPromptStore(context, "reset-import-prompts-${System.nanoTime()}")
        raceAutoCompletions = RaceAutoCompletionStore(context, "reset-race-auto-${System.nanoTime()}")
        replanSuggestions = ReplanSuggestionStore(context, "reset-replan-${System.nanoTime()}")
        levelGraduations =
            com.meucoach.app.level.LevelGraduationSuggestionStore(
                context,
                "reset-level-${System.nanoTime()}",
            )
        userData = UserDataRepository(database, healthStore, clock)
        val source = FakeSource()
        healthSync = HealthSyncCoordinator(
            source = source,
            service = HealthSyncService(
                source = source,
                metricsRepository = metrics,
                profileRepository = profiles,
                syncStore = healthStore,
                clock = clock,
                epochStore = epochStore,
                writeMutex = DataWriteMutex(),
            ),
            store = healthStore,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        coordinator = DataResetCoordinator(
            context = context,
            userData = userData,
            epochStore = epochStore,
            writeMutex = DataWriteMutex(),
            workManager = WorkManager.getInstance(context),
            staging = ImportStaging(context),
            photos = ProfilePhotoStore(context),
            importedRunPrompts = importedRunPrompts,
            raceAutoCompletions = raceAutoCompletions,
            replanSuggestions = replanSuggestions,
            profilePlanEvaluations = com.meucoach.app.replan.ProfilePlanEvaluationStore(),
            levelGraduations = levelGraduations,
            tutorial = tutorial,
            firstRunMetrics = firstRunMetrics,
            firstRunTips = firstRunTips,
            storyFailures = storyFailures,
            storyIntro = storyIntro,
            storyRecipe = storyRecipe,
            artRecipe = artRecipeStore,
            artPhotos = artPhotoStore,
            artExporter = artExporter,
            healthSync = healthSync,
            reminderNotifier = ReminderNotifier(context),
            importNotifier = ImportNotifier(context),
            storyNotifier = StoryNotifier(context),
        )
        clearStores()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `reset rotates the epoch and clears database, files and preferences`(): Unit = runBlocking {
        seedEverything()
        val oldEpoch = epochStore.current()

        val result = coordinator.reset()

        assertThat(result.epoch).isNotEqualTo(oldEpoch)
        assertThat(epochStore.current()).isEqualTo(result.epoch)
        assertThat(database.activityDao().getAllIncludingDeleted()).isEmpty()
        assertThat(database.profileDao().get()).isNull()
        assertThat(database.dailyCheckInDao().getAll()).isEmpty()
        assertThat(database.dailyMetricsDao().getAll()).isEmpty()
        assertThat(database.importJobDao().getAll()).isEmpty()
        assertThat(File(context.filesDir, "imports").exists()).isFalse()
        assertThat(File(context.filesDir, "profile").exists()).isFalse()
        assertThat(File(context.cacheDir, "stories").exists()).isFalse()
        assertThat(tutorial.isIntroDone()).isFalse()
        assertThat(firstRunTips.isShown("first-import")).isFalse()
        assertThat(importedRunPrompts.pending.value).isEmpty()
        assertThat(raceAutoCompletions.declinedActivityIds()).isEmpty()
        assertThat(replanSuggestions.isSuppressed(LocalDate.of(2026, 11, 6))).isFalse()
        assertThat(levelGraduations.isSuppressed(LocalDate.of(2026, 11, 6))).isFalse()
        assertThat(levelGraduations.declinedInputsHash("athlete-1")).isNull()
        assertThat(storyIntro.seen()).isFalse()
        assertThat(storyRecipe.load()).isEqualTo(StoryRecipe())
        assertThat(artRecipeStore.load()).isEqualTo(com.meucoach.core.domain.story.ArtRecipe())
        assertThat(storyFailures.messageFor("activity-1")).isNull()
        assertThat(healthStore.lastSyncedDate()).isNull()
        assertThat(healthSync.state.value.lastSyncedAt).isNull()
    }

    @Test
    fun `reset is a no-op for health connect and keeps the epoch store`(): Unit = runBlocking {
        seedEverything()
        val epoch = epochStore.current()

        val result = coordinator.reset()

        assertThat(result.epoch).isNotEqualTo(epoch)
        assertThat(epochStore.current()).isNotBlank()
        assertThat(healthStore.lastSyncedAt()).isNull()
    }

    @Test
    fun `reset cancels queued background work by tag`(): Unit = runBlocking {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<ResetTestWorker>()
            .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
            .addTag(ImportWorker.TAG)
            .build()
        workManager.enqueue(request).result.get()

        coordinator.reset()

        val infos = workManager.getWorkInfosByTag(ImportWorker.TAG).get()
        assertThat(infos).allMatch { it.state == WorkInfo.State.CANCELLED }
    }

    @Test
    fun `reset clears art photos cache and exporter while keeping no failures`(): Unit = runBlocking {
        seedEverything()
        seedArtPhoto("activity-1")
        seedArtPhoto("activity-2")
        val artCache = File(context.cacheDir, "art").apply { mkdirs() }
        File(artCache, "old-arte-4x5.png").writeText("png")
        File(artCache, "old-arte-foto-4x5.jpg").writeText("jpg")
        File(artCache, "pending.jpg.tmp").writeText("tmp")

        val result = coordinator.reset()

        assertThat(File(context.filesDir, ArtPhotoStore.DIRECTORY).exists()).isFalse()
        assertThat(File(context.cacheDir, "art").exists()).isFalse()
        assertThat(artExporter.invalidated).isTrue()
        assertThat(artRecipeStore.load()).isEqualTo(com.meucoach.core.domain.story.ArtRecipe())
        // Full reset keeps clearing the video recipe (existing behavior); the
        // art cleanup must not corrupt it.
        assertThat(storyRecipe.load()).isEqualTo(StoryRecipe())
        assertThat(result.failedCleanups).isEmpty()
    }

    @Test
    fun `reset aggregates art cleanup failures without throwing`(): Unit = runBlocking {
        seedEverything()
        val failing = DataResetCoordinator(
            context = context,
            userData = userData,
            epochStore = epochStore,
            writeMutex = DataWriteMutex(),
            workManager = WorkManager.getInstance(context),
            staging = ImportStaging(context),
            photos = ProfilePhotoStore(context),
            importedRunPrompts = importedRunPrompts,
            raceAutoCompletions = raceAutoCompletions,
            replanSuggestions = replanSuggestions,
            profilePlanEvaluations = com.meucoach.app.replan.ProfilePlanEvaluationStore(),
            levelGraduations = levelGraduations,
            tutorial = tutorial,
            firstRunMetrics = firstRunMetrics,
            firstRunTips = firstRunTips,
            storyFailures = storyFailures,
            storyIntro = storyIntro,
            storyRecipe = storyRecipe,
            artRecipe = artRecipeStore,
            artPhotos = artPhotoStore,
            artExporter = FailingArtExporter(),
            healthSync = healthSync,
            reminderNotifier = ReminderNotifier(context),
            importNotifier = ImportNotifier(context),
            storyNotifier = StoryNotifier(context),
        )

        val result = failing.reset()

        assertThat(result.failedCleanups).anyMatch { it.startsWith("exportador de arte") }
    }

    private suspend fun seedArtPhoto(activityId: String) {
        val photoId = java.util.UUID.randomUUID().toString()
        val bitmap = android.graphics.Bitmap.createBitmap(8, 6, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val file = artPhotoStore.photoFile(photoId)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        artPhotoStore.save(
            activityId,
            ArtPhotoRecord(
                com.meucoach.core.domain.story.ArtBackgroundMode.PHOTO,
                com.meucoach.core.domain.story.ArtPhotoRef(photoId, activityId),
            ),
        )
    }

    private suspend fun seedEverything() {
        profiles.saveProfile(
            AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                timezone = "America/Sao_Paulo",
            ),
        )
        checkIns.save(
            DailyCheckIn(
                date = LocalDate.of(2026, 11, 6),
                sleepQuality = 4,
                fatigue = 2,
                soreness = 1,
                stress = 2,
            ),
        )
        metrics.saveAll(listOf(DailyMetrics(date = LocalDate.of(2026, 11, 6), sleepDurationMin = 450)))
        activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = Instant.parse("2026-11-05T10:00:00Z"),
                durationSec = 1_800,
                distanceM = 5_000.0,
                averagePace = Pace.fromSecondsPerKm(360),
            ),
        ) as SaveActivityResult.Saved
        File(context.filesDir, "imports").mkdirs()
        File(context.filesDir, "imports/activity.fit").writeText("fit")
        File(context.filesDir, "profile").mkdirs()
        File(context.filesDir, "profile/avatar.jpg").writeText("avatar")
        File(context.cacheDir, "stories").mkdirs()
        File(context.cacheDir, "stories/map.png").writeText("map")
        tutorial.markIntroDone()
        firstRunMetrics.markTutorialStarted()
        firstRunTips.markShown("first-import")
        importedRunPrompts.add("activity-1")
        raceAutoCompletions.decline("activity-1")
        replanSuggestions.suppress(LocalDate.of(2026, 11, 6))
        levelGraduations.suppress(LocalDate.of(2026, 11, 6))
        levelGraduations.decline("athlete-1", "level-graduation-v1", "hash-a")
        storyFailures.save("activity-1", "erro")
        storyIntro.markSeen()
        storyRecipe.save(StoryRecipe(extras = listOf(StoryExtra.DATE)))
        artRecipeStore.save(
            com.meucoach.core.domain.story.ArtRecipe(font = com.meucoach.core.domain.story.ArtFont.ANTON),
        )
        healthStore.markSynced(Instant.parse("2026-11-06T10:00:00Z"), LocalDate.of(2026, 11, 6))
        healthSync.syncNow()
    }

    private fun clearStores() {
        tutorial.reset()
        firstRunMetrics.reset()
        firstRunTips.reset()
        replanSuggestions.reset()
        levelGraduations.reset()
        storyFailures.clearAll()
        storyIntro.reset()
        storyRecipe.reset()
        artRecipeStore.reset()
        healthStore.reset()
    }

    private class FakeSource : HealthDailySource {
        override val requiredPermissions: Set<String> = setOf("sleep")

        override suspend fun status(): HealthConnectStatus = HealthConnectStatus.AVAILABLE

        override suspend fun grantedPermissions(): Set<String> = requiredPermissions

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult =
            HealthConnectReadResult(emptyList(), emptySet())
    }

    private class FakeArtExporter : ArtPngExporter {
        var invalidated = false

        override suspend fun export(
            plan: com.meucoach.core.domain.story.StoryPlan,
            localDate: LocalDate?,
            recipe: com.meucoach.core.domain.story.ArtRecipe,
            activityId: String,
            forShare: Boolean,
        ): ArtPngExport = error("not used in reset")

        override suspend fun invalidate() {
            invalidated = true
        }
    }

    private class FailingArtExporter : ArtPngExporter {
        override suspend fun export(
            plan: com.meucoach.core.domain.story.StoryPlan,
            localDate: LocalDate?,
            recipe: com.meucoach.core.domain.story.ArtRecipe,
            activityId: String,
            forShare: Boolean,
        ): ArtPngExport = error("not used in reset")

        override suspend fun invalidate(): Unit = error("boom")
    }
}

private class ResetTestWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result = Result.success()
}
