package com.example.perfectoutfit.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** The system clock, on its own so the androidTest README screenshots can pin the time. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
