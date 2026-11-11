package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.RainClass
import com.example.perfectoutfit.core.model.WeatherReading
import com.example.perfectoutfit.core.model.formatPercent
import com.example.perfectoutfit.core.model.formatWindKmh
import com.example.perfectoutfit.core.model.rainAmountLine
import com.example.perfectoutfit.feature.home.WeatherThresholds
import kotlin.math.roundToInt

/** The neutral container shared by Home's cards and the outfit detail cards. */
@Composable
fun neutralCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** A warning line at the bottom of [WeatherCard]. */
data class WeatherHint(val icon: ImageVector, val text: String)

/**
 * One hour's weather: its reference temperature, basis, place and time, then UV, wind and rain,
 * then any [hints]. Shared by Home's recommendation and the history entry's Rate screen.
 * With [rainAmountClass] the Rain metric adds the amount and that class ("1.3 mm · light rain").
 */
@Composable
fun WeatherCard(
    displayTemp: Int,
    weather: WeatherReading,
    useApparentTemperature: Boolean,
    locationName: String,
    timeLabel: String?,
    hints: List<WeatherHint> = emptyList(),
    rainAmountClass: RainClass? = null
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(text = "${displayTemp}°C", style = MaterialTheme.typography.displayMedium)
                BasisLine(weather = weather, useApparentTemperature = useApparentTemperature)
                Spacer(modifier = Modifier.height(4.dp))
                PlaceLine(locationName = locationName, timeLabel = timeLabel)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WeatherMetricsRow(
                weather = weather,
                uvHigh = WeatherThresholds.isUvHigh(weather.uvIndex),
                rainAmountClass = rainAmountClass
            )
            if (hints.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hints.forEach { WeatherHintLine(it) }
                }
            }
        }
    }
}

@Composable
private fun WeatherHintLine(hint: WeatherHint) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            hint.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(text = hint.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * "📍 Aachen · Now": a long place name ellipsizes, the time stays. Without a place name only
 * the time shows.
 */
@Composable
fun PlaceLine(locationName: String, timeLabel: String?) {
    val hasPlace = locationName.isNotBlank()
    if (!hasPlace && timeLabel == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasPlace) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = locationName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (timeLabel != null) {
            Text(
                text = if (hasPlace) " · $timeLabel" else timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

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

/**
 * UV, wind and rain side by side; the UV icon is highlighted when [uvHigh]. With
 * [rainAmountClass], the rain column adds a small line with the amount and that class.
 */
@Composable
fun WeatherMetricsRow(
    weather: WeatherReading,
    uvHigh: Boolean,
    modifier: Modifier = Modifier,
    rainAmountClass: RainClass? = null
) {
    Row(modifier = modifier.fillMaxWidth()) {
        WeatherMetric(
            icon = Icons.Default.WbSunny,
            label = "UV",
            value = "${weather.uvIndex}",
            iconTint = if (uvHigh) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        WeatherMetric(
            icon = Icons.Default.Air,
            label = "Wind",
            value = formatWindKmh(weather.windSpeedKmh),
            modifier = Modifier.weight(1f)
        )
        WeatherMetric(
            icon = Icons.Default.WaterDrop,
            label = "Rain",
            value = formatPercent(weather.precipitationProbabilityPercent),
            detail = rainAmountClass?.let { rainAmountLine(weather.precipitationMm, it) },
            modifier = Modifier.weight(1f)
        )
    }
}

/** One centred column: icon and label above, the value below, then an optional small [detail]. */
@Composable
private fun WeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
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
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
