package com.ethred.panorama.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "hotspots")
data class HotspotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromSessionId: String,
    val toSessionId: String,
    val pitch: Float,
    val yaw: Float,
    val label: String
)
