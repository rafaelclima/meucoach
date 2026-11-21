package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachImportLabelsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `system turn marca a importacao feita`() {
        assertThat(CoachImportLabels.SYSTEM_LINE).contains("Importação feita")
    }

    @Test
    fun `importacao de hoje confirma que entrou na carga`() {
        val ack = CoachImportLabels.ack(context(importedToday = true))

        assertThat(ack).contains("Já entrou na carga")
    }

    @Test
    fun `sem importacao hoje nao ha resposta`() {
        assertThat(CoachImportLabels.ack(context(importedToday = false))).isNull()
    }

    @Test
    fun `sem plano a importacao nao vira conversa`() {
        assertThat(
            CoachImportLabels.ack(context(importedToday = true, hasPlan = false)),
        ).isNull()
    }

    private fun context(importedToday: Boolean, hasPlan: Boolean = true) = CoachNextContext(
        subject = CoachNextSubject.Training(
            PlannedSession(
                date = monday.plusDays(1),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        today = monday,
        hasPlan = hasPlan,
        hasProfile = true,
        importedToday = importedToday,
    )
}
