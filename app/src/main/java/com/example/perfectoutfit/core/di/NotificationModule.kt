package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.core.notification.AndroidBackupFailureNotifier
import com.example.perfectoutfit.core.notification.AndroidRatingReminder
import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import com.example.perfectoutfit.core.notification.RatingReminder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindRatingReminder(impl: AndroidRatingReminder): RatingReminder

    @Binds
    abstract fun bindBackupFailureNotifier(impl: AndroidBackupFailureNotifier): BackupFailureNotifier
}
