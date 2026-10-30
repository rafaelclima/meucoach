package com.meucoach.app.importer

/** User-facing summary for a picker/share selection that was handed to WorkManager. */
fun importEnqueueMessage(result: ImportEnqueueResult): String {
    val failures = result.failures
    return when {
        result.queued == 0 && failures.size == 1 ->
            "${failures.single().fileName}: ${failures.single().message}"

        result.queued == 0 -> "${failures.size} arquivos não puderam ser preparados."

        failures.isEmpty() && result.queued == 1 ->
            "1 arquivo na fila. A importação roda em segundo plano."

        failures.isEmpty() -> "${result.queued} arquivos na fila. A importação roda em segundo plano."

        else -> "${result.queued} na fila; ${failures.size} não puderam ser preparados."
    }
}
