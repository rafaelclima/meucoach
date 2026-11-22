package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic session context for ASK_SESSION / ASK_HOW_TO_RUN (mapa Coach).
 *
 * Pure Kotlin/JVM: given the persisted week and (only for today) the persisted
 * prescription, it builds the only numbers the answer may cite plus the
 * `engineContext` the proxy rewrites. It never invents pace, FC, carga, VDOT
 * or decisions: a future day is always labelled as planned, readiness and
 * sleep only appear for today, and past/missing days are reported as such so
 * the caller can answer locally without a model call.
 */
enum class SessionTask {
    WHAT,
    HOW_TO,
}

data class SessionAnswer(
    val targetDate: LocalDate,
    val dayLabel: String,
    val session: PlannedSession?,
    val isToday: Boolean,
    val isTomorrow: Boolean,
    val isPlannedOnly: Boolean,
    val isRest: Boolean,
    val hasSession: Boolean,
    val engineContext: String,
    val allowedMinutes: Set<Int>,
    val allowedDistancesKm: Set<Double>,
    /**
     * Deterministic answer shown when the model is unreachable (ADR 0057):
     * motor copy only, no invention possible. Empty when there is no
     * session to report (the caller answers those days locally).
     */
    val fallback: String = "",
)

object CoachSessionAnswer {
    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Resolves a [DayOfWeek] slot to the date inside the week that holds
     * [today] (Monday..Sunday). A null slot means "the next relevant day" and
     * is resolved by the caller, never guessed here.
     */
    fun resolveDayInWeek(today: LocalDate, dayOfWeek: DayOfWeek): LocalDate =
        weekStart(today).plusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    fun build(
        today: LocalDate,
        target: LocalDate,
        sessions: List<PlannedSession>,
        weeklyMinutes: Int,
        task: SessionTask,
        prescription: DailyPrescription? = null,
        shortSleep: Boolean = false,
        hasCheckIn: Boolean = false,
    ): SessionAnswer {
        val session = sessions.find { it.date == target }
        val isToday = target == today
        val isTomorrow = target == today.plusDays(1)
        val dayLabel = dayLabel(today, target)
        val isRest = session?.type == WorkoutType.REST
        val hasSession = session != null
        val prescribed = if (isToday) prescription else null
        val isPlannedOnly = !isToday || prescribed == null
        val allowedMinutes = buildSet {
            session?.durationMin?.let { add(it) }
            prescribed?.prescribedDurationMin?.let { add(it) }
            add(weeklyMinutes)
        }
        val allowedDistancesKm = buildSet {
            session?.targets?.distanceKm?.let { add(it) }
            prescribed?.targets?.distanceKm?.let { add(it) }
        }
        val engineContext = engineContext(
            task = task,
            dayLabel = dayLabel,
            target = target,
            session = session,
            weeklyMinutes = weeklyMinutes,
            prescribed = prescribed,
            isPlannedOnly = isPlannedOnly,
            shortSleep = shortSleep && isToday,
            hasCheckIn = hasCheckIn && isToday,
        )
        return SessionAnswer(
            targetDate = target,
            dayLabel = dayLabel,
            session = session,
            isToday = isToday,
            isTomorrow = isTomorrow,
            isPlannedOnly = isPlannedOnly,
            isRest = isRest,
            hasSession = hasSession,
            engineContext = engineContext,
            allowedMinutes = allowedMinutes,
            allowedDistancesKm = allowedDistancesKm,
            fallback = fallbackCopy(dayLabel, isToday, isTomorrow, session, prescribed, isPlannedOnly, task),
        )
    }

    /**
     * Motor-only answer for an unreachable model: what day, what workout,
     * how long, planned or prescribed — plus the effort line for HOW_TO.
     * No pace, no FC, no invention; the validator never sees it.
     */
    private fun fallbackCopy(
        dayLabel: String,
        isToday: Boolean,
        isTomorrow: Boolean,
        session: PlannedSession?,
        prescribed: DailyPrescription?,
        isPlannedOnly: Boolean,
        task: SessionTask,
    ): String {
        if (session == null) return ""
        if (session.type == WorkoutType.REST) return "$dayLabel é descanso — sem treino."
        val title = prescribed?.title ?: session.title
        val duration = prescribed?.prescribedDurationMin ?: session.durationMin
        val head = when {
            isToday -> "Hoje tem"
            isTomorrow -> "Amanhã tem"
            else -> "$dayLabel tem"
        }
        val planned = if (isPlannedOnly) " (planejado)" else ""
        val base = "$head $title${duration?.let { " · $it min" }.orEmpty()}$planned. ${session.rationale}."
        return if (task == SessionTask.HOW_TO) {
            "$base Comece soltinho e feche leve."
        } else {
            base
        }
    }

    private fun dayLabel(today: LocalDate, target: LocalDate): String = when (target) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> "${weekdayName(target.dayOfWeek)} ${target.format(dayFormat)}"
    }

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Segunda"
        DayOfWeek.TUESDAY -> "Terça"
        DayOfWeek.WEDNESDAY -> "Quarta"
        DayOfWeek.THURSDAY -> "Quinta"
        DayOfWeek.FRIDAY -> "Sexta"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }

    private data class EngineLines(
        val task: SessionTask,
        val dayLabel: String,
        val target: LocalDate,
        val session: PlannedSession?,
        val weeklyMinutes: Int,
        val prescribed: DailyPrescription?,
        val isPlannedOnly: Boolean,
        val shortSleep: Boolean,
        val hasCheckIn: Boolean,
    )

    private fun engineContext(
        task: SessionTask,
        dayLabel: String,
        target: LocalDate,
        session: PlannedSession?,
        weeklyMinutes: Int,
        prescribed: DailyPrescription? = null,
        isPlannedOnly: Boolean = true,
        shortSleep: Boolean = false,
        hasCheckIn: Boolean = false,
    ): String = renderLines(
        EngineLines(
            task = task,
            dayLabel = dayLabel,
            target = target,
            session = session,
            weeklyMinutes = weeklyMinutes,
            prescribed = prescribed,
            isPlannedOnly = isPlannedOnly,
            shortSleep = shortSleep,
            hasCheckIn = hasCheckIn,
        ),
    )

    private fun renderLines(input: EngineLines): String {
        val taskLine = taskLine(input.task)
        val weekLine = "Semana: ${input.weeklyMinutes} min planejados no total."
        val session = input.session ?: return "$taskLine DIA: ${dayLine(input)}. " +
            "Sem sessão planejada nesse dia (descanso ou fora da disponibilidade). " +
            weekLine
        val kind = kindName(session.type)
        val duration = session.durationMin?.let { "$it min" } ?: "sem duração fixa"
        return "$taskLine DIA: ${dayLine(input)}. " +
            "SESSÃO: ${session.title} · $duration · $kind. ${authorityLine(input)}. " +
            "Motivo do plano: ${session.rationale}.${signalsLine(input)}${effortLine(input)} " +
            weekLine
    }
    private fun taskLine(task: SessionTask): String = when (task) {
        SessionTask.WHAT ->
            "TAREFA: dizer o que tem no dia, sem inventar número novo. " +
                "Diga o essencial uma vez só: o que é, quanto tempo, e só mencione mudança " +
                "quando houver (redução, descanso). Nunca diga 'o plano manteve' — diga o que o treino é."

        SessionTask.HOW_TO ->
            "TAREFA: explicar como executar a sessão em linguagem de corrida, " +
                "sem citar pace, FC ou números novos. Fale com o atleta (você): frases curtas, " +
                "verbos de ação ('comece', 'segure', 'feche'). Pode usar 'soltinho', 'trotinho', " +
                "'sem pressa'. Proibido jargão: 'conversável', 'esforço contínuo', 'o plano manteve'. " +
                "Se o contexto disser sono curto, explique em uma frase o que muda " +
                "('dormiu pouco, então começa bem solto'); sem sono curto, não fale de sono. " +
                "Feche variando: Bora? / Fechado? / Topa? / Combinado?"
    }

    private fun dayLine(input: EngineLines): String =
        "${input.dayLabel} (${input.target.format(DateTimeFormatter.ISO_LOCAL_DATE)})"

    private fun kindName(type: WorkoutType): String = when (type) {
        WorkoutType.REST -> "descanso"
        WorkoutType.EASY -> "fácil"
        WorkoutType.LONG -> "longo"
        WorkoutType.QUALITY -> "forte"
    }

    private fun authorityLine(input: EngineLines): String {
        if (input.isPlannedOnly) return "Planejado (a confirmação do dia sai no check-in da manhã)"
        val prescribed = input.prescribed
        val adjustment = when (prescribed?.adjustment) {
            PrescriptionAdjustment.KEEP -> "Mantido como planejado"
            PrescriptionAdjustment.REDUCE -> "Reduzido para o dia"
            PrescriptionAdjustment.DOWNGRADE -> "Ajustado para fácil"
            PrescriptionAdjustment.REST -> "Descanso prescrito"
            PrescriptionAdjustment.NO_PLAN -> "Fácil conservador"
            null -> "Prescrito para hoje"
        }
        return prescribed?.let {
            val duration = it.prescribedDurationMin?.let { d -> "$d min" } ?: "sem duração fixa"
            "Prescrito: ${it.title} · $duration ($adjustment)"
        } ?: adjustment
    }

    private fun signalsLine(input: EngineLines): String {
        if (input.prescribed == null && !input.shortSleep && !input.hasCheckIn) return ""
        val sleep = if (input.shortSleep) " Sono curto." else ""
        val checkIn = if (input.hasCheckIn) " Check-in feito." else " Sem check-in ainda."
        return "$sleep$checkIn"
    }

    private fun effortLine(input: EngineLines): String {
        if (input.task != SessionTask.HOW_TO) return ""
        val session = input.session ?: return ""
        if (session.type == WorkoutType.REST) return " Como executar: descanso é treino — sem rodar para compensar."
        val structure = if (session.structure != null) "com blocos" else "contínuo"
        return " Como executar: comece soltinho, sem pressa nos primeiros minutos; " +
            "depois encaixe um ritmo leve, que dê para conversar ($structure); " +
            "se apertar, diminua e feche leve, sem trocar por outro treino."
    }
}
