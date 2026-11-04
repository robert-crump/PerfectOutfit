package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.WeatherReading
import kotlin.math.roundToInt

/** The neutral container shared by Home's cards and the outfit detail cards. */
@Composable
fun neutralCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/**
 * "Feels like (Actual 7°C)": the basis matching uses, then the other value as an aside, so a
 * surprising number explains itself.
 */
@Composable
fun BasisLine(weather: WeatherReading, useApparentTemperature: Boolean) {
    val basis = stringResource(
        if (useApparentTemperature) R.string.temperature_apparent else R.string.temperature_real
    )
    val otherLabel = stringResource(
        if (useApparentTemperature) R.string.temperature_real else R.string.temperature_apparent
    )
    val otherTemp = (
        if (useApparentTemperature) weather.temperatureCelsius else weather.apparentTemperatureCelsius
    ).roundToInt()
    val asideColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            append(basis)
            withStyle(SpanStyle(color = asideColor)) { append(" ($otherLabel ${otherTemp}°C)") }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** UV, wind and rain side by side; the UV icon is highlighted when [uvHigh]. */
@Composable
fun WeatherMetricsRow(weather: WeatherReading, uvHigh: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        WeatherMetric(
            icon = Icons.Default.WbSunny,
            label = "UV",
            value = "${weather.uvIndex}",
            unit = "",
            iconTint = if (uvHigh) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        WeatherMetric(
            icon = Icons.Default.Air,
            label = "Wind",
            value = "${weather.windSpeedKmh.toInt()}",
            unit = " km/h",
            modifier = Modifier.weight(1f)
        )
        WeatherMetric(
            icon = Icons.Default.WaterDrop,
            label = "Rain",
            value = "${weather.precipitationProbabilityPercent}",
            unit = " %",
            modifier = Modifier.weight(1f)
        )
    }
}

/** One centred column: icon and label above, the value and its unit below. */
@Composable
private fun WeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value + unit,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
