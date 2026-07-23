package com.ethred.panorama.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ethred.panorama.data.local.db.UploadQueueDao
import com.ethred.panorama.data.local.db.UploadQueueEntity
import com.ethred.panorama.data.remote.EthredApiService
import com.ethred.panorama.data.remote.dto.AttachMediaRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadQueueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadQueueDao: UploadQueueDao,
    private val apiService: EthredApiService
) {
    suspend fun enqueueUpload(
        sessionId: String,
        propertyId: String,
        localFilePath: String,
        mediaCategory: String,
        sortOrder: Int = 0
    ): UploadQueueEntity {
        val item = UploadQueueEntity(
            sessionId = sessionId,
            propertyId = propertyId,
            localFilePath = localFilePath,
            mediaCategory = mediaCategory,
            sortOrder = sortOrder
        )
        uploadQueueDao.insert(item)
        return item
    }

    /**
     * FR-SYNC-02: Generates 512×256 thumbnail of panorama JPEG before uploading,
     * then enqueues both thumbnail (sortOrder 0) and high-res panorama to upload_queue.
     */
    suspend fun enqueuePanoramaWithThumbnail(
        sessionId: String,
        propertyId: String,
        panoramaFilePath: String,
        sortOrder: Int = 1
    ) = withContext(Dispatchers.IO) {
        // 1. Generate 512x256 thumbnail
        val thumbPath = generate512x256Thumbnail(sessionId, panoramaFilePath)

        // 2. Enqueue thumbnail image first (sortOrder = 0)
        if (thumbPath != null) {
            enqueueUpload(
                sessionId = sessionId,
                propertyId = propertyId,
                localFilePath = thumbPath,
                mediaCategory = "IMAGE",
                sortOrder = 0
            )
        }

        // 3. Enqueue main high-res panorama JPEG
        enqueueUpload(
            sessionId = sessionId,
            propertyId = propertyId,
            localFilePath = panoramaFilePath,
            mediaCategory = "IMAGE",
            sortOrder = sortOrder
        )
    }

    private fun generate512x256Thumbnail(sessionId: String, sourcePath: String): String? {
        return try {
            val srcFile = File(sourcePath)
            if (!srcFile.exists()) return null

            val originalBitmap = BitmapFactory.decodeFile(sourcePath) ?: return null
            val thumbBitmap = Bitmap.createScaledBitmap(originalBitmap, 512, 256, true)

            val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val thumbFile = File(thumbDir, "THUMB_$sessionId.jpg")

            val outStream = FileOutputStream(thumbFile)
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
            outStream.flush()
            outStream.close()

            originalBitmap.recycle()
            thumbBitmap.recycle()

            thumbFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun getQueueForProperty(propertyId: String): Flow<List<UploadQueueEntity>> {
        return uploadQueueDao.getQueueForProperty(propertyId)
    }

    suspend fun getPendingItems(): List<UploadQueueEntity> {
        return uploadQueueDao.getPendingItems()
    }

    suspend fun processItem(item: UploadQueueEntity): Result<String> {
        return try {
            uploadQueueDao.update(item.copy(status = UploadQueueEntity.STATUS_IN_PROGRESS))

            val file = File(item.localFilePath)
            if (!file.exists()) {
                val errorMsg = "Local file not found at ${item.localFilePath}"
                uploadQueueDao.update(item.copy(status = UploadQueueEntity.STATUS_FAILED, retryCount = item.retryCount + 1))
                return Result.failure(Exception(errorMsg))
            }

            val mimeType = if (item.mediaCategory == "DOCUMENT") "application/json" else "image/jpeg"
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val uploadResponse = apiService.uploadFile(body)
            if (!uploadResponse.isSuccessful || uploadResponse.body() == null) {
                uploadQueueDao.update(item.copy(status = UploadQueueEntity.STATUS_FAILED, retryCount = item.retryCount + 1))
                return Result.failure(Exception("File upload failed: ${uploadResponse.code()}"))
            }

            val uploadedUrl = uploadResponse.body()!!.fileUrl

            val attachResponse = apiService.attachMedia(
                propertyId = item.propertyId,
                request = AttachMediaRequest(
                    fileUrl = uploadedUrl,
                    mediaCategory = item.mediaCategory,
                    sortOrder = item.sortOrder
                )
            )

            if (!attachResponse.isSuccessful) {
                uploadQueueDao.update(item.copy(status = UploadQueueEntity.STATUS_FAILED, retryCount = item.retryCount + 1))
                return Result.failure(Exception("Attach media failed: ${attachResponse.code()}"))
            }

            val completedItem = item.copy(
                fileUrl = uploadedUrl,
                status = UploadQueueEntity.STATUS_DONE
            )
            uploadQueueDao.update(completedItem)
            Result.success(uploadedUrl)

        } catch (e: Exception) {
            uploadQueueDao.update(item.copy(status = UploadQueueEntity.STATUS_FAILED, retryCount = item.retryCount + 1))
            Result.failure(e)
        }
    }
}
