package com.ethred.panorama.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CaptureSessionEntity)

    @Update
    suspend fun updateSession(session: CaptureSessionEntity)

    @Query("SELECT * FROM capture_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): CaptureSessionEntity?

    @Query("SELECT * FROM capture_sessions WHERE propertyId = :propertyId ORDER BY createdAt DESC")
    fun getSessionsForProperty(propertyId: String): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM capture_sessions WHERE status = 'DONE' ORDER BY createdAt DESC")
    fun getAllCompletedSessions(): Flow<List<CaptureSessionEntity>>

    @Query("DELETE FROM capture_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
