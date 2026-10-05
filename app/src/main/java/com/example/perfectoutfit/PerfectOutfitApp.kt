package com.example.perfectoutfit

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class PerfectOutfitApp : PerfectOutfitBaseApp()

/**
 * Everything the app class does besides being the Hilt root, shared with the androidTest
 * application (`@CustomTestApplication` builds on it). Hilt test applications can't have
 * `@Inject` fields, so the worker factory comes through an entry point.
 */
open class PerfectOutfitBaseApp : Application(), Configuration.Provider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java).workerFactory()
            )
            .build()
}
