package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoryCardSelectionTest {
    @Test
    fun `a ten kilometre run gets eight cards and ten split segments`() {
        val splits = splits(10, fastestKm = 4, slowestKm = 9)

        val cards = StoryCardSelector.select(splits)
        val bar = StorySplitBarCalculator.calculate(splits, cards.map { it.km }.toSet())

        assertThat(cards).hasSize(8)
        assertThat(cards.map { it.km }).contains(1, 10, 4, 9)
        assertThat(cards.map { it.km }).doesNotHaveDuplicates()
        assertThat(bar.segments).hasSize(10)
        assertThat(bar.segments.map { it.startKm }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertThat(bar.segments.count { it.hasCard }).isEqualTo(8)
    }

    @Test
    fun `short runs keep every kilometre as a card`() {
        val splits = splits(6)

        val cards = StoryCardSelector.select(splits)

        assertThat(cards.map { it.km }).containsExactly(1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `long runs still cap at eight cards but keep the important ones`() {
        val splits = splits(21, fastestKm = 7, slowestKm = 18)

        val cards = StoryCardSelector.select(splits)

        assertThat(cards).hasSize(8)
        assertThat(cards.map { it.km }).contains(1, 21, 7, 18)
        assertThat(cards.map { it.km }).doesNotHaveDuplicates()
    }

    @Test
    fun `the split bar groups long runs into labelled blocks`() {
        val splits = splits(21)
        val cards = StoryCardSelector.select(splits)
        val bar = StorySplitBarCalculator.calculate(splits, cards.map { it.km }.toSet())

        assertThat(bar.segments).hasSize(11)
        assertThat(bar.segments.first().label).isEqualTo("1–2")
        assertThat(bar.segments.last().label).isEqualTo("21")
    }

    @Test
    fun `block size grows with the run length`() {
        assertThat(StorySplitBarCalculator.blockSize(10)).isEqualTo(1)
        assertThat(StorySplitBarCalculator.blockSize(20)).isEqualTo(1)
        assertThat(StorySplitBarCalculator.blockSize(21)).isEqualTo(2)
        assertThat(StorySplitBarCalculator.blockSize(30)).isEqualTo(2)
        assertThat(StorySplitBarCalculator.blockSize(40)).isEqualTo(3)
        assertThat(StorySplitBarCalculator.blockSize(60)).isEqualTo(4)
        assertThat(StorySplitBarCalculator.blockSize(61)).isEqualTo(5)
    }

    @Test
    fun `the fastest and slowest cards are always selected even when they are interior`() {
        val splits = splits(30, fastestKm = 12, slowestKm = 24)

        val cards = StoryCardSelector.select(splits)

        assertThat(cards.map { it.km }).contains(1, 30, 12, 24)
    }

    @Test
    fun `elevation gain uses hysteresis and never invents a flat value`() {
        val altitudes = listOf(100.0, 100.5, 101.2, 100.8, 100.2, 101.0, 102.4, 102.0, 103.5, 103.0, 104.2)

        assertThat(StoryElevation.gain(altitudes)).isEqualTo(4.5)
        assertThat(StoryElevation.gain(altitudes.take(9))).isNull()
    }

    private fun splits(count: Int, fastestKm: Int = 1, slowestKm: Int = count): List<StorySplit> =
        (1..count).map { km ->
            val pace = when (km) {
                fastestKm -> 280
                slowestKm -> 420
                else -> 330 + (km % 5)
            }
            StorySplit(
                km = km,
                pace = Pace.fromSecondsPerKm(pace),
                heartRate = 150,
                progress = km / count.toFloat(),
            )
        }
}
