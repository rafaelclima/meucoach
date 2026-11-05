package com.meucoach.app.ui.common

import com.meucoach.core.data.health.HealthConnectStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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
    fun `formats the last read relative to now`() {
        val now = Instant.parse("2026-11-03T18:00:00Z")

        assertThat(healthConnectLastSyncLabel(now.minusSeconds(10), now)).isEqualTo("Atualizado agora")
        assertThat(healthConnectLastSyncLabel(now.minus(Duration.ofMinutes(12)), now))
            .isEqualTo("Atualizado há 12 min")
        assertThat(healthConnectLastSyncLabel(now.minus(Duration.ofHours(3)), now))
            .isEqualTo("Atualizado há 3 h")
        assertThat(healthConnectLastSyncLabel(now.minus(Duration.ofDays(1)), now))
            .isEqualTo("Atualizado ontem")
        assertThat(
            healthConnectLastSyncLabel(
                lastSyncedAt = now.minus(Duration.ofDays(8)),
                now = now,
                zone = ZoneOffset.UTC,
            ),
        ).isEqualTo("Atualizado em 26/10")
        assertThat(healthConnectLastSyncLabel(null, now)).isNull()
    }

    @Test
    fun `only counts as connected when permissions were granted`() {
        assertThat(isHealthConnectConnected(HealthConnectStatus.AVAILABLE, true)).isTrue()
        assertThat(isHealthConnectConnected(HealthConnectStatus.AVAILABLE, false)).isFalse()
        assertThat(isHealthConnectConnected(HealthConnectStatus.UNAVAILABLE, true)).isFalse()
    }
}
