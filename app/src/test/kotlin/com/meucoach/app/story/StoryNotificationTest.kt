package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoryNotificationTest {
    @Test
    fun `progress copy covers preparing, percent and eta`() {
        assertThat(StoryNotification.progressText(progress = 0f, etaSec = null))
            .isEqualTo("Preparando o mapa…")
        assertThat(StoryNotification.progressText(progress = 0.42f, etaSec = 15))
            .isEqualTo("42% · cerca de 15s restantes")
        assertThat(StoryNotification.progressText(progress = 0.42f, etaSec = null))
            .isEqualTo("42% concluído")
        assertThat(StoryNotification.progressText(progress = 1f, etaSec = 0))
            .isEqualTo("Finalizando…")
    }
}
