package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryRecipe
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * WorkManager wiring for the background export (ADR 0018). Worker execution is
 * covered by [StoryExportProcessorTest]; here only the request contract and the
 * map staging are checked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryExportCoordinatorTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var coordinator: WorkManagerStoryExportCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        coordinator = WorkManagerStoryExportCoordinator(context, workManager)
    }

    @Test
    fun `names the work uniquely per activity`() {
        assertThat(StoryExportCoordinator.uniqueName("activity-1")).isEqualTo("story-export-activity-1")
        assertThat(StoryExportCoordinator.uniqueName("activity-2")).isEqualTo("story-export-activity-2")
    }

    @Test
    fun `builds the input with palette recipe and optional map`() {
        val recipe = StoryRecipe(extras = listOf(StoryExtra.DATE))
        val withMap = StoryExportCoordinator.buildInput("a-1", recipe, "present.png", testPalette())
        assertThat(withMap.getString(StoryExportWorker.KEY_ACTIVITY_ID)).isEqualTo("a-1")
        assertThat(withMap.getString(StoryExportWorker.KEY_MAP_PATH)).isEqualTo("present.png")
        assertThat(withMap.getString(StoryExportWorker.KEY_RECIPE_JSON))
            .isEqualTo(StoryRecipeCodec.encode(recipe))
        assertThat(withMap.getString(StoryExportWorker.KEY_RECIPE_JSON)!!.toByteArray().size)
            .isLessThanOrEqualTo(StoryRecipeCodec.MAX_PAYLOAD_BYTES)
        assertThat(
            withMap.getIntArray(StoryPaletteCodec.KEY),
        ).isEqualTo(StoryPaletteCodec.encode(testPalette()))

        val withoutMap = StoryExportCoordinator.buildInput("a-1", recipe, null, testPalette())
        assertThat(withoutMap.getString(StoryExportWorker.KEY_MAP_PATH)).isNull()
    }

    @Test
    fun `stages the preview map and enqueues tagged work`() {
        runBlocking {
            coordinator.start(
                "activity-1",
                StoryRecipe(),
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                testPalette(),
            )
        }

        val staged = File(context.cacheDir, "stories/story-map-activity-1.png")
        assertThat(staged.isFile).isTrue()

        val work = workManager
            .getWorkInfosForUniqueWork(StoryExportCoordinator.uniqueName("activity-1"))
            .get()
        assertThat(work).isNotEmpty
        assertThat(work.first().tags).contains(StoryExportWorker.TAG)
        assertThat(work.first().state).isIn(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.SUCCEEDED,
            WorkInfo.State.FAILED,
        )
    }

    @Test
    fun `does not enqueue when cancelled while staging the map`() {
        runBlocking {
            coordinator.start(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                mapBackground = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                palette = testPalette(),
                isCancelled = { true },
            )
        }

        assertThat(
            workManager.getWorkInfosForUniqueWork(StoryExportCoordinator.uniqueName("activity-1")).get(),
        ).isEmpty()
        assertThat(File(context.cacheDir, "stories/story-map-activity-1.png").exists()).isFalse()
    }

    private fun testPalette() = StoryPalette(
        background = 1,
        surface = 2,
        surfaceInset = 3,
        border = 4,
        textPrimary = 5,
        textSecondary = 6,
        textTertiary = 7,
        accent = 8,
        coral = 9,
        onAccent = 10,
        mapCasing = 11,
        mapBaseRoute = 12,
        zone1 = 13,
        zone2 = 14,
        zone3 = 15,
        zone4 = 16,
        zone5 = 17,
    )
}
