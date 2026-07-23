package com.ethred.panorama.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CaptureSessionEntity::class,
        CapturedFrameEntity::class,
        HotspotEntity::class,
        UploadQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureSessionDao(): CaptureSessionDao
    abstract fun capturedFrameDao(): CapturedFrameDao
    abstract fun hotspotDao(): HotspotDao
    abstract fun uploadQueueDao(): UploadQueueDao
}
