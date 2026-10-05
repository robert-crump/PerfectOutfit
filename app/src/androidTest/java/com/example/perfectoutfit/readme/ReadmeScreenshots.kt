package com.example.perfectoutfit.readme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.perfectoutfit.MainActivity
import com.example.perfectoutfit.core.database.DatabaseTransactionRunner
import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.di.ClockModule
import com.example.perfectoutfit.core.di.LocationModule
import com.example.perfectoutfit.core.di.ThemeModule
import com.example.perfectoutfit.core.di.WeatherApiModule
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.network.OpenMeteoApi
import com.example.perfectoutfit.ui.theme.ThemeOptions
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Screenshots for the README, from [DemoHistory] outfits and the [DemoForecast] for Aachen with the
 * clock pinned to today at noon. Run through `./gradlew readmeScreenshots`, which also clears the
 * app's data, sets up a clean status bar and copies the PNGs to `docs/screenshots/`. Replaces the
 * app's outfits and settings, so [EmulatorOnlyRule] skips it without the argument and refuses
 * real devices.
 */
@HiltAndroidTest
@UninstallModules(WeatherApiModule::class, LocationModule::class, ClockModule::class, ThemeModule::class)
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmulatorOnlyRule())
        .around(hilt)
        .around(GrantPermissionRule.grant(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        .around(compose)

    @BindValue @JvmField
    val clock: Clock = Clock.fixed(today.atTime(DemoForecast.START_HOUR, 0).atZone(zone).toInstant(), zone)

    @BindValue @JvmField
    val weatherApi: OpenMeteoApi = DemoForecast.Api { today }

    @BindValue @JvmField
    val location: LocationSource = object : LocationSource {
        override suspend fun current() = LocationResult.Found(DemoForecast.LAT, DemoForecast.LON, DemoForecast.PLACE)
    }

    @BindValue @JvmField
    val themeOptions = ThemeOptions(dynamicColor = false)

    @Inject lateinit var clothingItemDao: ClothingItemDao
    @Inject lateinit var weatherSnapshotDao: WeatherSnapshotDao
    @Inject lateinit var outfitEntryDao: OutfitEntryDao
    @Inject lateinit var outfitItemDao: OutfitItemDao
    @Inject lateinit var transactionRunner: DatabaseTransactionRunner

    private lateinit var context: Context
    private lateinit var screenshots: ReadmeScreenshotCapture
    private lateinit var outfits: List<DemoHistory.Outfit>
    private var pendingEntryId = 0L

    @Before
    fun setUp() {
        hilt.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        screenshots = ReadmeScreenshotCapture.cleared(context)

        // Home's noon ride, the coldest and warmest hour of the 3-hour run, and Explorer's stop.
        val noon = feelsLike(0)
        outfits = DemoHistory.generate(
            today,
            perfectAt = listOf(
                Sport.CYCLING to noon,
                Sport.RUNNING to noon,
                Sport.RUNNING to warmestOfRun(),
                Sport.CYCLING to EXPLORER_TEMP
            )
        )
        runBlocking { seed(outfits) }
    }

    @Test
    fun captureReadmeScreenshots() {
        captureHomeAndExplorer()
        captureWorkout()
        captureHistory()
        captureRate()
    }

    /** Home's cycling recommendation for noon, then Explorer from it, slid to a cold stop. */
    private fun captureHomeAndExplorer() {
        ActivityScenario.launch(MainActivity::class.java).use {
            click("Cycling")
            waitForText("Use outfit")
            screenshots.capture(compose, "home")

            compose.onAllNodesWithContentDescription("Outfit Explorer").onFirst().performClick()
            waitForText("What you wore")
            val stops = outfits
                .filter { it.sport == Sport.CYCLING && it.rating != null }
                .map { it.feelsLike.roundToInt() }
                .distinct()
                .sorted()
            val index = stops.indices.minBy { abs(stops[it] - EXPLORER_TEMP) }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).onFirst()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
            waitForText("${stops[index]}°C")
            screenshots.capture(compose, "explorer")
        }
    }

    /** A 3-hour run from noon, on its warmest hour. */
    private fun captureWorkout() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Long workout")
            compose.onAllNodes(isToggleable()).onFirst().performClick()
            click("+")
            waitForText("3h")
            click("Running")
            waitForText("Warmest", substring = true)
            compose.onAllNodesWithText("Warmest", substring = true).onFirst().performClick()
            waitForText("Use outfit")
            screenshots.capture(compose, "workout")
        }
    }

    private fun captureHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            click("History")
            waitForText("Outfit History")
            screenshots.capture(compose, "history")
        }
    }

    /**
     * Yesterday's ride, opened from its rating reminder, scrolled down to the rating bar. The
     * deep link's own scroll can run before the item chips have loaded, so it isn't relied on.
     */
    private fun captureRate() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("perfectoutfit://rate?outfitEntryId=$pendingEntryId"))
            .setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitForText("Rate Outfit")
            waitForText("Thin Gloves")
            Thread.sleep(RATE_FLASH_MS)
            compose.onAllNodesWithText("Too hot", substring = true).onFirst().performScrollTo()
            screenshots.capture(compose, "rate")
        }
    }

    private suspend fun seed(outfits: List<DemoHistory.Outfit>) {
        val catalog = clothingItemDao.getAll().associateBy { it.sport to it.name }
        transactionRunner.runInTransaction {
            outfits.forEach { outfit ->
                val workoutMs = outfit.time.atZone(zone).toInstant().toEpochMilli()
                val snapshotId = weatherSnapshotDao.insert(
                    WeatherSnapshot(
                        timestamp = workoutMs,
                        latitude = DemoForecast.LAT,
                        longitude = DemoForecast.LON,
                        locationName = DemoForecast.PLACE,
                        temperatureCelsius = outfit.temperature,
                        apparentTemperatureCelsius = outfit.feelsLike,
                        windSpeedKmh = outfit.windKmh,
                        windDirectionDegrees = 240,
                        uvIndex = outfit.uv,
                        cloudCoverPercent = outfit.cloud,
                        precipitationProbabilityPercent = outfit.rain
                    )
                )
                val entryId = outfitEntryDao.insert(
                    OutfitEntry(
                        weatherSnapshotId = snapshotId,
                        sport = outfit.sport,
                        comfortRating = outfit.rating,
                        createdAt = workoutMs,
                        ratedAt = outfit.rating?.let { workoutMs + RATED_AFTER.toMillis() }
                    )
                )
                outfitItemDao.insertAll(outfit.items.map { name ->
                    val item = catalog[outfit.sport to name] ?: error("No default ${outfit.sport} item \"$name\"")
                    OutfitItem(entryId, item.id)
                })
                if (outfit.rating == null) pendingEntryId = entryId
            }
        }
    }

    /** Rounded feels-like temperature [hoursFromNow] after the pinned clock. */
    private fun feelsLike(hoursFromNow: Int): Int =
        DemoForecast.hour(today.atTime(DemoForecast.START_HOUR + hoursFromNow, 0)).apparent.roundToInt()

    private fun warmestOfRun(): Int = (0 until RUN_HOURS).maxOf(::feelsLike)

    private fun click(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text).onFirst().performClick()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val RUN_HOURS = 3
        const val EXPLORER_TEMP = 1
        const val UI_TIMEOUT_MS = 30_000L
        /** The reminder deep link scrolls to the rating bar and flashes it for about 1.4 s. */
        const val RATE_FLASH_MS = 2_000L
        val RATED_AFTER: Duration = Duration.ofHours(2)
    }
}
