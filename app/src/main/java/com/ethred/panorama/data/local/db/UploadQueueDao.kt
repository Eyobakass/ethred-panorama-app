package com.ethred.panorama.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UploadQueueEntity)

    @Update
    suspend fun update(item: UploadQueueEntity)

    @Query("SELECT * FROM upload_queue WHERE status = 'PENDING' ORDER BY sortOrder ASC")
    suspend fun getPendingItems(): List<UploadQueueEntity>

    @Query("SELECT * FROM upload_queue WHERE propertyId = :propertyId")
    fun getQueueForProperty(propertyId: String): Flow<List<UploadQueueEntity>>

    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun delete(id: String)
}
