package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.core.database.DatabaseTransactionRunner
import com.example.perfectoutfit.core.database.RoomTransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    abstract fun bindDatabaseTransactionRunner(impl: RoomTransactionRunner): DatabaseTransactionRunner
}
