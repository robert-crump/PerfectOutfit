package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.RainClass
import com.example.perfectoutfit.core.model.headToToeGroups
import com.example.perfectoutfit.core.model.loggedRainClass
import com.example.perfectoutfit.core.model.roundedReferenceTemp
import com.example.perfectoutfit.ui.theme.extendedColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

/**
 * A logged outfit recommended for [targetTemp] in [targetRain] weather: a band with how it felt,
 * tinted by the rating; where and when it was worn; the reference temperature (in
 * [useApparentTemperature]'s basis) and rain class it was worn in, emphasized with the
 * difference when either isn't the target's (see [wornLine]);
 * the items per body part; and the notes. With [onEditOutfit] the title row carries a pencil.
 */
@Composable
fun OutfitCard(
    title: String,
    outfit: OutfitEntryWithDetails,
    useApparentTemperature: Boolean,
    targetTemp: Int,
    targetRain: RainClass,
    modifier: Modifier = Modifier,
    onEditOutfit: (() -> Unit)? = null
) {
    val itemGroups = outfit.clothingItems.headToToeGroups { it.bodyPart }
    val worn = Instant.ofEpochMilli(outfit.entry.createdAt).atZone(ZoneId.systemDefault())
    val whenLabel = "${entryDateLabel(worn.toLocalDate(), LocalDate.now(), relative = true)} · " +
        worn.format(HOUR_FORMAT)
    val wornAt = outfit.weatherSnapshot.roundedReferenceTemp(useApparentTemperature)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        CardTitleRow(
            title = title,
            action = onEditOutfit?.let { onEdit ->
                {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit outfit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        outfit.entry.rating?.let { RatingBand(it) }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaceLine(locationName = outfit.weatherSnapshot.locationName, timeLabel = whenLabel)
                WornAtLine(wornLine(wornAt, targetTemp, outfit.weatherSnapshot.loggedRainClass(), targetRain))
            }

            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemGroups.forEach { (part, items) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = part.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Lines its first line up with the items' larger text.
                            modifier = Modifier
                                .width(BODY_PART_LABEL_WIDTH)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = items.joinToString(", ") { it.name },
                            // The weight goes on the items, not the body part labels.
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val notes = outfit.entry.notes
            if (notes.isNotEmpty()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** "🥶 Too cold" edge to edge: neutral for perfect, cold or hot otherwise. */
@Composable
private fun RatingBand(rating: ComfortRating) {
    val (container, content) = ratingBandColors(rating)
    Text(
        text = "${stringResource(rating.emojiRes)} ${stringResource(rating.labelRes)}",
        style = MaterialTheme.typography.headlineMedium,
        color = content,
        modifier = Modifier
            .fillMaxWidth()
            .background(container)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** Container and content colour of [rating]'s band. */
@Composable
private fun ratingBandColors(rating: ComfortRating): Pair<Color, Color> = when (rating) {
    ComfortRating.PERFECT ->
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    ComfortRating.TOO_COLD ->
        MaterialTheme.extendedColors.coldContainer to MaterialTheme.extendedColors.onColdContainer
    ComfortRating.TOO_HOT ->
        MaterialTheme.extendedColors.hotContainer to MaterialTheme.extendedColors.onHotContainer
}

/** The worn line's text, and whether it is emphasized (a neighbor temperature or rain class). */
internal data class WornLine(val text: String, val temperatureDiffers: Boolean, val rainDiffers: Boolean) {
    val emphasized: Boolean get() = temperatureDiffers || rainDiffers
}

/**
 * "Worn at 14°C" on an exact match, naming a wet class ("Worn at 9°C in light rain"). When the
 * temperature or the class differs from the target's, the differences follow in brackets:
 * "Worn at 12°C (2°C colder)", "Worn at 9°C, dry (light rain expected)",
 * "Worn at 8°C in light rain (2°C warmer, heavy rain expected)".
 */
internal fun wornLine(wornAt: Int, targetTemp: Int, wornRain: RainClass, targetRain: RainClass): WornLine {
    val diff = wornAt - targetTemp
    val rainDiffers = wornRain != targetRain
    val rain = when {
        wornRain.isWet -> " in ${wornRain.label}"
        rainDiffers -> ", ${wornRain.label}"
        else -> ""
    }
    val differences = listOfNotNull(
        diff.takeIf { it != 0 }?.let { "${it.absoluteValue}°C ${if (it < 0) "colder" else "warmer"}" },
        if (!rainDiffers) null else if (targetRain.isWet) "${targetRain.label} expected" else "no rain expected"
    )
    val brackets = if (differences.isEmpty()) "" else " (${differences.joinToString(", ")})"
    return WornLine("Worn at $wornAt°C$rain$brackets", temperatureDiffers = diff != 0, rainDiffers = rainDiffers)
}

/**
 * [line] in the muted place-line style on an exact match; otherwise emphasized with a
 * thermometer (or an umbrella when only the rain class differs).
 */
@Composable
private fun WornAtLine(line: WornLine) {
    if (!line.emphasized) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (line.temperatureDiffers) Icons.Default.Thermostat else Icons.Default.Umbrella,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = line.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Fits "Head and Throat", the longest body part name, on one line. */
private val BODY_PART_LABEL_WIDTH = 112.dp

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
