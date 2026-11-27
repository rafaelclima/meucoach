package com.meucoach.core.domain.content

/**
 * Canonical forbidden vocabulary for user-facing text (ADR 0071).
 *
 * The 28 entries come from ADR 0071 §2.1 (one per table row); [MOTOR_TERM]
 * comes from the voice rule in §2.4 (the internal name "motor" never appears
 * on screen, user-0 decision §2.10). Together they are the single source the
 * domain gate and the UI gate check against: the list never grows without a
 * new ADR decision, and the baseline files only shrink.
 *
 * Matching notes (all implemented as data, so the rules stay auditable):
 * - acronyms and enum names match whole words; enum names (`PEAK`, `BUILD`,
 *   `BASE`, `RECOVERY`) match all-caps only so the canonical labels ("Base",
 *   "Construção", "Pico", "Recuperação", "Semana de prova") never flag;
 * - `TAPER` is covered by the case-insensitive "taper" entry, so the enum
 *   entry skips it to avoid double reports;
 * - "Fácil" matches capital-F only (a session name) unless it is the zone
 *   label "Fácil (Z2)"; lowercase "fácil" stays valid as an adjective
 *   ("dia fácil", "sessão fácil");
 * - "pace" matches everywhere except inside the approved title
 *   "Pace de prova";
 * - `R-`/`EV-` match rule/evidence IDs; the gate never scans the collapsed
 *   "Ver fontes e detalhes técnicos" block, where the IDs are allowed.
 */
data class ForbiddenTerm(
    val display: String,
    val patterns: List<Regex>,
    val exceptions: List<Regex> = emptyList(),
    val source: String,
)

/** One forbidden occurrence: which term matched and the surrounding excerpt. */
data class VocabularyViolation(val term: ForbiddenTerm, val matched: String, val excerpt: String)

object ForbiddenVocabulary {
    private const val ADR_BLACKLIST = "ADR 0071 §2.1"
    private const val ADR_VOICE = "ADR 0071 §2.4"

    private val acwr = ForbiddenTerm(
        display = "ACWR",
        patterns = listOf(Regex("\\bACWR\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val ctlAtlTsb = ForbiddenTerm(
        display = "CTL / ATL / TSB",
        patterns = listOf(
            Regex("\\bCTL\\b", RegexOption.IGNORE_CASE),
            Regex("\\bATL\\b", RegexOption.IGNORE_CASE),
            Regex("\\bTSB\\b", RegexOption.IGNORE_CASE),
        ),
        source = ADR_BLACKLIST,
    )

    private val trimpRtss = ForbiddenTerm(
        display = "TRIMP / rTSS",
        patterns = listOf(
            Regex("\\bTRIMP\\b", RegexOption.IGNORE_CASE),
            Regex("\\brTSS\\b", RegexOption.IGNORE_CASE),
        ),
        source = ADR_BLACKLIST,
    )

    private val srpe = ForbiddenTerm(
        display = "sRPE",
        patterns = listOf(Regex("\\bsRPE\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val lthr = ForbiddenTerm(
        display = "LTHR",
        patterns = listOf(Regex("\\bLTHR\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val vdot = ForbiddenTerm(
        display = "VDOT",
        patterns = listOf(Regex("\\bVDOT\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val tier = ForbiddenTerm(
        display = "Tier A / Tier B / Tier C",
        patterns = listOf(Regex("\\bTier\\s+[ABC]\\b")),
        source = ADR_BLACKLIST,
    )

    private val monotonia = ForbiddenTerm(
        display = "monotonia / monótona",
        patterns = listOf(
            Regex("\\bmonotoni\\w*", RegexOption.IGNORE_CASE),
            Regex("\\bmonóton\\w*", RegexOption.IGNORE_CASE),
        ),
        source = ADR_BLACKLIST,
    )

    private val strain = ForbiddenTerm(
        display = "strain",
        patterns = listOf(Regex("\\bstrain\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val guardrails = ForbiddenTerm(
        display = "guardrails",
        patterns = listOf(Regex("\\bguardrails?\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val strides = ForbiddenTerm(
        display = "Strides / strides",
        patterns = listOf(Regex("\\bstrides\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val colinas = ForbiddenTerm(
        display = "Colinas",
        patterns = listOf(Regex("\\bcolinas?\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val facil = ForbiddenTerm(
        display = "Fácil",
        patterns = listOf(Regex("\\bFácil\\b")),
        exceptions = listOf(Regex("Fácil\\s*\\(Z2\\)")),
        source = ADR_BLACKLIST,
    )

    private val taper = ForbiddenTerm(
        display = "taper",
        patterns = listOf(Regex("\\btaper\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val deload = ForbiddenTerm(
        display = "deload",
        patterns = listOf(Regex("\\bdeload\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val mesocicloBloco = ForbiddenTerm(
        display = "mesociclo / bloco",
        patterns = listOf(
            Regex("\\bmesociclo\\b", RegexOption.IGNORE_CASE),
            Regex("\\bbloco\\b", RegexOption.IGNORE_CASE),
        ),
        source = ADR_BLACKLIST,
    )

    private val qualidade = ForbiddenTerm(
        display = "qualidade",
        patterns = listOf(Regex("\\bqualidades?\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val pace = ForbiddenTerm(
        display = "pace",
        patterns = listOf(Regex("\\bpace\\b", RegexOption.IGNORE_CASE)),
        exceptions = listOf(Regex("\\bpace\\s+de\\s+prova\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val splits = ForbiddenTerm(
        display = "splits",
        patterns = listOf(Regex("\\bsplits?\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val microDose = ForbiddenTerm(
        display = "micro-dose",
        patterns = listOf(Regex("\\bmicro[-\\s]?dose\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val blobTiles = ForbiddenTerm(
        display = "blob / tiles",
        patterns = listOf(
            Regex("\\bblob\\b", RegexOption.IGNORE_CASE),
            Regex("\\btiles?\\b", RegexOption.IGNORE_CASE),
        ),
        source = ADR_BLACKLIST,
    )

    private val analytics = ForbiddenTerm(
        display = "analytics",
        patterns = listOf(Regex("\\banalytics\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val especificidade = ForbiddenTerm(
        display = "especificidade",
        patterns = listOf(Regex("\\bespecificidade\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val performance = ForbiddenTerm(
        display = "performance",
        patterns = listOf(Regex("\\bperformance\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val riegel = ForbiddenTerm(
        display = "Riegel",
        patterns = listOf(Regex("\\briegel\\b", RegexOption.IGNORE_CASE)),
        source = ADR_BLACKLIST,
    )

    private val tt = ForbiddenTerm(
        display = "TT",
        patterns = listOf(Regex("\\bTT\\b")),
        source = ADR_BLACKLIST,
    )

    private val mesoPhaseEnum = ForbiddenTerm(
        display = "PEAK / BUILD / TAPER / BASE / RECOVERY",
        patterns = listOf(
            Regex("\\bPEAK\\b"),
            Regex("\\bBUILD\\b"),
            Regex("\\bBASE\\b"),
            Regex("\\bRECOVERY\\b"),
        ),
        source = ADR_BLACKLIST,
    )

    private val ruleEvidenceIds = ForbiddenTerm(
        display = "R- / EV-",
        patterns = listOf(
            Regex("\\bR-[A-Z]+-\\d+\\b"),
            Regex("\\bEV-[A-Z]+-\\d+\\b"),
        ),
        source = ADR_BLACKLIST,
    )

    private val motor = ForbiddenTerm(
        display = "motor",
        patterns = listOf(Regex("\\bmotor\\b", RegexOption.IGNORE_CASE)),
        source = ADR_VOICE,
    )

    /**
     * The 28 blacklist rows of ADR 0071 §2.1, plus "motor" (§2.4).
     * Never grows without a new ADR decision; the baseline only shrinks.
     */
    val TERMS: List<ForbiddenTerm> = listOf(
        acwr,
        ctlAtlTsb,
        trimpRtss,
        srpe,
        lthr,
        vdot,
        tier,
        monotonia,
        strain,
        guardrails,
        strides,
        colinas,
        facil,
        taper,
        deload,
        mesocicloBloco,
        qualidade,
        pace,
        splits,
        microDose,
        blobTiles,
        analytics,
        especificidade,
        performance,
        riegel,
        tt,
        mesoPhaseEnum,
        ruleEvidenceIds,
        motor,
    )

    /** Display names in canonical order, mirrored by the golden fixtures. */
    val DISPLAYS: List<String> = TERMS.map { it.display }

    fun findViolations(text: String): List<VocabularyViolation> {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.isBlank()) return emptyList()
        return TERMS.flatMap { term ->
            val exempt = term.exceptions.flatMap { exception -> exception.findAll(flat).map { it.range } }
            term.patterns.flatMap { pattern -> pattern.findAll(flat) }
                .filter { match ->
                    exempt.none { span -> match.range.first >= span.first && match.range.last <= span.last }
                }
                .map { match ->
                    VocabularyViolation(
                        term = term,
                        matched = match.value,
                        excerpt = window(flat, match.range),
                    )
                }
        }.distinctBy { violation -> Triple(violation.term.display, violation.matched, violation.excerpt) }
            .sortedWith(compareBy({ it.term.display }, { it.excerpt }))
    }

    fun violates(text: String): Boolean = findViolations(text).isNotEmpty()

    private fun window(flat: String, range: IntRange): String {
        val from = (range.first - EXCERPT_RADIUS).coerceAtLeast(0)
        val to = (range.last + 1 + EXCERPT_RADIUS).coerceAtMost(flat.length)
        val prefix = if (from > 0) "… " else ""
        val suffix = if (to < flat.length) " …" else ""
        return prefix + flat.substring(from, to).trim() + suffix
    }

    private const val EXCERPT_RADIUS = 60
}
