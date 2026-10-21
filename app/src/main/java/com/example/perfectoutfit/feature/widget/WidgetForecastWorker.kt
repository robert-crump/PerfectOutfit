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
import com.example.perfectoutfit.core.network.OpenMeteoApi
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

/** Refetches the saved place's forecast and redraws the widget; nothing to do while no place is saved. */
@HiltWorker
class WidgetForecastWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: OpenMeteoApi,
    private val store: WidgetForecastStore,
    private val widgetUpdater: WidgetUpdater
) : CoroutineWorker(context, params) {

    // A failed fetch is retried by the next periodic run; the redraw still moves the widget to
    // the current hour of the hours already saved.
    override suspend fun doWork(): Result {
        val saved = store.load() ?: return Result.success()
        try {
            val hours = WeatherMapper.extractAllHours(api.getForecast(saved.location.lat, saved.location.lon))
            store.saveIfStillAt(saved.location, hours)
        } catch (e: IOException) {
            // Offline: keep the saved hours.
        } catch (e: HttpException) {
            // The API refused: keep the saved hours.
        } catch (e: SerializationException) {
            // Malformed response: keep the saved hours.
        }
        widgetUpdater.redraw()
        return Result.success()
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
