package com.ethred.panorama.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.stitching.NativeStitcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@HiltWorker
class StitchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: CaptureSessionRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SESSION_ID   = "key_session_id"
        const val KEY_NADIR_OPTION = "key_nadir_option"
        const val KEY_PROGRESS     = "progress"
        const val KEY_STAGE        = "stage"
        private const val TIMEOUT_MS = 20L * 60 * 1000  // 20 minutes hard limit
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId   = inputData.getString(KEY_SESSION_ID)   ?: return@withContext Result.failure()
        val nadirOption = inputData.getInt(KEY_NADIR_OPTION, 0)

        val session = sessionRepository.getSession(sessionId) ?: return@withContext Result.failure()
        val frames  = sessionRepository.getFrames(sessionId)

        // Require at least 10 captured frames. The 85% threshold only applies
        // when the user has fully finished capturing (it's a completion gate, not a floor).
        val minRequired = maxOf(10, (frames.size * 0.70).toInt())
        if (frames.size < 10) {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Too few frames captured (${frames.size}). Capture at least 10 frames for stitching.")
            )
        }

        sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_STITCHING)

        // Report initial stage to UI
        setProgressAsync(workDataOf(KEY_STAGE to "Decoding frames…", KEY_PROGRESS to 0.05f))

        val outputDir  = File(applicationContext.filesDir, "panoramas/$sessionId").apply { mkdirs() }
        val outputFile = File(outputDir, "equirectangular_360.jpg")

        val framePaths = frames.map { it.filePath }.toTypedArray()
        val yaws       = frames.map { it.yawDeg }.toFloatArray()
        val pitches    = frames.map { it.pitchDeg }.toFloatArray()
        val rolls      = frames.map { it.rollDeg }.toFloatArray()

        setProgressAsync(workDataOf(KEY_STAGE to "Running stitching pipeline…", KEY_PROGRESS to 0.15f))

        // Hard 20-minute timeout — native call blocks this coroutine
        val stitchResult = withTimeoutOrNull(TIMEOUT_MS) {
            val nativeStitcher = NativeStitcher()
            nativeStitcher.nativeStitchFrames(
                framePaths    = framePaths,
                yaws          = yaws,
                pitches       = pitches,
                rolls         = rolls,
                outputPath    = outputFile.absolutePath,
                nadirCapOption = nadirOption
            )
        }

        if (stitchResult == null) {
            // Timed out
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(workDataOf("error" to "Stitching timed out after 20 minutes"))
        }

        setProgressAsync(workDataOf(KEY_STAGE to "Finalising…", KEY_PROGRESS to 0.95f))

        return@withContext if (stitchResult.isSuccess && stitchResult.outputPath != null) {
            sessionRepository.updateSessionStatus(
                sessionId    = sessionId,
                status       = CaptureSessionEntity.STATUS_DONE,
                outputPath   = stitchResult.outputPath,
                qualityScore = stitchResult.qualityScore
            )
            setProgressAsync(workDataOf(KEY_STAGE to "Done", KEY_PROGRESS to 1.0f))
            Result.success()
        } else {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            Result.failure(workDataOf("error" to (stitchResult.errorMessage ?: "Native stitcher failed")))
        }
    }
}
