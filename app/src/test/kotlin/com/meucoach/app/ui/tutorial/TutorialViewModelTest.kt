package com.meucoach.app.ui.tutorial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: TutorialStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = TutorialStore(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts unseen when the intro was never completed`() {
        val viewModel = TutorialViewModel(store)

        assertThat(viewModel.uiState.value.seen).isFalse()
    }

    @Test
    fun `starts seen when the intro was already completed`() {
        store.markIntroDone()

        assertThat(TutorialViewModel(store).uiState.value.seen).isTrue()
    }

    @Test
    fun `marking seen persists the flag`() {
        val viewModel = TutorialViewModel(store)

        viewModel.markSeen()

        assertThat(viewModel.uiState.value.seen).isTrue()
        assertThat(store.isIntroDone()).isTrue()
    }
}
