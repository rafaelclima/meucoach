package com.meucoach.app.ui.charts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.meucoach.core.designsystem.component.McBarChart
import com.meucoach.core.designsystem.component.McChartSeries
import com.meucoach.core.designsystem.component.McLineChart
import com.meucoach.core.designsystem.theme.McTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Vico can apply a chart model asynchronously. A screenshot test that samples
 * the chart before the series is applied records axes with no data and may even
 * save that state as a golden. These tests assert the series is painted once
 * composition settles, which is the contract of the synchronous model path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lineChartPaintsItsSeriesAfterCompositionSettles() {
        compose.setContent {
            McTheme(darkTheme = true) {
                McLineChart(
                    series = listOf(
                        McChartSeries(
                            label = "Série",
                            values = listOf(1.0, 3.0, 2.0, 4.0, 2.5),
                            color = SERIES_COLOR,
                        ),
                    ),
                    xLabels = listOf("1", "2", "3", "4", "5"),
                    contentDescription = "Gráfico de linha de teste",
                    showLegend = false,
                )
            }
        }

        assertThat(seriesPixels()).isGreaterThan(MIN_SERIES_PIXELS)
    }

    @Test
    fun barChartPaintsItsSeriesAfterCompositionSettles() {
        compose.setContent {
            McTheme(darkTheme = true) {
                McBarChart(
                    values = listOf(1.0, 2.0, null, 3.0, 2.0),
                    labels = listOf("1", "2", "3", "4", "5"),
                    color = SERIES_COLOR,
                    pointColors = List(5) { SERIES_COLOR },
                    contentDescription = "Gráfico de barras de teste",
                )
            }
        }

        assertThat(seriesPixels()).isGreaterThan(MIN_SERIES_PIXELS)
    }

    /** Counts pixels close enough to the series colour to exclude anti-aliasing noise. */
    private fun seriesPixels(): Int {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("mc-chart-ready")
            .captureToImage()
            .toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (
                    pixel.alpha > 0.5f &&
                    abs(pixel.red - SERIES_COLOR.red) < COLOR_TOLERANCE &&
                    abs(pixel.green - SERIES_COLOR.green) < COLOR_TOLERANCE &&
                    abs(pixel.blue - SERIES_COLOR.blue) < COLOR_TOLERANCE
                ) {
                    count++
                }
            }
        }
        return count
    }

    private companion object {
        val SERIES_COLOR = Color(0xFFE040FB)
        const val COLOR_TOLERANCE = 0.08f
        const val MIN_SERIES_PIXELS = 20
    }
}
