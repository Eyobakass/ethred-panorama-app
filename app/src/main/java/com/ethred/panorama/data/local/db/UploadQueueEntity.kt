package com.ethred.panorama.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val propertyId: String,
    val localFilePath: String,
    val fileUrl: String? = null,
    val mediaCategory: String, // "IMAGE" or "DOCUMENT"
    val sortOrder: Int = 0,
    val retryCount: Int = 0,
    val status: String = STATUS_PENDING // PENDING, IN_PROGRESS, DONE, FAILED
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
