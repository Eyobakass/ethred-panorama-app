package com.ethred.panorama.stitching

import android.util.Log

data class StitchResult(
    val isSuccess: Boolean,
    val outputPath: String?,
    val qualityScore: Int, // 1–5 stars
    val errorMessage: String? = null
)

class NativeStitcher {

    companion object {
        private const val TAG = "NativeStitcher"

        var isLibraryLoaded: Boolean = false
            private set

        init {
            isLibraryLoaded = try {
                System.loadLibrary("stitcher")
                Log.i(TAG, "libstitcher.so loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "libstitcher.so failed to load (${e.message}) — Kotlin fallback will be used")
                false
            }
        }
    }

    /**
     * Stitches frames into an equirectangular panorama.
     *
     * Priority chain:
     *  1. Native OpenCV stitcher  (if libstitcher.so is loaded AND OpenCV was compiled in)
     *  2. KotlinFallbackStitcher  (always available — blends all frames side-by-side)
     *
     * The C++ #else block now returns isSuccess=false with "OpenCV not available"
     * so this method can detect it and route to the Kotlin fallback.
     */
    fun stitch(
        framePaths: Array<String>,
        yaws: FloatArray,
        pitches: FloatArray,
        rolls: FloatArray,
        outputPath: String,
        nadirCapOption: Int
    ): StitchResult {
        // ── Step 1: Skip native entirely if .so never loaded ─────────────────
        if (!isLibraryLoaded) {
            Log.i(TAG, "Library not loaded — routing directly to KotlinFallbackStitcher")
            return KotlinFallbackStitcher.stitch(framePaths, outputPath)
        }

        // ── Step 2: Try native ───────────────────────────────────────────────
        Log.i(TAG, "Calling native stitcher for ${framePaths.size} frames")
        val nativeResult = nativeStitchFrames(
            framePaths, yaws, pitches, rolls, outputPath, nadirCapOption
        )

        // ── Step 3: Fall back to Kotlin if OpenCV is not compiled in ─────────
        // The C++ #else branch returns isSuccess=false with this specific message.
        if (!nativeResult.isSuccess &&
            nativeResult.errorMessage?.contains("OpenCV not available") == true
        ) {
            Log.i(TAG, "Native reported no OpenCV — routing to KotlinFallbackStitcher")
            return KotlinFallbackStitcher.stitch(framePaths, outputPath)
        }

        // ── Step 4: Return native result (success or genuine failure) ────────
        Log.i(TAG, "Native result: success=${nativeResult.isSuccess} err=${nativeResult.errorMessage}")
        return nativeResult
    }

    private external fun nativeStitchFrames(
        framePaths: Array<String>,
        yaws: FloatArray,
        pitches: FloatArray,
        rolls: FloatArray,
        outputPath: String,
        nadirCapOption: Int
    ): StitchResult
}
