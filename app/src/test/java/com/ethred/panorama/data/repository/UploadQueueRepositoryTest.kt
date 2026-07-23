package com.ethred.panorama.data.repository

import com.ethred.panorama.data.local.db.UploadQueueDao
import com.ethred.panorama.data.local.db.UploadQueueEntity
import com.ethred.panorama.data.remote.EthredApiService
import com.ethred.panorama.data.remote.dto.AttachMediaRequest
import com.ethred.panorama.data.remote.dto.PropertyMediaDto
import com.ethred.panorama.data.remote.dto.UploadResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import retrofit2.Response
import java.io.File

class UploadQueueRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: android.content.Context
    private lateinit var mockDao: UploadQueueDao
    private lateinit var mockApi: EthredApiService
    private lateinit var repository: UploadQueueRepository

    @Before
    fun setUp() {
        mockContext = mock(android.content.Context::class.java)
        mockDao = mock(UploadQueueDao::class.java)
        mockApi = mock(EthredApiService::class.java)
        repository = UploadQueueRepository(mockContext, mockDao, mockApi)
    }

    @Test
    fun testProcessItem_fileNotFound_marksFailedAndIncrementsRetry() = runBlocking {
        val item = UploadQueueEntity(
            id = "upload_1",
            sessionId = "sess_1",
            propertyId = "prop_1",
            localFilePath = "/non/existent/file.jpg",
            mediaCategory = "IMAGE"
        )

        val result = repository.processItem(item)

        assertTrue(result.isFailure)
        verify(mockDao).update(argThat { it.status == UploadQueueEntity.STATUS_FAILED && it.retryCount == 1 })
    }

    @Test
    fun testProcessItem_successfulUploadAndAttach_marksDone() = runBlocking {
        val testFile = tempFolder.newFile("test_pano.jpg")
        testFile.writeText("dummy image content")

        val item = UploadQueueEntity(
            id = "upload_1",
            sessionId = "sess_1",
            propertyId = "prop_1",
            localFilePath = testFile.absolutePath,
            mediaCategory = "IMAGE"
        )

        val uploadSuccessResponse = Response.success(UploadResponse("https://server.com/uploads/test_pano.jpg"))
        val attachSuccessResponse = Response.success(
            PropertyMediaDto("media_1", "prop_1", "https://server.com/uploads/test_pano.jpg", "IMAGE", 1)
        )

        `when`(mockApi.uploadFile(any(MultipartBody.Part::class.java))).thenReturn(uploadSuccessResponse)
        `when`(mockApi.attachMedia(eq("prop_1"), any(AttachMediaRequest::class.java))).thenReturn(attachSuccessResponse)

        val result = repository.processItem(item)

        assertTrue(result.isSuccess)
        assertEquals("https://server.com/uploads/test_pano.jpg", result.getOrNull())
        verify(mockDao).update(argThat { it.status == UploadQueueEntity.STATUS_DONE && it.fileUrl == "https://server.com/uploads/test_pano.jpg" })
    }
}
