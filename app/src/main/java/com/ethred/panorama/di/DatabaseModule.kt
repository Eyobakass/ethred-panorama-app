package com.ethred.panorama.di

import android.content.Context
import androidx.room.Room
import com.ethred.panorama.data.local.db.AppDatabase
import com.ethred.panorama.data.local.db.CaptureSessionDao
import com.ethred.panorama.data.local.db.CapturedFrameDao
import com.ethred.panorama.data.local.db.HotspotDao
import com.ethred.panorama.data.local.db.UploadQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ethred_360_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideCaptureSessionDao(db: AppDatabase): CaptureSessionDao = db.captureSessionDao()

    @Provides
    fun provideCapturedFrameDao(db: AppDatabase): CapturedFrameDao = db.capturedFrameDao()

    @Provides
    fun provideHotspotDao(db: AppDatabase): HotspotDao = db.hotspotDao()

    @Provides
    fun provideUploadQueueDao(db: AppDatabase): UploadQueueDao = db.uploadQueueDao()
}
