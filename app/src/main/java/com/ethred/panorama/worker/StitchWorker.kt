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
        private const val TAG      = "StitchWorker"
    }

    // ── Public entry-point: top-level catch ensures WorkManager always gets a Result ──
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            runStitching()
        } catch (t: Throwable) {
            // This catches anything the inner function didn't handle
            // (e.g. Hilt injection errors, DB init failures, CancellationException from system)
            Log.e(TAG, "UNCAUGHT in StitchWorker: ${t::class.java.name}: ${t.message}", t)
            try {
                inputData.getString(KEY_SESSION_ID)?.let { sid ->
                    sessionRepository.updateSessionStatus(sid, CaptureSessionEntity.STATUS_FAILED)
                }
            } catch (_: Throwable) { /* best-effort */ }
            Result.failure(
                workDataOf("error" to "Worker crash: ${t::class.java.simpleName}: ${t.message}")
            )
        }
    }

    // ── All business logic separated for clean returns ────────────────────────
    private suspend fun runStitching(): Result {
        val sessionId   = inputData.getString(KEY_SESSION_ID)
            ?: return Result.failure(workDataOf("error" to "Missing session ID"))
        val nadirOption = inputData.getInt(KEY_NADIR_OPTION, 0)

        Log.i(TAG, "Starting stitch: session=$sessionId nadirOption=$nadirOption")

        // ── Validate session exists in DB ─────────────────────────────────────
        sessionRepository.getSession(sessionId)
            ?: return Result.failure(workDataOf("error" to "Session '$sessionId' not found in database"))

        // ── Load frames from DB ───────────────────────────────────────────────
        val frames = sessionRepository.getFrames(sessionId)
        Log.i(TAG, "DB returned ${frames.size} frames")

        if (frames.size < 10) {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return Result.failure(
                workDataOf("error" to "Only ${frames.size} frames captured — need at least 10. Retake slowly.")
            )
        }

        // ── Verify files exist on disk ────────────────────────────────────────
        val validFrames = frames.filter { File(it.filePath).exists() }
        Log.i(TAG, "${validFrames.size}/${frames.size} frame files exist on disk")

        if (validFrames.size < 10) {
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return Result.failure(
                workDataOf("error" to "Frame files missing from device storage (${validFrames.size}/${frames.size}). Retake session.")
            )
        }

        // ── Mark session as stitching ─────────────────────────────────────────
        sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_STITCHING)
        setProgressAsync(workDataOf(KEY_STAGE to "Decoding frames…", KEY_PROGRESS to 0.05f))

        // ── Prepare output path ───────────────────────────────────────────────
        val outputDir  = File(applicationContext.filesDir, "panoramas/$sessionId").apply { mkdirs() }
        val outputFile = File(outputDir, "equirectangular_360.jpg")

        val framePaths = validFrames.map { it.filePath }.toTypedArray()
        val yaws       = validFrames.map { it.yawDeg }.toFloatArray()
        val pitches    = validFrames.map { it.pitchDeg }.toFloatArray()
        val rolls      = validFrames.map { it.rollDeg }.toFloatArray()

        Log.i(TAG, "Calling native stitcher: ${framePaths.size} frames → ${outputFile.absolutePath}")
        setProgressAsync(workDataOf(KEY_STAGE to "Running stitching pipeline…", KEY_PROGRESS to 0.15f))

        // ── Native stitching call (catch any JNI Error/Exception) ─────────────
        val stitchResult = try {
            NativeStitcher().stitch(
                framePaths     = framePaths,
                yaws           = yaws,
                pitches        = pitches,
                rolls          = rolls,
                outputPath     = outputFile.absolutePath,
                nadirCapOption = nadirOption
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError: ${e.message}")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return Result.failure(
                workDataOf("error" to "Native library not loaded on this device: ${e.message}")
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Native stitcher threw ${t::class.java.simpleName}: ${t.message}", t)
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return Result.failure(
                workDataOf("error" to "Stitcher error: ${t::class.java.simpleName}: ${t.message}")
            )
        }

        // ── Guard against JNI returning null ──────────────────────────────────
        if (stitchResult == null) {
            Log.e(TAG, "nativeStitchFrames returned null — JNI issue, see NativeStitcherCPP logcat")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            return Result.failure(
                workDataOf("error" to "Stitching engine returned null (JNI error). See Logcat tag: NativeStitcherCPP")
            )
        }

        Log.i(TAG, "Native result: success=${stitchResult.isSuccess} path=${stitchResult.outputPath} err=${stitchResult.errorMessage}")
        setProgressAsync(workDataOf(KEY_STAGE to "Finalising…", KEY_PROGRESS to 0.95f))

        // ── Handle result ─────────────────────────────────────────────────────
        return if (stitchResult.isSuccess && stitchResult.outputPath != null) {
            sessionRepository.updateSessionStatus(
                sessionId    = sessionId,
                status       = CaptureSessionEntity.STATUS_DONE,
                outputPath   = stitchResult.outputPath,
                qualityScore = stitchResult.qualityScore
            )
            setProgressAsync(workDataOf(KEY_STAGE to "Done", KEY_PROGRESS to 1.0f))
            Log.i(TAG, "Stitching SUCCEEDED → ${stitchResult.outputPath}")
            Result.success()
        } else {
            val err = stitchResult.errorMessage ?: "Native stitcher returned failure with no message"
            Log.e(TAG, "Native stitcher FAILED: $err")
            sessionRepository.updateSessionStatus(sessionId, CaptureSessionEntity.STATUS_FAILED)
            Result.failure(workDataOf("error" to err))
        }
    }
}
