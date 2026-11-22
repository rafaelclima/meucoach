package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachNextLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `rotulo do dia usa hoje, amanha ou dia da semana`() {
        assertThat(CoachNextLabels.dayLabel(monday, monday, true)).isEqualTo("Hoje")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(1), false)).isEqualTo("Amanhã")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(2), false)).isEqualTo("QUA")
        assertThat(CoachNextLabels.dayLabel(monday, monday.plusDays(5), false)).isEqualTo("SÁB")
    }

    @Test
    fun `futuro sem prescricao mostra o planejado sem decidir`() {
        val context = context()

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Amanhã · Planejado: Ritmo forte · 35 min")
        assertThat(CoachNextLabels.statusLine(context))
            .isEqualTo("Planejado; a confirmação sai no check-in da manhã.")
        assertThat(CoachNextLabels.readinessLine(context)).isNull()
    }

    @Test
    fun `hoje sem prescricao nao apresenta treino como decisao`() {
        val context = context(subject = CoachNextSubject.Training(session(monday)))

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Hoje · Planejado: Ritmo forte · 35 min")
        assertThat(CoachNextLabels.statusLine(context))
            .isEqualTo("A decisão de hoje ainda não saiu — faça o check-in.")
        assertThat(CoachNextLabels.actionLine(context)).isNull()
    }

    @Test
    fun `hoje com check-in aguardando decisao nao pede check-in de novo`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            hasCheckIn = true,
        )

        assertThat(CoachNextLabels.statusLine(context))
            .isEqualTo("A decisão de hoje ainda não saiu.")
    }

    @Test
    fun `dia de prova nao mostra treino`() {
        val context = context(subject = raceSubject(monday.plusDays(1)))

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Amanhã · Dia de prova")
        assertThat(CoachNextLabels.statusLine(context))
            .isEqualTo("Dia de prova amanhã — sem treino planejado.")
        assertThat(CoachNextLabels.plannedLine(context)).isNull()
    }

    @Test
    fun `prova alem de amanha mostra a data`() {
        val context = context(subject = raceSubject(monday.plusDays(4)))

        assertThat(CoachNextLabels.headline(context)).isEqualTo("SEX · Dia de prova")
        assertThat(CoachNextLabels.statusLine(context))
            .isEqualTo("Próxima prova em 20/11 — sem treino planejado.")
    }

    @Test
    fun `prescricao rebaixada manda no titulo e o planejado vira linha secundaria`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            prescription = prescription(
                adjustment = PrescriptionAdjustment.DOWNGRADE,
                reason = QualityBlockReason.SUBJECTIVE_MISSING,
            ),
        )

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Hoje · Rodagem · 25 min")
        assertThat(CoachNextLabels.plannedLine(context)).isEqualTo("Planejado: Ritmo forte · 35 min.")
        assertThat(CoachNextLabels.statusLine(context)).contains("Ajustado para fácil")
        assertThat(CoachNextLabels.actionLine(context))
            .isEqualTo("Faça o check-in de hoje para liberar o treino forte.")
    }

    @Test
    fun `prescricao de descanso nao mostra duracao`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            prescription = prescription(
                adjustment = PrescriptionAdjustment.REST,
                reason = QualityBlockReason.ILLNESS_OR_RED_READINESS,
                prescribedType = WorkoutType.REST,
                prescribedTitle = "Descanso",
                prescribedDuration = null,
            ),
        )

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Hoje · Descanso")
        assertThat(CoachNextLabels.statusLine(context)).contains("Descanso prescrito")
        assertThat(CoachNextLabels.statusLine(context)).contains("Dia de cuidado")
    }

    @Test
    fun `mantido nao repete o planejado`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            prescription = prescription(
                adjustment = PrescriptionAdjustment.KEEP,
                prescribedType = WorkoutType.QUALITY,
                prescribedTitle = "Ritmo forte",
                prescribedDuration = 35,
            ),
        )

        assertThat(CoachNextLabels.headline(context)).isEqualTo("Hoje · Ritmo forte · 35 min")
        assertThat(CoachNextLabels.plannedLine(context)).isNull()
        assertThat(CoachNextLabels.statusLine(context)).isEqualTo("Mantido como planejado.")
    }

    @Test
    fun `unlockHint cru com ACWR nunca aparece`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            prescription = prescription(
                adjustment = PrescriptionAdjustment.DOWNGRADE,
                reason = QualityBlockReason.ACWR_HIGH,
            ),
        )

        val lines = listOfNotNull(
            CoachNextLabels.headline(context),
            CoachNextLabels.statusLine(context),
            CoachNextLabels.actionLine(context),
            CoachNextLabels.plannedLine(context),
            CoachNextLabels.readinessLine(context),
        ).joinToString(" ")

        assertThat(lines).doesNotContain("ACWR")
        assertThat(lines).doesNotContain("1,50")
    }

    @Test
    fun `todo motivo de bloqueio tem copy e desbloqueio sem sigla tecnica`() {
        QualityBlockReason.entries.forEach { reason ->
            val copy = CoachNextLabels.blockCopy(reason)
            val unlock = CoachNextLabels.unlockCopy(reason)

            assertThat(copy).isNotBlank()
            assertThat(unlock).isNotBlank()
            assertThat(copy).doesNotContain("ACWR")
            assertThat(unlock).doesNotContain("ACWR")
            assertThat(copy).doesNotContain("R-")
            assertThat(unlock).doesNotContain("R-")
        }
    }

    @Test
    fun `prontidao sem dados nao inventa nivel nem score`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            readiness = readiness(
                score = 50,
                level = ReadinessLevel.AMBER,
                flags = setOf(ReadinessFlag.INSUFFICIENT_DATA, ReadinessFlag.SUBJECTIVE_MISSING),
            ),
        )

        assertThat(CoachNextLabels.readinessLine(context)).contains("ainda não medida")
        assertThat(CoachNextLabels.readinessLevel(context)).isNull()
    }

    @Test
    fun `prontidao moderada mostra rotulo, score e sono`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday)),
            readiness = readiness(
                score = 59,
                level = ReadinessLevel.AMBER,
                flags = setOf(ReadinessFlag.SHORT_SLEEP, ReadinessFlag.NO_QUALITY),
            ),
            hasCheckIn = true,
            sleepMinutes = 340,
        )

        assertThat(CoachNextLabels.readinessLine(context))
            .isEqualTo("Prontidão Moderada · 59/100 — sono 5h40.")
        assertThat(CoachNextLabels.readinessLevel(context)).isEqualTo(ReadinessLevel.AMBER)
    }

    @Test
    fun `prontidao alta e baixa usam o mesmo vocabulario do Hoje`() {
        val green = context(
            subject = CoachNextSubject.Training(session(monday)),
            readiness = readiness(82, ReadinessLevel.GREEN, emptySet()),
            hasCheckIn = true,
        )
        val red = context(
            subject = CoachNextSubject.Training(session(monday)),
            readiness = readiness(28, ReadinessLevel.RED, setOf(ReadinessFlag.NO_QUALITY)),
            hasCheckIn = true,
        )

        assertThat(CoachNextLabels.readinessLine(green)).isEqualTo("Prontidão Alta · 82/100.")
        assertThat(CoachNextLabels.readinessLine(red)).isEqualTo("Prontidão Baixa · 28/100.")
    }

    @Test
    fun `amanha nao inventa semaforo`() {
        val context = context(
            subject = CoachNextSubject.Training(session(monday.plusDays(1))),
            readiness = readiness(82, ReadinessLevel.GREEN, emptySet()),
            hasCheckIn = true,
        )

        assertThat(CoachNextLabels.readinessLine(context)).isNull()
        assertThat(CoachNextLabels.readinessLevel(context)).isNull()
    }

    private fun context(
        subject: CoachNextSubject? = CoachNextSubject.Training(session(monday.plusDays(1))),
        prescription: DailyPrescription? = null,
        readiness: ReadinessResult? = null,
        hasPlan: Boolean = true,
        hasProfile: Boolean = true,
        hasCheckIn: Boolean = false,
        sleepMinutes: Int? = null,
    ) = CoachNextContext(
        subject = subject,
        today = monday,
        hasPlan = hasPlan,
        hasProfile = hasProfile,
        prescription = prescription,
        readiness = readiness,
        hasCheckIn = hasCheckIn,
        sleepMinutes = sleepMinutes,
    )

    private fun session(date: LocalDate) = PlannedSession(
        date = date,
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = 35,
        rationale = "Estímulo.",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.PLANNED,
    )

    private fun raceSubject(date: LocalDate) = CoachNextSubject.Race(
        RaceEvent(
            id = "race-1",
            distance = RaceDistance.FIVE_K,
            targetDate = date,
            createdAt = Instant.parse("2026-11-16T12:00:00Z"),
            updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
        ),
    )

    private fun prescription(
        adjustment: PrescriptionAdjustment,
        reason: QualityBlockReason? = null,
        prescribedType: WorkoutType = WorkoutType.EASY,
        prescribedTitle: String = "Fácil",
        prescribedDuration: Int? = 25,
    ) = DailyPrescription(
        date = monday,
        plannedSessionId = null,
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Ritmo forte",
        plannedDurationMin = 35,
        prescribedType = prescribedType,
        title = prescribedTitle,
        prescribedDurationMin = prescribedDuration,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = adjustment,
        rationale = "Dia leve.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
        qualityBlockReason = reason,
        decisionDetail = if (reason == null) null else "ACWR 1,50 > 1,50",
        unlockHint = if (reason == null) {
            null
        } else {
            "ACWR 1,50 > 1,50. Reduza a carga para liberar o treino forte."
        },
    )

    private fun readiness(score: Int, level: ReadinessLevel, flags: Set<ReadinessFlag>) = ReadinessResult(
        score = score,
        level = level,
        confidence = DataConfidence.MEDIUM,
        components = if (ReadinessFlag.INSUFFICIENT_DATA in flags) {
            emptyMap()
        } else {
            mapOf(ReadinessComponent.SUBJECTIVE to score)
        },
        missing = emptySet(),
        flags = flags,
    )
}
