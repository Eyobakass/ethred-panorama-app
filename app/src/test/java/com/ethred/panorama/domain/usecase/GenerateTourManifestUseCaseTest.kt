package com.ethred.panorama.domain.usecase

import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.local.db.HotspotEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*

class GenerateTourManifestUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockRepository: CaptureSessionRepository
    private lateinit var useCase: GenerateTourManifestUseCase

    @Before
    fun setUp() {
        mockRepository = mock(CaptureSessionRepository::class.java)
        useCase = GenerateTourManifestUseCase(mockRepository)
    }

    @Test
    fun testExecute_emptySessions_returnsFailure() = runBlocking {
        val result = useCase.execute("prop_123", emptyList(), tempFolder.root)
        assertTrue(result.isFailure)
    }

    @Test
    fun testExecute_validSessions_createsTourManifestJson() = runBlocking {
        val session1 = CaptureSessionEntity(
            id = "sess_1",
            propertyId = "prop_123",
            roomName = "Living Room",
            outputPath = "/path/to/living_room_360.jpg"
        )
        val session2 = CaptureSessionEntity(
            id = "sess_2",
            propertyId = "prop_123",
            roomName = "Kitchen",
            outputPath = "/path/to/kitchen_360.jpg"
        )

        val hotspot = HotspotEntity(
            id = "hs_1",
            fromSessionId = "sess_1",
            toSessionId = "sess_2",
            pitch = -5.2f,
            yaw = 112.5f,
            label = "Go to Kitchen"
        )

        `when`(mockRepository.getHotspots("sess_1")).thenReturn(listOf(hotspot))
        `when`(mockRepository.getHotspots("sess_2")).thenReturn(emptyList())
        `when`(mockRepository.getSession("sess_2")).thenReturn(session2)

        val result = useCase.execute("prop_123", listOf(session1, session2), tempFolder.root)

        assertTrue(result.isSuccess)
        val manifestFile = result.getOrNull()
        assertNotNull(manifestFile)
        assertTrue(manifestFile!!.exists())

        val jsonContent = manifestFile.readText()
        assertTrue(jsonContent.contains("\"property_id\": \"prop_123\""))
        assertTrue(jsonContent.contains("\"name\": \"Living Room\""))
        assertTrue(jsonContent.contains("\"name\": \"Kitchen\""))
        assertTrue(jsonContent.contains("\"text\": \"Go to Kitchen\""))
        assertTrue(jsonContent.contains("\"target_scene_id\": \"scene_sess_2\""))
    }
}
