package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class StoryFormattersTest {
    @Test
    fun `formats short runs as minutes and seconds`() {
        assertThat(formatStoryDuration(0)).isEqualTo("0:00")
        assertThat(formatStoryDuration(59)).isEqualTo("0:59")
        assertThat(formatStoryDuration(1_694)).isEqualTo("28:14")
    }

    @Test
    fun `adds hours only when the run reaches them`() {
        assertThat(formatStoryDuration(3_600)).isEqualTo("1:00:00")
        assertThat(formatStoryDuration(3_725)).isEqualTo("1:02:05")
    }

    @Test
    fun `rejects negative durations instead of inventing a time`() {
        assertThatThrownBy { formatStoryDuration(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
