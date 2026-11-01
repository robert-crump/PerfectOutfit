package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.feature.recommendation.DegreeCell
import com.example.perfectoutfit.feature.recommendation.DegreeCoverage
import com.example.perfectoutfit.feature.recommendation.ItemRange
import com.example.perfectoutfit.feature.recommendation.TemperatureChart
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The chart's horizontal scale, shared by the strip, the axis and every row so they line up:
 * one equal-width cell per degree from [min] to [max], a degree drawn at its cell's centre.
 */
private class DegreeScale(val min: Int, val max: Int) {
    val count: Int get() = max - min + 1
    fun cellWidth(width: Float): Float = width / count
    fun xOf(temp: Int, width: Float): Float = (temp - min + 0.5f) * cellWidth(width)
    fun tempAt(x: Float, width: Float): Int =
        min + floor(x / cellWidth(width)).toInt().coerceIn(0, count - 1)
}

/** "7°C · 2 perfect outfits", or a hint when nothing is selected. */
internal fun selectionCaption(cell: DegreeCell?): String = when {
    cell == null -> "Touch the chart to pick a temperature"
    cell.coverage == DegreeCoverage.PERFECT ->
        "${cell.temp}°C · ${cell.perfectOutfits} perfect outfit${if (cell.perfectOutfits == 1) "" else "s"}"
    cell.coverage == DegreeCoverage.RATED -> "${cell.temp}°C · rated, none perfect"
    else -> "${cell.temp}°C · nothing rated"
}

internal fun rangeDescription(row: ItemRange): String =
    if (row.minTemp == row.maxTemp) "${row.item.name}, perfect at ${row.minTemp} °C"
    else "${row.item.name}, perfect from ${row.minTemp} to ${row.maxTemp} °C, " +
        "${row.perfectTemps.size} temperatures"

/**
 * Selects the degree under the finger, on the chart's [DegreeScale] across this element's width:
 * on a tap, and continuously while dragging sideways. A vertical drag is left to any scrolling
 * child, so it can wrap the scrolling rows. With [toggleOnTap], tapping the selected degree clears it.
 */
@Composable
internal fun Modifier.degreeSelection(
    chart: TemperatureChart,
    toggleOnTap: Boolean,
    onSelect: (temp: Int, toggle: Boolean) -> Unit
): Modifier {
    val scale = DegreeScale(chart.minTemp ?: return this, chart.maxTemp ?: return this)
    val select by rememberUpdatedState(onSelect)
    return this
        .pointerInput(scale.min, scale.max, toggleOnTap) {
            detectTapGestures { select(scale.tempAt(it.x, size.width.toFloat()), toggleOnTap) }
        }
        .pointerInput(scale.min, scale.max) {
            detectHorizontalDragGestures(
                onDragStart = { select(scale.tempAt(it.x, size.width.toFloat()), false) }
            ) { change, _ ->
                select(scale.tempAt(change.position.x, size.width.toFloat()), false)
            }
        }
}

/**
 * The selection caption, the coverage line and its labels: the chart's x-axis, pinned below the rows.
 * Tapping or dragging selects a degree; tapping the selected one clears it. For TalkBack the strip is one
 * adjustable control that announces the caption.
 */
@Composable
internal fun CoverageAxis(
    chart: TemperatureChart,
    selectedTemp: Int?,
    onSelect: (temp: Int, toggle: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = DegreeScale(chart.minTemp ?: return, chart.maxTemp ?: return)
    val select by rememberUpdatedState(onSelect)
    val caption = selectionCaption(selectedTemp?.let { chart.cellAt(it) })
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val marker = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(STRIP_HEIGHT)
                .degreeSelection(chart, toggleOnTap = true, onSelect = onSelect)
                .semantics {
                    contentDescription = "Temperature"
                    stateDescription = caption
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (selectedTemp ?: scale.min).toFloat(),
                        range = scale.min.toFloat()..scale.max.toFloat(),
                        steps = (scale.count - 2).coerceAtLeast(0)
                    )
                    setProgress { value ->
                        select(value.roundToInt(), false)
                        true
                    }
                }
        ) {
            // Drawn like an item row: a line across the whole axis, a dot per rated degree —
            // filled where an outfit was perfect, hollow where none was.
            val y = size.height / 2
            val radius = DOT_RADIUS.toPx()
            val stroke = 1.5.dp.toPx()
            drawLine(
                primary.copy(alpha = BAR_ALPHA),
                Offset(scale.xOf(scale.min, size.width), y),
                Offset(scale.xOf(scale.max, size.width), y),
                BAR_WIDTH.toPx(),
                StrokeCap.Round
            )
            chart.degrees.forEach { cell ->
                val centre = Offset(scale.xOf(cell.temp, size.width), y)
                when (cell.coverage) {
                    DegreeCoverage.PERFECT -> drawCircle(primary, radius, centre)
                    DegreeCoverage.RATED -> {
                        drawCircle(surface, radius, centre)
                        drawCircle(primary, radius - stroke / 2, centre, style = Stroke(stroke))
                    }
                    DegreeCoverage.NONE -> Unit
                }
            }
            if (selectedTemp != null) drawMarker(scale.xOf(selectedTemp, size.width), marker, 2.dp.toPx())
        }
        AxisLabels(scale, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** Both ends, plus every multiple of 5 not crowding an end. */
@Composable
private fun AxisLabels(scale: DegreeScale, modifier: Modifier) {
    val temps = buildList {
        add(scale.min)
        (scale.min + 1 until scale.max)
            .filter { it % 5 == 0 && it - scale.min >= 3 && scale.max - it >= 3 }
            .forEach(::add)
        if (scale.max != scale.min) add(scale.max)
    }
    Layout(
        content = {
            temps.forEach {
                Text(
                    "$it°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier.clearAndSetSemantics { }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val centre = scale.xOf(temps[i], width.toFloat())
                val x = (centre - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.place(x, 0)
            }
        }
    }
}

/**
 * One item: its name above a thin bar from its coldest to its warmest perfect degree, with a
 * dot at each perfect degree. Fades when a degree is selected that the bar doesn't span.
 */
@Composable
internal fun ItemRangeRow(row: ItemRange, chart: TemperatureChart, selectedTemp: Int?) {
    val scale = DegreeScale(chart.minTemp ?: return, chart.maxTemp ?: return)
    val primary = MaterialTheme.colorScheme.primary
    val faded = selectedTemp != null && !row.spans(selectedTemp)
    SelectionLine(chart, selectedTemp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (faded) FADED_ALPHA else 1f)
                .padding(vertical = 4.dp)
                .clearAndSetSemantics { contentDescription = rangeDescription(row) }
        ) {
            Text(text = row.item.name, style = MaterialTheme.typography.bodyMedium)
            Canvas(modifier = Modifier.fillMaxWidth().height(ROW_BAR_HEIGHT)) {
                val y = size.height / 2
                val xMin = scale.xOf(row.minTemp, size.width)
                val xMax = scale.xOf(row.maxTemp, size.width)
                if (xMax > xMin) {
                    drawLine(primary.copy(alpha = BAR_ALPHA), Offset(xMin, y), Offset(xMax, y), BAR_WIDTH.toPx(), StrokeCap.Round)
                }
                row.perfectTemps.forEach { drawCircle(primary, DOT_RADIUS.toPx(), Offset(scale.xOf(it, size.width), y)) }
            }
        }
    }
}

/** Draws the selected degree's vertical line behind [content], unfaded, across its full height. */
@Composable
private fun SelectionLine(chart: TemperatureChart, selectedTemp: Int?, content: @Composable () -> Unit) {
    val min = chart.minTemp
    val max = chart.maxTemp
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (selectedTemp != null && min != null && max != null) {
                    drawMarker(DegreeScale(min, max).xOf(selectedTemp, size.width), colour.copy(alpha = 0.6f), 1.dp.toPx())
                }
            }
    ) { content() }
}

private fun DrawScope.drawMarker(x: Float, colour: Color, width: Float) {
    drawLine(colour, Offset(x, 0f), Offset(x, size.height), width)
}

private val STRIP_HEIGHT = 28.dp
private val ROW_BAR_HEIGHT = 14.dp
private const val FADED_ALPHA = 0.35f
private const val BAR_ALPHA = 0.45f
private val BAR_WIDTH = 3.dp
private val DOT_RADIUS = 4.dp
