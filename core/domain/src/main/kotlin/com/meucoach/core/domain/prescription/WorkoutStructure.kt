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

    /**
     * Shrinks the block to [maxSeconds], preserving its kind and target.
     *
     * Repetition blocks lose repetitions first; a single repetition then loses
     * work/recovery time. Continuous blocks shrink their work time. One second
     * is the floor so a block is never emitted with a non-positive duration.
     */
    internal fun fittedTo(maxSeconds: Long): WorkoutBlock {
        if (plannedSeconds <= maxSeconds) return this
        val work = requireNotNull(workSec) { "Block work duration must be positive" }
        val recovery = recoverySec?.toLong() ?: 0L
        val repetitions = repetitions
        if (repetitions == null) {
            return copy(workSec = maxSeconds.coerceIn(1L, work.toLong()).toInt())
        }
        val cycle = work.toLong() + recovery
        val fittedRepetitions = (maxSeconds / cycle).toInt().coerceIn(1, repetitions)
        if (fittedRepetitions > 1 || cycle <= maxSeconds) {
            return copy(repetitions = fittedRepetitions)
        }
        if (recovery == 0L) {
            return copy(repetitions = 1, workSec = maxSeconds.coerceIn(1L, work.toLong()).toInt())
        }
        val fittedRecovery = recovery.coerceIn(1L, (maxSeconds - 1).coerceAtLeast(1L))
        val fittedWork = (maxSeconds - fittedRecovery).coerceIn(1L, work.toLong()).toInt()
        return copy(
            repetitions = 1,
            workSec = fittedWork,
            recoverySec = fittedRecovery.toInt(),
        )
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

    /**
     * Fits the executable blocks into a real session duration (ADR 0047 §2.1).
     *
     * A longer session keeps the prescribed work and raises [totalMin]; the
     * spare time is easy running described by the plan copy. A shorter session
     * shrinks repetitions or a continuous block so the structure never promises
     * more than the athlete has time for. Warm-up and cool-down are fixed: a
     * duration that cannot hold them is invalid, not silently cut.
     */
    fun fittedTo(minutes: Int): WorkoutStructure {
        require(minutes > 0) { "Session duration must be positive" }
        val budget = minutes.toLong() * SECONDS_PER_MINUTE
        if (plannedSeconds <= budget) return copy(totalMin = minutes)
        val fixedSeconds = (warmupMin + cooldownMin).toLong() * SECONDS_PER_MINUTE
        require(fixedSeconds < budget) {
            "Warm-up and cool-down must fit in $minutes min"
        }
        return copy(blocks = fitBlocks(blocks, budget - fixedSeconds), totalMin = minutes)
    }

    private fun fitBlocks(source: List<WorkoutBlock>, budget: Long): List<WorkoutBlock> {
        require(budget >= source.size) { "Not enough time for every block" }
        val totalWeight = source.sumOf { it.plannedSeconds }.coerceAtLeast(1L)
        var remaining = budget
        return source.mapIndexed { index, block ->
            val left = source.size - index - 1
            val share = if (index == source.lastIndex) {
                remaining
            } else {
                budget * block.plannedSeconds / totalWeight
            }
            val fittedShare = share.coerceIn(1L, remaining - left)
            remaining -= fittedShare
            block.fittedTo(fittedShare)
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
