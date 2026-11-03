package com.meucoach.app.ui.common

import com.meucoach.core.data.health.HealthConnectStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class HealthConnectCopyTest {
    @Test
    fun `explains an unavailable device`() {
        assertThat(
            healthConnectStatusLabel(
                status = HealthConnectStatus.UNAVAILABLE,
                hasPermissions = false,
            ),
        ).isEqualTo("Indisponível neste aparelho")
    }

    @Test
    fun `asks for an update when the provider is outdated`() {
        assertThat(
            healthConnectStatusLabel(
                status = HealthConnectStatus.UPDATE_REQUIRED,
                hasPermissions = false,
            ),
        ).isEqualTo("Atualize o Health Connect")
    }

    @Test
    fun `distinguishes connected with and without data`() {
        assertThat(
            healthConnectStatusLabel(
                status = HealthConnectStatus.AVAILABLE,
                hasPermissions = true,
                storedDays = 0,
            ),
        ).isEqualTo("Conectado · sem dados ainda")

        assertThat(
            healthConnectStatusLabel(
                status = HealthConnectStatus.AVAILABLE,
                hasPermissions = true,
                storedDays = 42,
            ),
        ).isEqualTo("Conectado · 42 dias")
    }

    @Test
    fun `says the connection is missing before permissions`() {
        assertThat(
            healthConnectStatusLabel(
                status = HealthConnectStatus.AVAILABLE,
                hasPermissions = false,
                storedDays = 42,
            ),
        ).isEqualTo("Ainda não conectado")
    }

    @Test
    fun `formats the last read relative to today`() {
        val today = LocalDate.of(2026, 11, 3)

        assertThat(healthConnectLastSyncLabel(today, today)).isEqualTo("Última leitura hoje")
        assertThat(healthConnectLastSyncLabel(today.minusDays(1), today)).isEqualTo("Última leitura ontem")
        assertThat(healthConnectLastSyncLabel(today.minusDays(8), today)).isEqualTo("Última leitura em 26/10")
        assertThat(healthConnectLastSyncLabel(null, today)).isNull()
    }

    @Test
    fun `only counts as connected when permissions were granted`() {
        assertThat(isHealthConnectConnected(HealthConnectStatus.AVAILABLE, true)).isTrue()
        assertThat(isHealthConnectConnected(HealthConnectStatus.AVAILABLE, false)).isFalse()
        assertThat(isHealthConnectConnected(HealthConnectStatus.UNAVAILABLE, true)).isFalse()
    }
}
