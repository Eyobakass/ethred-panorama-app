package com.ethred.panorama.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val propertyId: String,
    val roomName: String,
    val status: String = STATUS_CAPTURING, // CAPTURING, STITCHING, DONE, UPLOADING, UPLOADED, FAILED
    val frameCount: Int = 0,
    val outputPath: String? = null,
    val qualityScore: Int = 0, // 1 to 5 stars
    val createdAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null
) {
    companion object {
        const val STATUS_CAPTURING = "CAPTURING"
        const val STATUS_STITCHING = "STITCHING"
        const val STATUS_DONE = "DONE"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
    }
}
