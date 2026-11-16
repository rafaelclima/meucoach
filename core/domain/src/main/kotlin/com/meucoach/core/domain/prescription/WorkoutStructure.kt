package com.meucoach.core.domain.prescription

/** Kind of executable block inside a workout structure (ADR 0047 §2.1). */
enum class BlockKind {
    WORK,
    RECOVERY,
    STRIDES,
    RACE_PACE,
}

/**
 * One executable block of a session (ADR 0047 §2.1).
 *
 * [repetitions] is `null` for a single continuous block. [workSec] is always the
 * duration of one repetition (or of the whole continuous block). [recoverySec]
 * is the easy interval after a repetition and is counted after every one of
 * them, including the last, so the estimate never undercounts the time the
 * session needs.
 *
 * A [BlockKind.RECOVERY] block is a standalone easy interval; it cannot nest
 * repetitions nor another recovery.
 */
data class WorkoutBlock(
    val kind: BlockKind,
    val repetitions: Int? = null,
    val workSec: Int? = null,
    val recoverySec: Int? = null,
    val target: String? = null,
    val note: String? = null,
) {
    init {
        require(repetitions == null || repetitions > 0) { "Block repetitions must be positive" }
        require(workSec != null && workSec > 0) { "Block work duration must be positive" }
        require(recoverySec == null || recoverySec > 0) { "Block recovery must be positive" }
        require(kind != BlockKind.RECOVERY || repetitions == null) {
            "A recovery block is a single continuous block"
        }
        require(kind != BlockKind.RECOVERY || recoverySec == null) {
            "A recovery block cannot carry another recovery"
        }
        require(target == null || target.isNotBlank()) { "Block target must not be blank" }
        require(note == null || note.isNotBlank()) { "Block note must not be blank" }
    }

    /** Repetitions of [workSec]; a continuous block repeats once. */
    val effectiveRepetitions: Int get() = repetitions ?: 1

    /** Conservative time this block needs, including recovery after every repetition. */
    val plannedSeconds: Long
        get() {
            val work = workSec?.toLong() ?: 0L
            val recovery = recoverySec?.toLong() ?: 0L
            return effectiveRepetitions.toLong() * (work + recovery)
        }
}

/**
 * Executable prescription of a session (ADR 0047 §2.1, PRD §5.3).
 *
 * The invariant is what protects the athlete from an impossible session:
 * warm-up + blocks + recoveries + cool-down never exceed [totalMin]. The
 * structure carries no pace and no clock; targets are labels resolved by the
 * briefing and the targets of the prescription.
 */
data class WorkoutStructure(
    val version: Int = 1,
    val warmupMin: Int,
    val blocks: List<WorkoutBlock>,
    val cooldownMin: Int,
    val totalMin: Int,
) {
    init {
        require(version >= 1) { "Structure version must be positive" }
        require(warmupMin >= 0) { "Warm-up must not be negative" }
        require(cooldownMin >= 0) { "Cool-down must not be negative" }
        require(totalMin > 0) { "Structure total must be positive" }
        require(blocks.isNotEmpty()) { "Structure must have at least one block" }
        require(plannedSeconds <= totalMin.toLong() * SECONDS_PER_MINUTE) {
            "Workout must fit in $totalMin min (planned ${plannedSeconds}s)"
        }
    }

    /** Warm-up + every block + cool-down, in seconds. */
    val plannedSeconds: Long
        get() = warmupMin.toLong() * SECONDS_PER_MINUTE +
            blocks.sumOf { it.plannedSeconds } +
            cooldownMin.toLong() * SECONDS_PER_MINUTE

    /** Rounded-up planned time; a partial minute still occupies the session. */
    val plannedMinutes: Int
        get() = ((plannedSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt()

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
