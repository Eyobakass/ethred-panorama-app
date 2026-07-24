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
                Log.i(TAG, "libstitcher.so loaded — OpenCV stitching available")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "libstitcher.so not loaded (${e.message}) — Kotlin fallback will be used")
                false
            }
        }
    }

    /**
     * Stitches frames to an equirectangular panorama.
     *
     * If libstitcher.so is available (built with OpenCV), the full native
     * OpenCV pipeline runs. Otherwise, the Kotlin fallback blender is used —
     * it produces a real 4096×2048 horizontal strip rather than copying frame[0].
     */
    fun stitch(
        framePaths: Array<String>,
        yaws: FloatArray,
        pitches: FloatArray,
        rolls: FloatArray,
        outputPath: String,
        nadirCapOption: Int
    ): StitchResult {
        return if (isLibraryLoaded) {
            Log.i(TAG, "Using native OpenCV stitcher for ${framePaths.size} frames")
            nativeStitchFrames(framePaths, yaws, pitches, rolls, outputPath, nadirCapOption)
        } else {
            Log.i(TAG, "Using Kotlin fallback stitcher for ${framePaths.size} frames")
            KotlinFallbackStitcher.stitch(framePaths, outputPath)
        }
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
