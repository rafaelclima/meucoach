package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class UnitsTest {
    @Test
    fun `formats pace with zero padded seconds`() {
        assertThat(Pace.fromSecondsPerKm(460).format()).isEqualTo("7:40")
        assertThat(Pace.fromSecondsPerKm(364).format()).isEqualTo("6:04")
        assertThat(Pace.fromSecondsPerKm(600).format()).isEqualTo("10:00")
    }

    @Test
    fun `creates pace from minutes and seconds`() {
        assertThat(Pace.of(7, 40).secondsPerKm).isEqualTo(460)
        assertThat(Pace.of(0, 30).secondsPerKm).isEqualTo(30)
    }

    @Test
    fun `converts between pace and speed`() {
        assertThat(Pace.fromSpeedMetersPerSecond(2.5).secondsPerKm).isEqualTo(400)
        assertThat(Pace.fromSecondsPerKm(250).speedMetersPerSecond()).isEqualTo(4.0)
    }

    @Test
    fun `rejects invalid pace and time parts`() {
        assertThatThrownBy { Pace.fromSecondsPerKm(0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Pace.of(-1, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Pace.of(1, 60) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Pace.fromSpeedMetersPerSecond(0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `converts distance to kilometers`() {
        assertThat(Distance.ofKilometers(5.11).kilometers).isEqualTo(5.11)
        assertThat(Distance(1_000.0).kilometers).isEqualTo(1.0)
    }

    @Test
    fun `rejects invalid distance`() {
        assertThatThrownBy { Distance(-1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Distance(Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
