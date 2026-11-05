package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.core.network.GeocodingApi
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

/** Open-Meteo city search, on its own so tests can replace it. */
@Module
@InstallIn(SingletonComponent::class)
object GeocodingApiModule {

    @Provides
    @Singleton
    fun provideGeocodingApi(client: OkHttpClient, json: Json): GeocodingApi =
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeocodingApi::class.java)
}
