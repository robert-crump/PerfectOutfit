package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.ui.theme.ThemeOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Theme switches, on their own so the androidTest README screenshots can use the app's own palette. */
@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {

    @Provides
    fun provideThemeOptions(): ThemeOptions = ThemeOptions(dynamicColor = true)
}
