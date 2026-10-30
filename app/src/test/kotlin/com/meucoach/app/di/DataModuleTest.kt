package com.meucoach.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataModuleTest {
    @Test
    fun `provides the database and activity repository`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(MeuCoachDatabase.NAME)

        val database = DataModule.provideDatabase(context)
        try {
            assertThat(database.openHelper.readableDatabase.version)
                .isEqualTo(MeuCoachDatabase.VERSION)
            assertThat(DataModule.provideActivityRepository(database))
                .isInstanceOf(ActivityRepository::class.java)
        } finally {
            database.close()
            context.deleteDatabase(MeuCoachDatabase.NAME)
        }
    }
}
