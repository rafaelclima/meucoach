package com.meucoach.app.story

/**
 * User-facing copy shared by the detail screen and the background export
 * (ADR 0018). One place, so the tone cannot drift between states.
 */
object StoryMessages {
    const val MISSING_GPS = "Esta atividade não tem rota GPS suficiente para um story."

    /** Map fetch failure (network or tile source) in the preview and in the worker. */
    const val MAP_UNAVAILABLE = "Não foi possível baixar o mapa. Verifique a conexão e tente novamente."

    const val MAP_TIMEOUT_MS = 45_000L

    /** Failure to stage the rendered map into the cache before enqueueing. */
    const val MAP_STAGE_FAILED = "Não foi possível preparar o story para gerar em segundo plano."

    const val EXPORT_FAILED = "Não foi possível gerar o vídeo."
}
