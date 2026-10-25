package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.core.location.AndroidLocationPermissions
import com.example.perfectoutfit.core.location.AndroidLocationSource
import com.example.perfectoutfit.core.location.LocationPermissions
import com.example.perfectoutfit.core.location.LocationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    abstract fun bindLocationSource(impl: AndroidLocationSource): LocationSource
}

/** Apart from [LocationModule], so tests that replace the location keep the real grant checks. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationPermissionsModule {

    @Binds
    abstract fun bindLocationPermissions(impl: AndroidLocationPermissions): LocationPermissions
}
