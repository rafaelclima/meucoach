package com.meucoach.app.ui.coach

import com.meucoach.core.domain.coach.CoachResponse
import java.text.Normalizer

/**
 * Decides whether an open answer actually talks about today's session/targets.
 *
 * A generic definition ("o que é intervalado?") must not carry the
 * deterministic pace/FC card; an answer that says "hoje o treino é em ritmo
 * soltinho" or "seu pace está na referência" should. The check is deliberately
 * conservative: only explicit target/session words, never the generic
 * "treino"/"correr" that a definition can contain.
 */
internal fun responseCitesSessionTargets(response: CoachResponse): Boolean = citesSessionTargets(
    (listOf(response.title, response.body) + response.bullets).joinToString(" "),
)

internal fun citesSessionTargets(text: String): Boolean {
    val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
    return sessionTargetPatterns.any { it.containsMatchIn(normalized) }
}

private val sessionTargetPatterns = listOf(
    Regex("""\bpace\b"""),
    Regex("""\britmo\b"""),
    Regex("""\bfc\b"""),
    Regex("""\bbpm\b"""),
    Regex("""\bzona\b"""),
    Regex("""\besforco\b"""),
    Regex("""\bprescrit\w*"""),
    Regex("""\breferencia\b"""),
    Regex("""\btreino de hoje\b"""),
    Regex("""\bsessao de hoje\b"""),
    Regex("""\btreino prescrito\b"""),
    Regex("""\bsessao prescrita\b"""),
)
