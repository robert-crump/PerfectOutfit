package com.example.perfectoutfit.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.perfectoutfit.feature.widget.AndroidWidgetUpdater
import com.example.perfectoutfit.feature.widget.WidgetForecastData
import com.example.perfectoutfit.feature.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.widgetForecastDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_forecast")

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetUpdater(impl: AndroidWidgetUpdater): WidgetUpdater

    companion object {
        @Provides
        @Singleton
        @WidgetForecastData
        fun provideWidgetForecastDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.widgetForecastDataStore
    }
}
