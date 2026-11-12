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
        val fingerprint = input.planningFingerprint
        val profileChanged = input.currentEvaluatedFingerprintVersion != null &&
            input.currentEvaluatedFingerprint != null &&
            (
                input.currentEvaluatedFingerprintVersion != fingerprint.version ||
                    input.currentEvaluatedFingerprint != fingerprint.hash
                )
        val temporalChanged = input.currentTemporalContextHash != null &&
            input.currentTemporalContextHash != input.temporalContextHash
        val t4Triggered = profileChanged || temporalChanged

        if (input.currentPlan.weekStart != currentWeekStart(input.today)) {
            return noChange("Só é possível replanejar a semana vigente.", input, availabilityHash)
        }

        val missed = missedSessions(input)
        val missedTriggered = missed.size >= input.missedSessionThreshold
        val availabilityTriggered = input.currentAvailabilityHash != null &&
            input.currentAvailabilityHash != availabilityHash
        val userRequested = input.trigger == ReplanTrigger.USER_REQUEST
        // T5 (ADR 0037): the persisted week still has a strong/long or over-cap
        // session inside an active post-race recovery window.
        val recoveryViolations = recoveryViolations(input)
        val recoveryTriggered = recoveryViolations.isNotEmpty()
        if (!userRequested && !missedTriggered && !availabilityTriggered && !t4Triggered &&
            !recoveryTriggered
        ) {
            // A missing baseline is captured for the future, never inferred as a change.
            val reason = if (input.currentAvailabilityHash == null) {
                "Disponibilidade atual registrada como referência; ainda não há histórico para comparar."
            } else {
                "Nenhum desvio elegível nesta semana."
            }
            return noChange(reason, input, availabilityHash)
        }

        if (input.profile.weeklyAvailability.minutesByDay.isEmpty()) {
            return noChange(
                "Sem disponibilidade configurada; não é possível replanejar.",
                input,
                availabilityHash,
            )
        }
        if (input.currentPlan.sessions.none { it.date.isAfter(input.today) }) {
            return noChange(
                "Não há dias futuros nesta semana para replanejar.",
                input,
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
                recentWeeks = input.recentWeeks,
                weekNumberInBlock = input.weekNumberInBlock,
                race = input.race,
                eventDates = input.eventDates,
                recovery = input.recovery,
            ),
        )
        val raceDates = input.eventDates + listOfNotNull(input.race?.targetDate)
        val preserved = input.currentPlan.sessions.filter {
            !it.date.isAfter(input.today) || it.status != SessionStatus.PLANNED
        }
        val preservedDates = preserved.map { it.date }.toSet()
        val candidateFuture = generated.sessions.filter {
            it.date.isAfter(input.today) &&
                it.date !in preservedDates &&
                it.date !in raceDates
        }
        val candidateSessions = (preserved + candidateFuture).sortedBy { it.date }
        val currentFuture = input.currentPlan.sessions.filter { it.date.isAfter(input.today) }

        validationFailure(candidateSessions, candidateFuture, input)?.let { reason ->
            return noChange(reason, input, availabilityHash)
        }
        ceilingFailure(currentFuture, candidateFuture)?.let { reason ->
            return noChange(reason, input, availabilityHash)
        }
        if (sameProjection(currentFuture, candidateFuture)) {
            return noChange(
                "O resto da semana já está como deveria; nada a sugerir.",
                input,
                availabilityHash,
            )
        }

        val causes = causes(
            userRequested = userRequested,
            profileChanged = profileChanged,
            temporalChanged = temporalChanged,
            missedTriggered = missedTriggered,
            availabilityTriggered = availabilityTriggered,
            recoveryTriggered = recoveryTriggered,
        )
        val trigger = activeTrigger(causes)
        val rationale = rationale(trigger, causes, missed.size)
        val suggestionRules = if (profileChanged || temporalChanged) {
            (generated.ruleIds + "R-PLAN-09").distinct()
        } else {
            generated.ruleIds
        }
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
                causes = causes,
                missedTrainableCount = missed.size,
                expectedVersion = input.currentVersion,
                expectedInputsHash = inputsHash(input),
                expectedAvailabilityHash = input.currentAvailabilityHash,
                availabilityHash = availabilityHash,
                expectedFingerprintVersion = fingerprint.version,
                expectedFingerprint = fingerprint.hash,
                profileSnapshot = fingerprint.snapshot,
                expectedTemporalContextHash = input.temporalContextHash,
                rationale = rationale,
                ruleIds = suggestionRules,
                evidenceIds = generated.evidenceIds,
                confidence = generated.confidence,
                recoveryEventId = input.recovery?.race?.eventId,
            ),
            candidate = candidate,
        )
    }

    private fun noChange(reason: String, input: ReplanInput, availabilityHash: String): ReplanResult.NoChange =
        ReplanResult.NoChange(
            reason = reason,
            availabilityHash = availabilityHash,
            checkpoint = PlanEvaluationCheckpoint(
                fingerprintVersion = input.planningFingerprint.version,
                fingerprint = input.planningFingerprint.hash,
                profileSnapshot = input.planningFingerprint.snapshot,
                temporalContextHash = input.temporalContextHash,
                availabilityHash = availabilityHash,
            ),
        )

    /**
     * Future `PLANNED` sessions that violate an active recovery window (ADR 0037).
     * A week already compatible with the window produces no suggestion.
     */
    private fun recoveryViolations(input: ReplanInput): List<PlannedSession> {
        val recovery = input.recovery ?: return emptyList()
        return input.currentPlan.sessions.filter { session ->
            session.status == SessionStatus.PLANNED &&
                session.date.isAfter(input.today) &&
                recovery.blocks(session.type, session.date, session.durationMin)
        }
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
                session.date !in input.nonTrainablePastDates &&
                session.date !in input.eventDates &&
                session.date != input.race?.targetDate
        }

    /** Validates the whole merged week: only executed sessions and the new future constrain it. */
    private fun validationFailure(
        sessions: List<PlannedSession>,
        future: List<PlannedSession>,
        input: ReplanInput,
    ): String? {
        val raceDates = input.eventDates + listOfNotNull(input.race?.targetDate)
        future.forEach { session ->
            if (session.date in raceDates) {
                return "Dia de prova não recebe sessão de treino."
            }
            if (session.type == WorkoutType.QUALITY && input.race?.allowsQuality(session.date) == false) {
                return "Sem treino forte na semana da prova nem dentro do corte conservador."
            }
            if (input.recovery != null &&
                input.recovery.blocks(session.type, session.date, session.durationMin)
            ) {
                return "Recuperação após a prova: sem treino forte, longão ou sessão acima do teto."
            }
        }
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

    private fun causes(
        userRequested: Boolean,
        profileChanged: Boolean,
        temporalChanged: Boolean,
        missedTriggered: Boolean,
        availabilityTriggered: Boolean,
        recoveryTriggered: Boolean,
    ): List<ReplanCause> = buildList {
        if (userRequested) add(ReplanCause.USER_REQUEST)
        if (profileChanged) add(ReplanCause.PROFILE_CHANGED)
        if (temporalChanged) add(ReplanCause.TIMEZONE_CONTEXT_CHANGED)
        if (recoveryTriggered) add(ReplanCause.RACE_COMPLETED)
        if (missedTriggered) add(ReplanCause.MISSED_SESSIONS)
        if (availabilityTriggered) add(ReplanCause.AVAILABILITY_CHANGED)
    }.distinct()

    private fun activeTrigger(causes: List<ReplanCause>): ReplanTrigger = when {
        ReplanCause.USER_REQUEST in causes -> ReplanTrigger.USER_REQUEST

        ReplanCause.PROFILE_CHANGED in causes ||
            ReplanCause.TIMEZONE_CONTEXT_CHANGED in causes -> ReplanTrigger.PROFILE_CHANGED

        ReplanCause.RACE_COMPLETED in causes -> ReplanTrigger.RACE_COMPLETED

        ReplanCause.MISSED_SESSIONS in causes -> ReplanTrigger.MISSED_SESSIONS

        else -> ReplanTrigger.AVAILABILITY_CHANGED
    }

    private fun rationale(trigger: ReplanTrigger, causes: List<ReplanCause>, missedCount: Int): String {
        if (causes.size > 1) {
            val labels = causes.joinToString(" e ") { it.label() }
            return "Mais de um motivo motivou a revisão: $labels. " +
                "O restante da semana foi reorganizado sem repor carga."
        }
        return when (trigger) {
            ReplanTrigger.USER_REQUEST ->
                "Revisão pedida por você. O restante da semana foi reorganizado sem repor treinos perdidos."

            ReplanTrigger.PROFILE_CHANGED -> when {
                ReplanCause.TIMEZONE_CONTEXT_CHANGED in causes &&
                    ReplanCause.PROFILE_CHANGED !in causes ->
                    "Seu fuso mudou. O restante da semana foi reorganizado sem aumentar a carga."

                else ->
                    "Seu perfil de planejamento mudou. " +
                        "O restante da semana foi reorganizado sem aumentar a carga."
            }

            ReplanTrigger.MISSED_SESSIONS ->
                "Treinos elegíveis sem conclusão: $missedCount. " +
                    "O restante da semana foi reorganizado sem repor carga."

            ReplanTrigger.RACE_COMPLETED ->
                "A prova foi concluída. O restante da semana foi encurtado para a recuperação, " +
                    "sem treino forte, sem repor carga e sem mexer no que já passou."

            ReplanTrigger.AVAILABILITY_CHANGED ->
                "Sua disponibilidade mudou. O restante da semana foi reorganizado sem aumentar a carga."
        }
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

private fun ReplanCause.label(): String = when (this) {
    ReplanCause.PROFILE_CHANGED -> "perfil"
    ReplanCause.TIMEZONE_CONTEXT_CHANGED -> "fuso"
    ReplanCause.MISSED_SESSIONS -> "treinos não concluídos"
    ReplanCause.AVAILABILITY_CHANGED -> "disponibilidade"
    ReplanCause.USER_REQUEST -> "pedido seu"
    ReplanCause.RACE_COMPLETED -> "recuperação após a prova"
}

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
    append(";fingerprintVersion=").append(planningFingerprint.version)
    append(";fingerprint=").append(planningFingerprint.hash)
    append(";temporalContext=").append(temporalContextHash)
    append(";readiness=").append(readiness.canonical())
    append(";load=").append(load?.canonical() ?: "null")
    append(";previous=").append(previousWeek?.canonical() ?: "null")
    append(";recent=").append(recentWeeks.sortedBy { it.weekStart }.joinToString("|") { it.canonical() })
    append(";block=").append(weekNumberInBlock)
    append(";nonTrainable=").append(nonTrainablePastDates.sorted().joinToString(","))
    append(";race=").append(
        race?.let { "${it.eventId},${it.distance.name},${it.targetDate}" } ?: "null",
    )
    append(";eventDates=").append(eventDates.sorted().joinToString(","))
    append(";recovery=").append(
        recovery?.let { "${it.race.eventId},${it.race.distance.name},${it.race.raceDate}" } ?: "null",
    )
    append(";threshold=").append(missedSessionThreshold)
    append(";sessions=").append(currentPlan.sessions.joinToString("|") { it.canonical() })
}

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
