package com.meucoach.app.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/** Public copy must stand on its own: no plan, no "no alvo", no judgement. */
class StoryHighlightsCalculatorTest {
    @Test
    fun `celebrates a negative split`() {
        val plan = plan(
            splits = listOf(1 to 400, 2 to 395, 3 to 390, 4 to 380),
            averagePace = 392,
        )

        val highlights = StoryHighlightsCalculator.calculate(plan)

        assertThat(highlights.headline).isEqualTo("2ª METADE MAIS RÁPIDA")
        assertThat(highlights.proof).contains("mais rápida")
        assertThat(highlights.fastestKm).isEqualTo(4)
        assertThat(highlights.closingLine).isEqualTo("Cada corrida conta.")
    }

    @Test
    fun `calls out a steady pace`() {
        val plan = plan(
            splits = listOf(1 to 360, 2 to 362, 3 to 358, 4 to 361),
            averagePace = 360,
        )

        val highlights = StoryHighlightsCalculator.calculate(plan)

        assertThat(highlights.headline).isEqualTo("RITMO CONSTANTE")
        assertThat(highlights.proof).contains("variação")
    }

    @Test
    fun `falls back to the fastest kilometre when pace swings`() {
        val plan = plan(
            splits = listOf(1 to 420, 2 to 360, 3 to 430, 4 to 415),
            averagePace = 406,
        )

        val highlights = StoryHighlightsCalculator.calculate(plan)

        assertThat(highlights.headline).isEqualTo("KM MAIS RÁPIDO")
        assertThat(highlights.fastestKm).isEqualTo(2)
        assertThat(highlights.proof).contains("km 2")
        assertThat(highlights.proof).contains("6:00")
    }

    @Test
    fun `a short run falls back to a neutral closing`() {
        val plan = plan(splits = listOf(1 to 360), averagePace = 360)

        val highlights = StoryHighlightsCalculator.calculate(plan)

        assertThat(highlights.headline).isEqualTo("CORRIDA CONCLUÍDA")
        assertThat(highlights.fastestKm).isEqualTo(1)
    }

    @Test
    fun `never mentions the prescribed session`() {
        val plan = plan(listOf(1 to 400, 2 to 380, 3 to 390, 4 to 410), averagePace = 395)

        val copy = listOfNotNull(
            StoryHighlightsCalculator.calculate(plan).headline,
            StoryHighlightsCalculator.calculate(plan).proof,
            StoryHighlightsCalculator.calculate(plan).closingLine,
        ).joinToString(" ").lowercase()

        assertThat(copy).doesNotContain("longão", "forte demais", "no alvo", "leve demais")
    }

    private fun plan(splits: List<Pair<Int, Int>>, averagePace: Int): StoryPlan {
        val distanceKm = splits.size.toDouble()
        return StoryPlan(
            route = com.meucoach.app.ui.activitydetail.RouteData(
                points = listOf(
                    com.meucoach.app.ui.activitydetail.RoutePoint(0f, 0f),
                    com.meucoach.app.ui.activitydetail.RoutePoint(1f, 1f),
                ),
            ),
            splits = splits.map { (km, seconds) ->
                StorySplit(
                    km = km,
                    pace = Pace.fromSecondsPerKm(seconds),
                    heartRate = 150,
                    calories = 60,
                    progress = km / distanceKm.toFloat(),
                )
            },
            segments = emptyList(),
            durationSec = 30f,
            distanceKm = distanceKm,
            durationTotalSec = averagePace * splits.size,
            calories = 240,
            averagePace = Pace.fromSecondsPerKm(averagePace),
            averageHeartRate = 150,
            boundingBox = null,
        )
    }
}
