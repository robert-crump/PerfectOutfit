package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.core.network.OpenMeteoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/** Open-Meteo, on its own so the androidTest README screenshots can replace it with a demo forecast. */
@Module
@InstallIn(SingletonComponent::class)
object WeatherApiModule {

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, json: Json): OpenMeteoApi =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
}
