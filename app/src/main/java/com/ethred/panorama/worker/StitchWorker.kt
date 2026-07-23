package com.ethred.panorama.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.stitching.NativeStitcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class StitchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: CaptureSessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
        val nadirOption = inputData.getInt(KEY_NADIR_OPTION, 0)

        val session = sessionRepository.getSession(sessionId) ?: return@withContext Result.failure()
        val frames = sessionRepository.getFrames(sessionId)

        if (frames.size < 16) {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure()
        }

        sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_STITCHING)

        val outputDir = File(applicationContext.filesDir, "panoramas/$sessionId")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "equirectangular_360.jpg")

        val framePaths = frames.map { it.filePath }.toTypedArray()
        val yaws = frames.map { it.yawDeg }.toFloatArray()
        val pitches = frames.map { it.pitchDeg }.toFloatArray()
        val rolls = frames.map { it.rollDeg }.toFloatArray()

        val nativeStitcher = NativeStitcher()
        val stitchResult = nativeStitcher.nativeStitchFrames(
            framePaths = framePaths,
            yaws = yaws,
            pitches = pitches,
            rolls = rolls,
            outputPath = outputFile.absolutePath,
            nadirCapOption = nadirOption
        )

        if (stitchResult.isSuccess && stitchResult.outputPath != null) {
            sessionRepository.updateSessionStatus(
                sessionId = sessionId,
                status = CaptureSessionEntity.STATUS_DONE,
                outputPath = stitchResult.outputPath,
                qualityScore = stitchResult.qualityScore
            )
            Result.success()
        } else {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            Result.failure()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "key_session_id"
        const val KEY_NADIR_OPTION = "key_nadir_option"
    }
}
