package com.ethred.panorama.data.repository

import com.ethred.panorama.data.local.db.UploadQueueDao
import com.ethred.panorama.data.local.db.UploadQueueEntity
import com.ethred.panorama.data.remote.EthredApiService
import com.ethred.panorama.data.remote.dto.AttachMediaRequest
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadQueueRepository @Inject constructor(
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
