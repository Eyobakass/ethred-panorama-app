package com.ethred.panorama.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HotspotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHotspot(hotspot: HotspotEntity)

    @Query("SELECT * FROM hotspots WHERE fromSessionId = :sessionId")
    suspend fun getHotspotsFromSession(sessionId: String): List<HotspotEntity>

    @Query("DELETE FROM hotspots WHERE id = :hotspotId")
    suspend fun deleteHotspot(hotspotId: String)
}
