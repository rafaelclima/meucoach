package com.meucoach.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One line in an [McLineChart]. A `null` value creates a gap instead of a misleading straight
 * line across days without data.
 */
@Immutable
data class McChartSeries(
    val label: String,
    val values: List<Double?>,
    val color: Color,
    val area: Boolean = false,
    val dashed: Boolean = false,
    val showPoints: Boolean = false,
    val legend: Boolean = true,
)

/** A horizontal reference line, such as a target band edge or a personal baseline. */
@Immutable
data class McChartReference(val value: Double, val label: String? = null, val color: Color? = null)

/**
 * Line chart backed by Vico. It keeps the brand's axes, units, gaps and accessibility contract:
 * every chart has a [contentDescription] and a table alternative can be offered by the caller.
 */
@Composable
fun McLineChart(
    series: List<McChartSeries>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    ySuffix: String = "",
    yDecimals: Int = 0,
    yLabelCount: Int = 4,
    yStep: Double? = null,
    minY: Double? = null,
    maxY: Double? = null,
    labelSpacing: Int = 1,
    height: Dp = 180.dp,
    references: List<McChartReference> = emptyList(),
    showLegend: Boolean = true,
    scrollable: Boolean = false,
    contentDescription: String? = null,
    emptyLabel: String = "Sem dados no período.",
) {
    val colors = McTheme.colors
    val xCount = series.maxOfOrNull { it.values.size } ?: 0
    val allSeries = remember(series, references, xCount, colors.borderStrong) {
        series + references.map { reference ->
            McChartSeries(
                label = reference.label.orEmpty(),
                values = List(xCount) { reference.value },
                color = reference.color ?: colors.borderStrong,
                dashed = true,
                legend = false,
            )
        }
    }
    val lineSpecs = remember(allSeries) { allSeries.flatMap { splitIntoLineSegments(it) } }

    if (xCount == 0 || lineSpecs.isEmpty()) {
        McChartEmpty(
            label = emptyLabel,
            contentDescription = contentDescription,
            modifier = modifier.height(height),
        )
        return
    }

    // A manually built model is rendered synchronously, unlike a model producer
    // whose transaction is applied on another dispatcher. Screenshot tests and
    // Compose previews therefore never capture a chart with axes but no series.
    val chartModel = remember(lineSpecs) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                lineSpecs.forEach { spec ->
                    series(x = spec.x, y = spec.y)
                }
            },
        )
    }

    val lines = lineSpecs.map { spec ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(spec.color)),
            stroke = if (spec.dashed) {
                LineCartesianLayer.LineStroke.Dashed(
                    thickness = 1.dp,
                    cap = StrokeCap.Round,
                    dashLength = 5.dp,
                    gapLength = 4.dp,
                )
            } else {
                LineCartesianLayer.LineStroke.Continuous(
                    thickness = 2.5.dp,
                    cap = StrokeCap.Round,
                )
            },
            areaFill = if (spec.area) {
                LineCartesianLayer.AreaFill.single(
                    Fill(
                        Brush.verticalGradient(
                            listOf(spec.color.copy(alpha = 0.30f), Color.Transparent),
                        ),
                    ),
                )
            } else {
                null
            },
            pointProvider = if (spec.showPoints && !spec.dashed) {
                LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(
                        component = rememberShapeComponent(
                            fill = Fill(colors.surface),
                            shape = CircleShape,
                            strokeFill = Fill(spec.color),
                            strokeThickness = 2.dp,
                        ),
                        size = 7.dp,
                    ),
                )
            } else {
                null
            },
            interpolator = LineCartesianLayer.Interpolator.Sharp,
        )
    }

    val xFormatter = remember(xLabels) {
        CartesianValueFormatter { _, x, _ ->
            xLabels.getOrNull(x.roundToInt()) ?: x.roundToInt().toString()
        }
    }
    val yFormatter = remember(yDecimals, ySuffix) { decimalFormatter(yDecimals, ySuffix) }
    val axisLabel = rememberTextComponent(
        TextStyle(color = colors.textTertiary, fontSize = 10.sp),
    )
    val gridLine = rememberLineComponent(Fill(colors.borderSubtle), thickness = 1.dp)
    val marker = rememberChartMarker(
        xLabels = xLabels,
        ySuffix = ySuffix,
        yDecimals = yDecimals,
    )

    if (showLegend && series.any { it.legend }) {
        McChartLegend(series = series.filter { it.legend })
        Spacer(modifier = Modifier.height(10.dp))
    }

    val chartModifier = modifier
        .fillMaxWidth()
        .height(height)
        .testTag("mc-chart-ready")
        .then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                pointSpacing = if (scrollable) 22.dp else 12.dp,
                rangeProvider = if (minY != null || maxY != null) {
                    CartesianLayerRangeProvider.fixed(minY = minY, maxY = maxY)
                } else {
                    CartesianLayerRangeProvider.auto()
                },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = axisLabel,
                valueFormatter = yFormatter,
                tick = null,
                guideline = gridLine,
                itemPlacer = remember(yLabelCount, yStep) {
                    if (yStep != null) {
                        VerticalAxis.ItemPlacer.step(step = { yStep })
                    } else {
                        VerticalAxis.ItemPlacer.count(count = { yLabelCount })
                    }
                },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                label = axisLabel,
                valueFormatter = xFormatter,
                tick = null,
                guideline = null,
                itemPlacer = remember(labelSpacing) {
                    HorizontalAxis.ItemPlacer.aligned(
                        spacing = { labelSpacing },
                        shiftExtremeLabels = true,
                    )
                },
            ),
            marker = marker,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
        ),
        model = chartModel,
        modifier = chartModifier,
        scrollState = rememberVicoScrollState(
            scrollEnabled = scrollable,
            initialScroll = if (scrollable) Scroll.Absolute.End else Scroll.Absolute.Start,
        ),
        chartAreaHeight = height,
    )
}

/**
 * Column chart backed by Vico. Missing values are skipped rather than drawn as zero, and the
 * current period can be highlighted without changing the meaning of the colour.
 */
@Composable
fun McBarChart(
    values: List<Double?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    mutedColor: Color? = null,
    pointColors: List<Color>? = null,
    highlightIndex: Int? = null,
    ySuffix: String = "",
    yDecimals: Int = 0,
    yLabelCount: Int = 4,
    yStep: Double? = null,
    minY: Double? = 0.0,
    maxY: Double? = null,
    labelSpacing: Int = 1,
    height: Dp = 140.dp,
    columnWidth: Dp? = null,
    scrollable: Boolean = false,
    contentDescription: String? = null,
    emptyLabel: String = "Sem dados no período.",
) {
    val colors = McTheme.colors
    val barColor = color ?: colors.accent
    val barMutedColor = mutedColor ?: barColor.copy(alpha = 0.42f)
    val barWidth = columnWidth ?: when {
        values.size > 20 -> 5.dp
        values.size > 14 -> 7.dp
        else -> 12.dp
    }
    val barCorner = if (barWidth < 8.dp) 3.dp else 6.dp
    val columns = remember(values) {
        values.mapIndexedNotNull { index, value -> value?.let { index to it } }
    }
    if (columns.isEmpty()) {
        McChartEmpty(
            label = emptyLabel,
            contentDescription = contentDescription,
            modifier = modifier.height(height),
        )
        return
    }

    val chartModel = remember(columns) {
        CartesianChartModel(
            ColumnCartesianLayerModel.build {
                series(
                    x = columns.map { it.first },
                    y = columns.map { it.second },
                )
            },
        )
    }

    val highlightColumn = rememberLineComponent(
        fill = Fill(barColor),
        thickness = barWidth,
        shape = RoundedCornerShape(topStart = barCorner, topEnd = barCorner),
    )
    val mutedColumn = rememberLineComponent(
        fill = Fill(barMutedColor),
        thickness = barWidth,
        shape = RoundedCornerShape(topStart = barCorner, topEnd = barCorner),
    )
    val customColumns = mutableMapOf<Color, LineComponent>()
    pointColors.orEmpty().distinct().forEach { customColor ->
        customColumns[customColor] = rememberLineComponent(
            fill = Fill(customColor),
            thickness = barWidth,
            shape = RoundedCornerShape(topStart = barCorner, topEnd = barCorner),
        )
    }
    val provider = remember(
        highlightColumn,
        mutedColumn,
        highlightIndex,
        pointColors,
        customColumns,
    ) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore): LineComponent {
                val index = entry.x.roundToInt()
                pointColors?.getOrNull(index)?.let { customColumns[it] }?.let { return it }
                return if (index == highlightIndex) highlightColumn else mutedColumn
            }

            override fun getWidestSeriesColumn(
                seriesKey: Any,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = highlightColumn
        }
    }

    val xFormatter = remember(labels) {
        CartesianValueFormatter { _, x, _ ->
            labels.getOrNull(x.roundToInt()) ?: x.roundToInt().toString()
        }
    }
    val yFormatter = remember(yDecimals, ySuffix) { decimalFormatter(yDecimals, ySuffix) }
    val axisLabel = rememberTextComponent(
        TextStyle(color = colors.textTertiary, fontSize = 10.sp),
    )
    val gridLine = rememberLineComponent(Fill(colors.borderSubtle), thickness = 1.dp)
    val marker = rememberChartMarker(
        xLabels = labels,
        ySuffix = ySuffix,
        yDecimals = yDecimals,
    )

    val chartModifier = modifier
        .fillMaxWidth()
        .height(height)
        .testTag("mc-chart-ready")
        .then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = provider,
                columnCollectionSpacing = 8.dp,
                rangeProvider = if (minY != null || maxY != null) {
                    CartesianLayerRangeProvider.fixed(minY = minY, maxY = maxY)
                } else {
                    CartesianLayerRangeProvider.auto()
                },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = axisLabel,
                valueFormatter = yFormatter,
                tick = null,
                guideline = gridLine,
                itemPlacer = remember(yLabelCount, yStep) {
                    if (yStep != null) {
                        VerticalAxis.ItemPlacer.step(step = { yStep })
                    } else {
                        VerticalAxis.ItemPlacer.count(count = { yLabelCount })
                    }
                },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                label = axisLabel,
                valueFormatter = xFormatter,
                tick = null,
                guideline = null,
                itemPlacer = remember(labelSpacing) {
                    HorizontalAxis.ItemPlacer.aligned(
                        spacing = { labelSpacing },
                        shiftExtremeLabels = true,
                    )
                },
            ),
            marker = marker,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
        ),
        model = chartModel,
        modifier = chartModifier,
        scrollState = rememberVicoScrollState(
            scrollEnabled = scrollable,
            initialScroll = if (scrollable) Scroll.Absolute.End else Scroll.Absolute.Start,
        ),
        chartAreaHeight = height,
    )
}

@Composable
fun McChartLegend(series: List<McChartSeries>, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        series.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(item.color),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.label,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun McChartEmpty(label: String, contentDescription: String?, modifier: Modifier) {
    val colors = McTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = McTextStyles.caption, color = colors.textTertiary)
    }
}

@Composable
private fun rememberChartMarker(xLabels: List<String>, ySuffix: String, yDecimals: Int): DefaultCartesianMarker {
    val colors = McTheme.colors
    val label = rememberTextComponent(
        style = TextStyle(color = colors.textPrimary, fontSize = 11.sp),
        padding = Insets(horizontal = 8.dp, vertical = 5.dp),
        background = rememberShapeComponent(
            fill = Fill(colors.surfaceElevated),
            shape = RoundedCornerShape(8.dp),
            strokeFill = Fill(colors.borderStrong),
            strokeThickness = 1.dp,
        ),
    )
    val formatter = remember(xLabels, ySuffix, yDecimals) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull()
            if (target == null) {
                ""
            } else {
                val date = xLabels.getOrNull(target.x.roundToInt()).orEmpty()
                val values = when (target) {
                    is LineCartesianLayerMarkerTarget ->
                        target.points
                            .joinToString(" · ") { point ->
                                formatValue(point.entry.y, yDecimals) + ySuffix
                            }

                    is ColumnCartesianLayerMarkerTarget ->
                        target.columns
                            .joinToString(" · ") { column ->
                                formatValue(column.entry.y, yDecimals) + ySuffix
                            }

                    else -> ""
                }
                listOf(date, values).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
    }
    return DefaultCartesianMarker(
        label = label,
        valueFormatter = formatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.Top,
        guideline = rememberLineComponent(
            fill = Fill(colors.borderStrong),
            thickness = 1.dp,
        ),
    )
}

private data class LineSpec(
    val x: List<Int>,
    val y: List<Double>,
    val color: Color,
    val area: Boolean,
    val dashed: Boolean,
    val showPoints: Boolean,
)

private fun splitIntoLineSegments(series: McChartSeries): List<LineSpec> {
    val segments = mutableListOf<LineSpec>()
    var xs = mutableListOf<Int>()
    var ys = mutableListOf<Double>()
    fun flush() {
        if (xs.isNotEmpty()) {
            segments += LineSpec(
                x = xs,
                y = ys,
                color = series.color,
                area = series.area,
                dashed = series.dashed,
                showPoints = series.showPoints,
            )
            xs = mutableListOf()
            ys = mutableListOf()
        }
    }
    series.values.forEachIndexed { index, value ->
        if (value == null) {
            flush()
        } else {
            xs += index
            ys += value
        }
    }
    flush()
    return segments
}

private fun decimalFormatter(decimals: Int, suffix: String): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        formatValue(value, decimals) + suffix
    }

private fun formatValue(value: Double, decimals: Int): String = String.format(CHART_LOCALE, "%.${decimals}f", value)

private val CHART_LOCALE = Locale.forLanguageTag("pt-BR")
