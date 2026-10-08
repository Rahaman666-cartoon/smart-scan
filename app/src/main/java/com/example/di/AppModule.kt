package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.DocumentDao
import com.example.data.DocumentRepository
import com.example.data.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartscan_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao
    }

    @Provides
    @Singleton
    fun provideDocumentRepository(documentDao: DocumentDao): DocumentRepository {
        return DocumentRepository(documentDao)
    }

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }
}

