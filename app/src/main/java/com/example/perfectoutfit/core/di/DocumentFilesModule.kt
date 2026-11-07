package com.example.perfectoutfit.core.di

import com.example.perfectoutfit.feature.settings.ContentResolverDocumentFiles
import com.example.perfectoutfit.feature.settings.DocumentFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentFilesModule {

    @Binds
    abstract fun bindDocumentFiles(impl: ContentResolverDocumentFiles): DocumentFiles
}
