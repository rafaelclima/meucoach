package com.meucoach.app.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ImportFailurePolicyTest {
    @Test
    fun `retries a transient read error while attempts remain`() {
        assertThat(ImportFailurePolicy.decide(IOException("leitura"), runAttemptCount = 0))
            .isEqualTo(ImportFailurePolicy.Action.RETRY)
        assertThat(ImportFailurePolicy.decide(IOException("leitura"), runAttemptCount = 1))
            .isEqualTo(ImportFailurePolicy.Action.RETRY)
    }

    @Test
    fun `gives up after the attempt budget`() {
        assertThat(ImportFailurePolicy.decide(IOException("leitura"), runAttemptCount = 2))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
        assertThat(ImportFailurePolicy.decide(IOException("leitura"), runAttemptCount = 7))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
    }

    @Test
    fun `a missing staged file never comes back`() {
        assertThat(ImportFailurePolicy.decide(FileNotFoundException("sumiu"), runAttemptCount = 0))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
    }

    @Test
    fun `a staging error is permanent from the first attempt`() {
        assertThat(ImportFailurePolicy.decide(ImportStagingException("O arquivo está vazio."), runAttemptCount = 0))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
    }

    @Test
    fun `denied access does not improve with retries`() {
        assertThat(ImportFailurePolicy.decide(SecurityException("negado"), runAttemptCount = 0))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
    }

    @Test
    fun `a programming error is not retried`() {
        assertThat(ImportFailurePolicy.decide(IllegalStateException("boom"), runAttemptCount = 0))
            .isEqualTo(ImportFailurePolicy.Action.GIVE_UP)
    }

    @Test
    fun `only staging copy reaches the user`() {
        assertThat(ImportFailurePolicy.message(ImportStagingException("Arquivo maior que 64 MB.")))
            .isEqualTo("Arquivo maior que 64 MB.")
    }

    @Test
    fun `internal errors use generic copy`() {
        assertThat(ImportFailurePolicy.message(IllegalStateException("/data/user/0/segredo")))
            .isEqualTo(ImportFailurePolicy.GIVE_UP_MESSAGE)
    }
}
