package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
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
    cell == null -> "Tap or drag along the strip to pick a temperature"
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
 * The coverage strip, its axis labels and the selection caption, pinned above the rows.
 * Tapping or dragging selects a degree; tapping the selected one clears it ([onSelect]'s
 * second argument says whether the gesture may toggle). For TalkBack the strip is one
 * adjustable control that announces the caption.
 */
@Composable
internal fun CoverageHeader(
    chart: TemperatureChart,
    selectedTemp: Int?,
    onSelect: (temp: Int, toggle: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = DegreeScale(chart.minTemp ?: return, chart.maxTemp ?: return)
    val select by rememberUpdatedState(onSelect)
    val caption = selectionCaption(selectedTemp?.let { chart.cellAt(it) })
    val primary = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(STRIP_HEIGHT)
                .pointerInput(scale.min, scale.max) {
                    detectTapGestures { select(scale.tempAt(it.x, size.width.toFloat()), true) }
                }
                .pointerInput(scale.min, scale.max) {
                    detectHorizontalDragGestures(
                        onDragStart = { select(scale.tempAt(it.x, size.width.toFloat()), false) }
                    ) { change, _ ->
                        select(scale.tempAt(change.position.x, size.width.toFloat()), false)
                    }
                }
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
            val cellW = scale.cellWidth(size.width)
            val gap = minOf(1.dp.toPx(), cellW * 0.15f)
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            chart.degrees.forEachIndexed { i, cell ->
                val left = i * cellW + gap / 2
                val cellSize = Size(cellW - gap, size.height)
                when (cell.coverage) {
                    DegreeCoverage.PERFECT -> drawRect(primary, Offset(left, 0f), cellSize)
                    DegreeCoverage.RATED -> {
                        val stroke = 1.dp.toPx()
                        drawRect(
                            primary,
                            Offset(left + stroke / 2, stroke / 2),
                            Size(cellSize.width - stroke, cellSize.height - stroke),
                            style = Stroke(stroke)
                        )
                    }
                    DegreeCoverage.NONE -> Unit
                }
            }
            if (selectedTemp != null) drawMarker(scale.xOf(selectedTemp, size.width), marker, 2.dp.toPx())
        }
        AxisLabels(scale, Modifier.fillMaxWidth().padding(top = 4.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
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

@Composable
internal fun BodyPartHeader(bodyPart: BodyPart, chart: TemperatureChart, selectedTemp: Int?) {
    SelectionLine(chart, selectedTemp) {
        Text(
            text = bodyPart.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
        )
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
                    drawLine(primary.copy(alpha = 0.45f), Offset(xMin, y), Offset(xMax, y), 3.dp.toPx(), StrokeCap.Round)
                }
                row.perfectTemps.forEach { drawCircle(primary, 4.dp.toPx(), Offset(scale.xOf(it, size.width), y)) }
            }
        }
    }
}

/** Items tagged for the activity type that no perfect outfit has included yet. */
@Composable
internal fun NotYetPerfect(items: List<ClothingItem>) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Not yet in a perfect outfit: " + items.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
