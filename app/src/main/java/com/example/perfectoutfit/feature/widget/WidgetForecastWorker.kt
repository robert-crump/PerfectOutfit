package com.example.perfectoutfit.feature.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.perfectoutfit.core.location.LocationPermissions
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.core.network.OpenMeteoApi
import com.example.perfectoutfit.feature.forecast.ForecastLocation
import com.example.perfectoutfit.feature.home.WeatherMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Runs [WidgetForecastRefresh]; a failed fetch is retried by the next periodic run. */
@HiltWorker
class WidgetForecastWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refresh: WidgetForecastRefresh
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        refresh.run()
        return Result.success()
    }
}

/**
 * Refetches the widget's forecast and redraws it. With background location granted it follows
 * the device; otherwise (or when no location comes back) it stays on the saved place. Nothing to
 * do while neither is known.
 */
class WidgetForecastRefresh @Inject constructor(
    private val api: OpenMeteoApi,
    private val store: WidgetForecastStore,
    private val widgetUpdater: WidgetUpdater,
    private val locationSource: LocationSource,
    private val locationPermissions: LocationPermissions
) {
    // The redraw also runs after a failed fetch: it moves the widget to the current hour of the
    // hours already saved.
    suspend fun run() {
        val here = deviceLocation()
        val saved = store.load()?.location
        val location = here ?: saved ?: return
        try {
            val hours = WeatherMapper.extractAllHours(api.getForecast(location.lat, location.lon))
            if (here != null) store.save(here, hours) else store.saveIfStillAt(location, hours)
        } catch (e: IOException) {
            // Offline: keep the saved hours.
        } catch (e: HttpException) {
            // The API refused: keep the saved hours.
        } catch (e: SerializationException) {
            // Malformed response: keep the saved hours.
        }
        widgetUpdater.redraw()
    }

    private suspend fun deviceLocation(): ForecastLocation? {
        if (!locationPermissions.hasBackground()) return null
        val found = locationSource.current() as? LocationResult.Found ?: return null
        return ForecastLocation(found.lat, found.lon, found.name)
    }
}

/** Runs [WidgetForecastWorker] every 30 minutes while at least one widget is placed. */
@Singleton
class WidgetForecastScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Starts the periodic refresh and fetches once right away. */
    fun schedule() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetForecastWorker>(30, TimeUnit.MINUTES).build()
        )
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetForecastWorker>().build()
        )
    }

    fun cancel() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "widget_forecast"
        const val IMMEDIATE_WORK_NAME = "widget_forecast_now"
    }
}
