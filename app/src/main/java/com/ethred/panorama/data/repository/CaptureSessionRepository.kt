package com.ethred.panorama.data.repository

import com.ethred.panorama.data.local.db.CaptureSessionDao
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.local.db.CapturedFrameDao
import com.ethred.panorama.data.local.db.CapturedFrameEntity
import com.ethred.panorama.data.local.db.HotspotDao
import com.ethred.panorama.data.local.db.HotspotEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureSessionRepository @Inject constructor(
    private val sessionDao: CaptureSessionDao,
    private val frameDao: CapturedFrameDao,
    private val hotspotDao: HotspotDao
) {
    suspend fun createSession(propertyId: String, roomName: String): CaptureSessionEntity {
        val session = CaptureSessionEntity(
            propertyId = propertyId,
            roomName = roomName
        )
        sessionDao.insertSession(session)
        return session
    }

    suspend fun getSession(sessionId: String): CaptureSessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    fun getSessionsForProperty(propertyId: String): Flow<List<CaptureSessionEntity>> {
        return sessionDao.getSessionsForProperty(propertyId)
    }

    fun getAllCompletedSessions(): Flow<List<CaptureSessionEntity>> {
        return sessionDao.getAllCompletedSessions()
    }

    suspend fun saveFrame(sessionId: String, filePath: String, yaw: Float, pitch: Float, roll: Float) {
        val frame = CapturedFrameEntity(
            sessionId = sessionId,
            filePath = filePath,
            yawDeg = yaw,
            pitchDeg = pitch,
            rollDeg = roll
        )
        frameDao.insertFrame(frame)

        // Update session frame count
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            val updatedFrames = frameDao.getFramesForSession(sessionId).size
            sessionDao.updateSession(session.copy(frameCount = updatedFrames))
        }
    }

    suspend fun getFrames(sessionId: String): List<CapturedFrameEntity> {
        return frameDao.getFramesForSession(sessionId)
    }

    suspend fun updateSessionStatus(sessionId: String, status: String, outputPath: String? = null, qualityScore: Int = 0) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(
            session.copy(
                status = status,
                outputPath = outputPath ?: session.outputPath,
                qualityScore = if (qualityScore > 0) qualityScore else session.qualityScore
            )
        )
    }

    suspend fun addHotspot(fromSessionId: String, toSessionId: String, pitch: Float, yaw: Float, label: String): HotspotEntity {
        val hotspot = HotspotEntity(
            fromSessionId = fromSessionId,
            toSessionId = toSessionId,
            pitch = pitch,
            yaw = yaw,
            label = label
        )
        hotspotDao.insertHotspot(hotspot)
        return hotspot
    }

    suspend fun getHotspots(sessionId: String): List<HotspotEntity> {
        return hotspotDao.getHotspotsFromSession(sessionId)
    }

    suspend fun clearFrames(sessionId: String) {
        frameDao.deleteFramesForSession(sessionId)
    }
}
