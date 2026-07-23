package com.ethred.panorama.domain.usecase

import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.remote.dto.HotspotDto
import com.ethred.panorama.data.remote.dto.SceneDto
import com.ethred.panorama.data.remote.dto.TourManifestDto
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateTourManifestUseCase @Inject constructor(
    private val sessionRepository: CaptureSessionRepository
) {
    suspend fun execute(
        propertyId: String,
        sessions: List<CaptureSessionEntity>,
        outputDirectory: File
    ): Result<File> {
        return try {
            if (sessions.isEmpty()) {
                return Result.failure(Exception("No sessions available for manifest generation"))
            }

            val scenes = mutableListOf<SceneDto>()

            for (session in sessions) {
                val hotspots = sessionRepository.getHotspots(session.id)
                val hotspotDtos = hotspots.map { hs ->
                    val targetSession = sessionRepository.getSession(hs.toSessionId)
                    HotspotDto(
                        id = hs.id,
                        pitch = hs.pitch,
                        yaw = hs.yaw,
                        text = hs.label.ifEmpty { "Go to ${targetSession?.roomName ?: "Room"}" },
                        targetSceneId = "scene_${hs.toSessionId}"
                    )
                }

                scenes.add(
                    SceneDto(
                        id = "scene_${session.id}",
                        name = session.roomName,
                        fileUrl = session.outputPath ?: "",
                        thumbnailUrl = session.outputPath,
                        hotspots = hotspotDtos
                    )
                )
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val manifest = TourManifestDto(
                version = "1.0",
                propertyId = propertyId,
                defaultScene = scenes.first().id,
                createdAt = dateFormat.format(Date()),
                scenes = scenes
            )

            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(manifest)

            if (!outputDirectory.exists()) outputDirectory.mkdirs()
            val manifestFile = File(outputDirectory, "tour_manifest.json")
            manifestFile.writeText(jsonString)

            Result.success(manifestFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
