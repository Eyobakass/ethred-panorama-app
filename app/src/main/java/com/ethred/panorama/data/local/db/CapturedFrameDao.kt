package com.ethred.panorama.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CapturedFrameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: CapturedFrameEntity)

    @Query("SELECT * FROM captured_frames WHERE sessionId = :sessionId ORDER BY capturedAt ASC")
    suspend fun getFramesForSession(sessionId: String): List<CapturedFrameEntity>

    @Query("DELETE FROM captured_frames WHERE sessionId = :sessionId")
    suspend fun deleteFramesForSession(sessionId: String)
}
