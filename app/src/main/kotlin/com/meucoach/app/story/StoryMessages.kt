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

    /** The art CTAs are disabled without a route; this copy covers races. */
    const val ART_NO_ROUTE = "Sem rota suficiente para a arte."

    /** Render/publish failure of the art card; the technical detail stays in the log. */
    const val ART_EXPORT_FAILED = "Não foi possível gerar o PNG da arte. Tente novamente."
}
