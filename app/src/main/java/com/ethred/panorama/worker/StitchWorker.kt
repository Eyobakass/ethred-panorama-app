package com.ethred.panorama.worker

import android.content.Context
import android.util.Log
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
        private const val TAG = "StitchWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId   = inputData.getString(KEY_SESSION_ID)   ?: return@withContext Result.failure(
            workDataOf("error" to "Missing session ID")
        )
        val nadirOption = inputData.getInt(KEY_NADIR_OPTION, 0)

        Log.i(TAG, "Starting stitch for session=$sessionId nadirOption=$nadirOption")

        val session = sessionRepository.getSession(sessionId) ?: return@withContext Result.failure(
            workDataOf("error" to "Session not found in database")
        )
        val frames  = sessionRepository.getFrames(sessionId)
        Log.i(TAG, "Loaded ${frames.size} frames from DB for session=$sessionId")

        // Require at least 10 captured frames
        if (frames.size < 10) {
            Log.e(TAG, "Too few frames: ${frames.size}")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Too few frames (${frames.size}). Need at least 10. Retake more slowly.")
            )
        }

        // Verify frame files actually exist on disk
        val validFrames = frames.filter { File(it.filePath).exists() }
        Log.i(TAG, "${validFrames.size}/${frames.size} frame files exist on disk")
        if (validFrames.size < 10) {
            Log.e(TAG, "Frame files missing from disk! Only ${validFrames.size} found")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Frame files missing from disk (${validFrames.size}/${frames.size} found). Please retake.")
            )
        }

        sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_STITCHING)
        setProgressAsync(workDataOf(KEY_STAGE to "Decoding frames…", KEY_PROGRESS to 0.05f))

        val outputDir  = File(applicationContext.filesDir, "panoramas/$sessionId").apply { mkdirs() }
        val outputFile = File(outputDir, "equirectangular_360.jpg")

        val framePaths = validFrames.map { it.filePath }.toTypedArray()
        val yaws       = validFrames.map { it.yawDeg }.toFloatArray()
        val pitches    = validFrames.map { it.pitchDeg }.toFloatArray()
        val rolls      = validFrames.map { it.rollDeg }.toFloatArray()

        Log.i(TAG, "Calling nativeStitchFrames with ${framePaths.size} frames → ${outputFile.absolutePath}")
        setProgressAsync(workDataOf(KEY_STAGE to "Running stitching pipeline…", KEY_PROGRESS to 0.15f))

        // Wrap in try/catch(Throwable) — JNI errors are Error subclasses (not Exception),
        // e.g. UnsatisfiedLinkError if the .so failed to load, or NPE if JNI returns null.
        val stitchResult = try {
            val nativeStitcher = NativeStitcher()
            nativeStitcher.stitch(
                framePaths     = framePaths,
                yaws           = yaws,
                pitches        = pitches,
                rolls          = rolls,
                outputPath     = outputFile.absolutePath,
                nadirCapOption = nadirOption
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not loaded: ${e.message}")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Stitching engine not available on this device (${e.message})")
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Native stitching threw: ${t::class.java.simpleName}: ${t.message}", t)
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Stitching crashed: ${t::class.java.simpleName} – ${t.message}")
            )
        }

        // Guard against JNI returning null instead of a StitchResult object
        if (stitchResult == null) {
            Log.e(TAG, "nativeStitchFrames returned null — JNI FindClass may have failed")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return@withContext Result.failure(
                workDataOf("error" to "Stitching engine internal error (null result). Check Logcat for NativeStitcherCPP tag.")
            )
        }

        Log.i(TAG, "nativeStitchFrames returned: success=${stitchResult.isSuccess} path=${stitchResult.outputPath} err=${stitchResult.errorMessage}")
        setProgressAsync(workDataOf(KEY_STAGE to "Finalising…", KEY_PROGRESS to 0.95f))

        return@withContext if (stitchResult.isSuccess && stitchResult.outputPath != null) {
            sessionRepository.updateSessionStatus(
                sessionId    = sessionId,
                status       = CaptureSessionEntity.STATUS_DONE,
                outputPath   = stitchResult.outputPath,
                qualityScore = stitchResult.qualityScore
            )
            setProgressAsync(workDataOf(KEY_STAGE to "Done", KEY_PROGRESS to 1.0f))
            Log.i(TAG, "Stitching succeeded → ${stitchResult.outputPath}")
            Result.success()
        } else {
            val errMsg = stitchResult.errorMessage ?: "Native stitcher returned failure"
            Log.e(TAG, "Native stitcher reported failure: $errMsg")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            Result.failure(workDataOf("error" to errMsg))
        }
    }
}
