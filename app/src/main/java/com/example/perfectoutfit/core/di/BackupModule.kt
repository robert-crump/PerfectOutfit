package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.feature.backup.BackupStateStore
import com.example.perfectoutfit.feature.backup.DriveBackupService
import com.example.perfectoutfit.feature.backup.DriveClient
import com.example.perfectoutfit.feature.backup.SharedPreferencesBackupStateStore
import com.example.perfectoutfit.feature.settings.ExportImportManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    abstract fun bindBackupStateStore(impl: SharedPreferencesBackupStateStore): BackupStateStore

    companion object {
        @Provides
        @Singleton
        fun provideDriveBackupService(
            drive: DriveClient,
            exportImport: ExportImportManager,
            state: BackupStateStore,
            clock: Clock
        ): DriveBackupService = DriveBackupService(drive, exportImport, state, clock)
    }
}
