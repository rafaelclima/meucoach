package com.meucoach.app.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ImportMessagesTest {
    @Test
    fun `describes queued files`() {
        assertThat(importEnqueueMessage(ImportEnqueueResult(queued = 1)))
            .isEqualTo("1 arquivo na fila. A importação roda em segundo plano.")
        assertThat(importEnqueueMessage(ImportEnqueueResult(queued = 3)))
            .isEqualTo("3 arquivos na fila. A importação roda em segundo plano.")
    }

    @Test
    fun `describes a single failure`() {
        val result = ImportEnqueueResult(
            queued = 0,
            failures = listOf(ImportEnqueueFailure("sumiu.fit", "Não foi possível abrir o arquivo.")),
        )

        assertThat(importEnqueueMessage(result))
            .isEqualTo("sumiu.fit: Não foi possível abrir o arquivo.")
    }

    @Test
    fun `describes partial failures`() {
        val result = ImportEnqueueResult(
            queued = 2,
            failures = listOf(
                ImportEnqueueFailure("a.fit", "falhou"),
                ImportEnqueueFailure("b.fit", "falhou"),
            ),
        )

        assertThat(importEnqueueMessage(result)).isEqualTo("2 na fila; 2 não puderam ser preparados.")
    }
}
