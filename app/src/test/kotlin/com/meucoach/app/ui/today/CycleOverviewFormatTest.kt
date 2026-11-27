package com.meucoach.app.ui.today

import com.meucoach.core.domain.cycle.CycleDeload
import com.meucoach.core.domain.cycle.CycleStrongIntent
import com.meucoach.core.domain.cycle.CycleVolumeIntent
import com.meucoach.core.domain.cycle.CycleWeek
import com.meucoach.core.domain.cycle.CycleWeekState
import com.meucoach.core.domain.prescription.MesoPhase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class CycleOverviewFormatTest {
    private val monday = LocalDate.of(2026, 11, 30)

    @Test
    fun `same month range is short and readable`() {
        assertThat(CycleOverviewFormat.weekRange(LocalDate.of(2026, 12, 1)))
            .isEqualTo("1–7 dez")
    }

    @Test
    fun `cross month range names both months`() {
        assertThat(CycleOverviewFormat.weekRange(LocalDate.of(2026, 12, 29)))
            .isEqualTo("29 dez–4 jan")
    }

    @Test
    fun `full description is spoken by TalkBack`() {
        assertThat(CycleOverviewFormat.weekDescription(monday))
            .isEqualTo("Semana de 30 de novembro a 6 de dezembro")
    }

    @Test
    fun `strong label uses the persisted days for current and next`() {
        val week = week(
            strongDays = listOf(monday.plusDays(1), monday.plusDays(5)),
        )

        assertThat(CycleOverviewFormat.strong(week)).isEqualTo("2 treinos fortes: ter e sáb")
    }

    @Test
    fun `future strong label is a cap, not a session`() {
        val week = week(state = CycleWeekState.FUTURE, strong = CycleStrongIntent.UP_TO_TWO)

        assertThat(CycleOverviewFormat.strong(week)).isEqualTo("Até 2 treinos fortes")
    }

    @Test
    fun `conditional deload explains the condition`() {
        assertThat(CycleOverviewFormat.deloadCondition(1))
            .isEqualTo("Se você completar esta semana de carga, a descarga cai aqui.")
        assertThat(CycleOverviewFormat.deloadCondition(3))
            .isEqualTo("Se você completar as próximas 3 semanas de carga, a descarga cai aqui.")
    }

    @Test
    fun `accessibility combines phase, volume, strong and state`() {
        val week = week(
            state = CycleWeekState.NEXT,
            isNext = true,
            phase = MesoPhase.BUILD,
            volume = CycleVolumeIntent.BUILD,
            strong = CycleStrongIntent.UP_TO_TWO,
            deload = CycleDeload.CONDITIONAL,
            conditionWeeks = 1,
        )

        assertThat(CycleOverviewFormat.weekAccessibility(week)).contains(
            "Semana de 30 de novembro a 6 de dezembro",
            "Construção",
            "Constrói",
            "Até dois treinos fortes",
            "Descarga a confirmar",
            "Próxima",
        )
    }

    private fun week(
        state: CycleWeekState = CycleWeekState.CURRENT,
        isNext: Boolean = false,
        phase: MesoPhase? = MesoPhase.BASE,
        volume: CycleVolumeIntent = CycleVolumeIntent.BUILD,
        strong: CycleStrongIntent = CycleStrongIntent.UP_TO_ONE,
        strongDays: List<LocalDate> = emptyList(),
        deload: CycleDeload = CycleDeload.NONE,
        conditionWeeks: Int? = null,
    ) = CycleWeek(
        weekStart = monday,
        phase = phase,
        state = state,
        isCurrent = state == CycleWeekState.CURRENT,
        isNext = isNext,
        volume = volume,
        strong = strong,
        strongDays = strongDays,
        deload = deload,
        deloadConditionWeeks = conditionWeeks,
    )
}
