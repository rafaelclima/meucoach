package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryIntroStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context.getSharedPreferences("meucoach_story_intro", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `shows the notice until it is dismissed once`() {
        val store = StoryIntroStore(context)

        assertThat(store.seen()).isFalse()
        store.markSeen()
        assertThat(StoryIntroStore(context).seen()).isTrue()
    }
}
