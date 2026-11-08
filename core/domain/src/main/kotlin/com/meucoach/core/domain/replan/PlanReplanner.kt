package com.meucoach.core.domain.replan

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.WeeklyAvailability
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Pure, deterministic replanner for the current week (ADR 0028, R-01).
 *
 * The replanner never creates a training rule: it regenerates the strictly
 * future days with [WeeklyPlanGenerator], merges them with the sessions it must
 * preserve and validates the full week (quality/long-run limits and spacing,
 * availability, weekly aggregates). Sessions of today and of the past, and
 * future `DONE`/`CLOSED` sessions, are copied untouched.
 *
 * It also never compensates a missed session: the future may not gain volume or
 * quality beyond the current future intent. A revision that cannot satisfy the
 * merged-week guardrails is not suggested.
 */
class PlanReplanner(private val generator: WeeklyPlanGenerator = WeeklyPlanGenerator()) {
    fun evaluate(input: ReplanInput): ReplanResult {
        val availabilityHash = availabilityHash(input.profile.weeklyAvailability)
        if (input.currentPlan.weekStart != currentWeekStart(input.today)) {
            return ReplanResult.NoChange(
                "Só é possível replanejar a semana vigente.",
                availabilityHash,
            )
        }

        val missed = missedSessions(input)
        val missedTriggered = missed.size >= input.missedSessionThreshold
        val availabilityTriggered = input.currentAvailabilityHash != null &&
            input.currentAvailabilityHash != availabilityHash
        val userRequested = input.trigger == ReplanTrigger.USER_REQUEST
        if (!userRequested && !missedTriggered && !availabilityTriggered) {
            // A missing baseline is captured for the future, never inferred as a change.
            val reason = if (input.currentAvailabilityHash == null) {
                "Disponibilidade atual registrada como referência; ainda não há histórico para comparar."
            } else {
                "Nenhum desvio elegível nesta semana."
            }
            return ReplanResult.NoChange(reason, availabilityHash)
        }

        if (input.profile.weeklyAvailability.minutesByDay.isEmpty()) {
            return ReplanResult.NoChange(
                "Sem disponibilidade configurada; não é possível replanejar.",
                availabilityHash,
            )
        }
        if (input.currentPlan.sessions.none { it.date.isAfter(input.today) }) {
            return ReplanResult.NoChange(
                "Não há dias futuros nesta semana para replanejar.",
                availabilityHash,
            )
        }

        val generated = generator.generate(
            WeeklyPlanInput(
                profile = input.profile,
                readiness = input.readiness,
                load = input.load,
                weekStart = input.currentPlan.weekStart,
                today = input.today,
                previousWeek = input.previousWeek,
                weekNumberInBlock = input.weekNumberInBlock,
            ),
        )
        val preserved = input.currentPlan.sessions.filter {
            !it.date.isAfter(input.today) || it.status != SessionStatus.PLANNED
        }
        val preservedDates = preserved.map { it.date }.toSet()
        val candidateFuture = generated.sessions.filter {
            it.date.isAfter(input.today) && it.date !in preservedDates
        }
        val candidateSessions = (preserved + candidateFuture).sortedBy { it.date }
        val currentFuture = input.currentPlan.sessions.filter { it.date.isAfter(input.today) }

        validationFailure(candidateSessions, candidateFuture, input)?.let { reason ->
            return ReplanResult.NoChange(reason, availabilityHash)
        }
        ceilingFailure(currentFuture, candidateFuture)?.let { reason ->
            return ReplanResult.NoChange(reason, availabilityHash)
        }
        if (sameProjection(currentFuture, candidateFuture)) {
            return ReplanResult.NoChange(
                "O resto da semana já está como deveria; nada a sugerir.",
                availabilityHash,
            )
        }

        val trigger = activeTrigger(
            userRequested = userRequested,
            missedTriggered = missedTriggered,
        )
        val rationale = rationale(trigger, missed.size)
        val candidate = WeeklyPlan(
            weekStart = input.currentPlan.weekStart,
            sessions = candidateSessions,
            weeklyMinutes = candidateSessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = candidateSessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
            qualityCount = candidateSessions.count { it.type == WorkoutType.QUALITY },
            rationale = rationale,
            confidence = generated.confidence,
            ruleIds = generated.ruleIds,
            evidenceIds = generated.evidenceIds,
            createdOn = input.currentPlan.createdOn,
        )
        return ReplanResult.Suggest(
            suggestion = ReplanSuggestion(
                weekStart = candidate.weekStart,
                trigger = trigger,
                missedTrainableCount = missed.size,
                expectedVersion = input.currentVersion,
                expectedInputsHash = inputsHash(input),
                expectedAvailabilityHash = input.currentAvailabilityHash,
                availabilityHash = availabilityHash,
                rationale = rationale,
                ruleIds = generated.ruleIds,
                evidenceIds = generated.evidenceIds,
                confidence = generated.confidence,
            ),
            candidate = candidate,
        )
    }

    /**
     * T1 counts `PLANNED` sessions of the week that are already in the past and
     * were trainable, without changing their status (A5). Days before the plan
     * was created and days known as rest/illness never count.
     */
    private fun missedSessions(input: ReplanInput): List<PlannedSession> =
        input.currentPlan.sessions.filter { session ->
            session.status == SessionStatus.PLANNED &&
                session.type != WorkoutType.REST &&
                session.date.isBefore(input.today) &&
                !session.date.isBefore(input.currentPlan.createdOn) &&
                session.date !in input.nonTrainablePastDates
        }

    /** Validates the whole merged week: only executed sessions and the new future constrain it. */
    private fun validationFailure(
        sessions: List<PlannedSession>,
        future: List<PlannedSession>,
        input: ReplanInput,
    ): String? {
        val effective = sessions.filter {
            it.status == SessionStatus.DONE ||
                (it.status == SessionStatus.PLANNED && it.date.isAfter(input.today))
        }
        val qualities = effective.filter { it.type == WorkoutType.QUALITY }
        if (qualities.size > MAX_QUALITIES) {
            return "O plano permite no máximo $MAX_QUALITIES treinos fortes por semana."
        }
        val longDays = effective.filter { it.type == WorkoutType.LONG }.map { it.date.dayOfWeek }
        if (longDays.size > 1) {
            return "O plano permite um único longão por semana."
        }
        qualities.forEach { quality ->
            if (longDays.any { it == quality.date.dayOfWeek || adjacent(it, quality.date.dayOfWeek) }) {
                return "Treino forte não pode ficar no dia do longão nem ao lado dele."
            }
        }
        qualities.forEachIndexed { index, quality ->
            qualities.drop(index + 1).forEach { other ->
                if (adjacent(quality.date.dayOfWeek, other.date.dayOfWeek)) {
                    return "Não deixe dois treinos fortes em dias consecutivos."
                }
            }
        }
        val availableDays = input.profile.weeklyAvailability.minutesByDay.keys
        future.forEach { session ->
            if (session.date.dayOfWeek !in availableDays) {
                return "Dia sem disponibilidade no futuro; revise sua semana."
            }
        }
        return null
    }

    /**
     * A revision never compensates missed work: future volume and the number of
     * future quality sessions may not exceed the current future intent.
     */
    private fun ceilingFailure(currentFuture: List<PlannedSession>, candidateFuture: List<PlannedSession>): String? {
        val current = currentFuture.filter { it.status == SessionStatus.PLANNED }
        val candidate = candidateFuture.filter { it.status == SessionStatus.PLANNED }
        if (candidate.sumOf { it.durationMin ?: 0 } > current.sumOf { it.durationMin ?: 0 }) {
            return "A revisão não pode aumentar o volume futuro da semana; ela não repõe treinos perdidos."
        }
        if (candidate.count { it.type == WorkoutType.QUALITY } > current.count { it.type == WorkoutType.QUALITY }) {
            return "A revisão não pode adicionar treino forte além do que já estava planejado."
        }
        return null
    }

    /**
     * NoChange compares the full semantic future — date, type, title, duration,
     * targets, rules and evidence — not only the session count.
     */
    private fun sameProjection(currentFuture: List<PlannedSession>, candidateFuture: List<PlannedSession>): Boolean {
        val current = currentFuture.map { it.projection() }.sortedWith(PROJECTION_ORDER)
        val candidate = candidateFuture.map { it.projection() }.sortedWith(PROJECTION_ORDER)
        return current == candidate
    }

    private fun activeTrigger(userRequested: Boolean, missedTriggered: Boolean): ReplanTrigger = when {
        userRequested -> ReplanTrigger.USER_REQUEST
        missedTriggered -> ReplanTrigger.MISSED_SESSIONS
        else -> ReplanTrigger.AVAILABILITY_CHANGED
    }

    private fun rationale(trigger: ReplanTrigger, missedCount: Int): String = when (trigger) {
        ReplanTrigger.USER_REQUEST ->
            "Revisão pedida por você. O restante da semana foi reorganizado sem repor treinos perdidos."

        ReplanTrigger.MISSED_SESSIONS ->
            "Treinos elegíveis sem conclusão: $missedCount. O restante da semana foi reorganizado sem repor carga."

        ReplanTrigger.AVAILABILITY_CHANGED ->
            "Sua disponibilidade mudou. O restante da semana foi reorganizado sem aumentar a carga."
    }

    companion object {
        /**
         * Product heuristic (A6), not a scientific threshold: two eligible missed
         * sessions suggest a revision. Callers may configure another value.
         */
        const val PRODUCT_MISSED_SESSION_THRESHOLD = 2

        private const val MAX_QUALITIES = 2

        /** Stable hash of the configured availability; order of the map never matters. */
        fun availabilityHash(availability: WeeklyAvailability): String = sha256(
            availability.minutesByDay.entries
                .sortedBy { it.key.value }
                .joinToString("|") { "${it.key.name}=${it.value}" },
        )

        /** Stable hash of every input that can change the candidate plan. */
        fun inputsHash(input: ReplanInput): String = sha256(input.canonical())
    }
}

private data class SessionProjection(
    val date: LocalDate,
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val targets: PrescriptionTargets,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
)

private val PROJECTION_ORDER: Comparator<SessionProjection> = compareBy(
    { it.date },
    { it.type },
    { it.title },
    { it.durationMin ?: -1 },
)

private fun PlannedSession.projection(): SessionProjection = SessionProjection(
    date = date,
    type = type,
    title = title,
    durationMin = durationMin,
    targets = targets,
    ruleIds = ruleIds,
    evidenceIds = evidenceIds,
)

private fun currentWeekStart(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun adjacent(a: DayOfWeek, b: DayOfWeek): Boolean {
    val diff = abs(a.value - b.value)
    return diff == 1 || diff == 6
}

private fun ReplanInput.canonical(): String = buildString {
    append("week=").append(currentPlan.weekStart)
    append(";createdOn=").append(currentPlan.createdOn)
    append(";today=").append(today)
    append(";availability=").append(PlanReplanner.availabilityHash(profile.weeklyAvailability))
    append(";profile=").append(profile.canonical())
    append(";readiness=").append(readiness.canonical())
    append(";load=").append(load?.canonical() ?: "null")
    append(";previous=").append(previousWeek?.canonical() ?: "null")
    append(";block=").append(weekNumberInBlock)
    append(";nonTrainable=").append(nonTrainablePastDates.sorted().joinToString(","))
    append(";threshold=").append(missedSessionThreshold)
    append(";sessions=").append(currentPlan.sessions.joinToString("|") { it.canonical() })
}

private fun AthleteProfile.canonical(): String = listOf(
    id,
    birthDate.toString(),
    sex.name,
    heightCm?.toString(),
    weightKg?.toString(),
    restingHeartRate?.toString(),
    maxHeartRateObserved?.toString(),
    lactateThresholdHeartRate?.toString(),
    thresholdPace?.secondsPerKm?.toString(),
    vdot?.toString(),
    experience.name,
    timezone,
    weeklyAvailability.minutesByDay.entries
        .sortedBy { it.key.value }
        .joinToString(",") { "${it.key.name}=${it.value}" },
    goal?.let { "${it.type.name}@${it.targetDate}" },
    city,
    latitude?.toString(),
    longitude?.toString(),
    units.name,
    treadmillAccess.toString(),
).joinToString(",")

private fun ReadinessResult.canonical(): String = buildString {
    append(score).append(',')
    append(level.name).append(',')
    append(confidence.name).append(',')
    append(components.entries.sortedBy { it.key.name }.joinToString("|") { "${it.key.name}=${it.value}" })
    append(',')
    append(missing.map { it.name }.sorted().joinToString("|")).append(',')
    append(flags.map { it.name }.sorted().joinToString("|"))
}

private fun LoadSnapshot.canonical(): String = listOf(
    dataState.name,
    acwr?.toString(),
    ctl.toString(),
    atl.toString(),
    coverage.daysWithLoad.toString(),
    coverage.sourceFresh.toString(),
    coverage.lastActivityAt?.toString(),
).joinToString(",")

private fun WeeklyPlan.canonical(): String = buildString {
    append(weekStart).append(':')
    append(sessions.joinToString("|") { it.canonical() })
}

private fun PlannedSession.canonical(): String = listOf(
    date.toString(),
    type.name,
    title,
    durationMin?.toString(),
    targets.zone,
    targets.pace?.fast?.secondsPerKm?.toString(),
    targets.pace?.slow?.secondsPerKm?.toString(),
    targets.heartRate?.minBpm?.toString(),
    targets.heartRate?.maxBpm?.toString(),
    targets.distanceKm?.toString(),
    targets.rpe?.toString(),
    ruleIds.joinToString(","),
    evidenceIds.joinToString(","),
    status.name,
    skipReason,
    completionEvidence?.name,
    executionId,
    activityId,
    closureReason?.name,
    closedAt?.toString(),
).joinToString(";")

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xFF
        hex[index * 2] = digits[unsigned ushr 4]
        hex[index * 2 + 1] = digits[unsigned and 0x0F]
    }
    return String(hex)
}
