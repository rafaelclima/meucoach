package com.meucoach.core.domain.replan

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.prescription.longRunFloorMinutes
import com.meucoach.core.model.Experience
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Outcome of the idempotent engine upgrade of the current week (ADR 0048 §2.2). */
sealed interface PlanUpgradeResult {
    /** The merged week with [WeeklyPlan.engineVersion] already set to the target. */
    data class Upgraded(val plan: WeeklyPlan) : PlanUpgradeResult

    /** Nothing was written; the stored week stays exactly as it was. */
    data class Skipped(val reason: String) : PlanUpgradeResult {
        init {
            require(reason.isNotBlank()) { "Skip reason must not be blank" }
        }
    }
}

/**
 * Pure, deterministic upgrade of a stored week to a newer generator version.
 *
 * The merge preserves today, the past and every future `DONE`/`CLOSED` session,
 * replaces only strictly future `PLANNED` sessions with the regenerated ones
 * and allows a single structural quality slot when the current future has none.
 * It never grows future volume, never adds an extra long run and never adds a
 * second quality. Running it twice is a no-op once the stored version matches.
 */
object PlanUpgrade {
    /** Revision trigger recorded by the repository when the upgrade is applied. */
    const val ENGINE_UPGRADE_TRIGGER = "ENGINE_UPGRADE"

    fun needsUpgrade(storedVersion: String, targetVersion: String): Boolean = storedVersion != targetVersion

    fun upgrade(
        current: WeeklyPlan,
        regenerated: WeeklyPlan,
        today: LocalDate,
        targetVersion: String,
        experience: Experience? = null,
    ): PlanUpgradeResult {
        require(targetVersion.isNotBlank()) { "Target version must not be blank" }
        val preserved = current.sessions.filter {
            !it.date.isAfter(today) || it.status != SessionStatus.PLANNED
        }
        val preservedDates = preserved.map { it.date }.toSet()
        val candidateFuture = regenerated.sessions.filter {
            it.date.isAfter(today) && it.date !in preservedDates
        }
        val currentFuture = current.sessions.filter {
            it.date.isAfter(today) && it.status == SessionStatus.PLANNED
        }
        val mergedSessions = (preserved + candidateFuture).sortedBy { it.date }
        val skipReason = preconditions(
            current = current,
            regenerated = regenerated,
            today = today,
            targetVersion = targetVersion,
            candidateFuture = candidateFuture,
            currentFuture = currentFuture,
        )
            ?: ceilingFailure(currentFuture, candidateFuture, experience)
            ?: validationFailure(mergedSessions, candidateFuture, today)
            ?: sameProjectionReason(currentFuture, candidateFuture)
        if (skipReason != null) return PlanUpgradeResult.Skipped(skipReason)

        val merged = WeeklyPlan(
            weekStart = current.weekStart,
            sessions = mergedSessions,
            weeklyMinutes = mergedSessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = mergedSessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
            qualityCount = mergedSessions.count { it.type == WorkoutType.QUALITY },
            rationale = regenerated.rationale,
            confidence = regenerated.confidence,
            ruleIds = regenerated.ruleIds,
            evidenceIds = regenerated.evidenceIds,
            createdOn = current.createdOn,
            engineVersion = targetVersion,
        )
        return PlanUpgradeResult.Upgraded(merged)
    }

    private fun preconditions(
        current: WeeklyPlan,
        regenerated: WeeklyPlan,
        today: LocalDate,
        targetVersion: String,
        candidateFuture: List<PlannedSession>,
        currentFuture: List<PlannedSession>,
    ): String? = when {
        !needsUpgrade(current.engineVersion, targetVersion) ->
            "O plano já está na versão atual do motor."

        regenerated.weekStart != current.weekStart ->
            "A semana regenerada pertence a outra semana."

        current.weekStart != currentWeekStart(today) ->
            "Só a semana vigente pode ser atualizada."

        candidateFuture.none { it.status == SessionStatus.PLANNED } && currentFuture.isEmpty() ->
            "Não há dias futuros para atualizar."

        else -> null
    }

    private fun ceilingFailure(
        currentFuture: List<PlannedSession>,
        candidateFuture: List<PlannedSession>,
        experience: Experience?,
    ): String? {
        val current = currentFuture.filter { it.status == SessionStatus.PLANNED }
        val candidate = candidateFuture.filter { it.status == SessionStatus.PLANNED }
        val currentVolume = current.sumOf { it.durationMin ?: 0 }
        val candidateVolume = candidate.sumOf { it.durationMin ?: 0 }
        val currentLong = current.filter { it.type == WorkoutType.LONG }.sumOf { it.durationMin ?: 0 }
        val candidateLong = candidate.filter { it.type == WorkoutType.LONG }.sumOf { it.durationMin ?: 0 }
        val currentOther = currentVolume - currentLong
        val candidateOther = candidateVolume - candidateLong
        val floor = experience?.let { longRunFloorMinutes(it) }
        val floorCorrection = floor != null &&
            currentLong in 1 until floor &&
            candidateLong in (currentLong + 1)..floor &&
            candidateOther <= currentOther
        val currentQuality = current.count { it.type == WorkoutType.QUALITY }
        val candidateQuality = candidate.count { it.type == WorkoutType.QUALITY }
        val singleSlot = currentQuality == 0 && candidateQuality == 1
        return when {
            candidateVolume > currentVolume && !floorCorrection ->
                "A atualização não pode aumentar o volume futuro da semana."

            candidateQuality > currentQuality && !singleSlot ->
                "A atualização adiciona no máximo uma oportunidade estrutural de qualidade."

            candidate.count { it.type == WorkoutType.LONG } > current.count { it.type == WorkoutType.LONG } ->
                "A atualização não adiciona longão extra."

            candidateLong > currentLong && !floorCorrection ->
                "A atualização não cresce o longão além do piso."

            else -> null
        }
    }

    private fun validationFailure(
        merged: List<PlannedSession>,
        future: List<PlannedSession>,
        today: LocalDate,
    ): String? {
        val effective = merged.filter {
            it.status == SessionStatus.DONE ||
                (it.status == SessionStatus.PLANNED && it.date.isAfter(today))
        }
        val qualities = effective.filter { it.type == WorkoutType.QUALITY }
        val longDays = merged.filter { it.type == WorkoutType.LONG }.map { it.date.dayOfWeek }
        return when {
            qualities.size > MAX_QUALITIES ->
                "O plano permite no máximo $MAX_QUALITIES treinos fortes por semana."

            longDays.size > 1 ->
                "O plano permite um único longão por semana."

            qualities.any { quality ->
                longDays.any { it == quality.date.dayOfWeek || adjacent(it, quality.date.dayOfWeek) }
            } ->
                "Treino forte não pode ficar no dia do longão nem ao lado dele."

            qualities.indices.any { index ->
                qualities.drop(index + 1).any { other ->
                    adjacent(qualities[index].date.dayOfWeek, other.date.dayOfWeek)
                }
            } ->
                "Não deixe dois treinos fortes em dias consecutivos."

            future.any { it.status != SessionStatus.PLANNED } ->
                "A atualização só propõe sessões futuras planejadas."

            else -> null
        }
    }

    private fun sameProjectionReason(
        currentFuture: List<PlannedSession>,
        candidateFuture: List<PlannedSession>,
    ): String? = if (sameProjection(currentFuture, candidateFuture)) {
        "O resto da semana já está como deveria; nada a atualizar."
    } else {
        null
    }

    private fun sameProjection(currentFuture: List<PlannedSession>, candidateFuture: List<PlannedSession>): Boolean {
        val current = currentFuture.map { it.projection() }.sortedWith(PROJECTION_ORDER)
        val candidate = candidateFuture.map { it.projection() }.sortedWith(PROJECTION_ORDER)
        return current == candidate
    }

    private const val MAX_QUALITIES = 2
}

private data class UpgradeProjection(
    val date: LocalDate,
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
)

private val PROJECTION_ORDER: Comparator<UpgradeProjection> = compareBy(
    { it.date },
    { it.type },
    { it.title },
    { it.durationMin ?: -1 },
)

private fun PlannedSession.projection(): UpgradeProjection = UpgradeProjection(
    date = date,
    type = type,
    title = title,
    durationMin = durationMin,
    ruleIds = ruleIds,
    evidenceIds = evidenceIds,
)

private fun currentWeekStart(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun adjacent(a: DayOfWeek, b: DayOfWeek): Boolean {
    val diff = abs(a.value - b.value)
    return diff == 1 || diff == 6
}
