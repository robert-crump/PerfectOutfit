package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.feature.home.HourlyWeather
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val ColorGreen  = Color(0xFF2E7D32)
private val ColorYellow = Color(0xFFF57F17)
private val ColorRed    = Color(0xFFC62828)

private fun uvColor(uv: Int) = when {
    uv <= 2 -> ColorGreen
    uv <= 5 -> ColorYellow
    else    -> ColorRed
}
private fun windColor(kmh: Double) = when {
    kmh < 10  -> ColorGreen
    kmh <= 20 -> ColorYellow
    else      -> ColorRed
}
private fun rainColor(pct: Int) = when {
    pct < 20  -> ColorGreen
    pct < 50  -> ColorYellow
    else      -> ColorRed
}

@Composable
fun WeatherCard(
    hourlyWeather: List<HourlyWeather>,
    selectedIndex: Int = 0,
    onHourSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "Weather Forecast",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Scrollable hour row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                hourlyWeather.forEachIndexed { index, hour ->
                    val isSelected = index == selectedIndex
                    val bgColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface

                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable { onHourSelected(index) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = hour.time.format(timeFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${hour.temperatureCelsius.toInt()}\u00B0",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "\u2193${hour.apparentTemperatureCelsius.toInt()}\u00B0",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Selected hour detail with color-coded indicators
            val selected = hourlyWeather.getOrNull(selectedIndex)
            if (selected != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ColoredIndicator(
                        label = "UV",
                        value = "${selected.uvIndex}",
                        color = uvColor(selected.uvIndex)
                    )
                    ColoredIndicator(
                        label = "Wind",
                        value = "${selected.windSpeedKmh.toInt()} km/h",
                        color = windColor(selected.windSpeedKmh)
                    )
                    ColoredIndicator(
                        label = "Rain",
                        value = "${selected.precipitationProbabilityPercent}%",
                        color = rainColor(selected.precipitationProbabilityPercent)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Cloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selected.cloudCoverPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColoredIndicator(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
