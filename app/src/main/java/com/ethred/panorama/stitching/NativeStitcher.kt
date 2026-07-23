package com.ethred.panorama.stitching

import android.util.Log

data class StitchResult(
    val isSuccess: Boolean,
    val outputPath: String?,
    val qualityScore: Int, // 1 to 5 stars
    val errorMessage: String? = null
)

class NativeStitcher {

    companion object {
        private const val TAG = "NativeStitcher"

        init {
            try {
                System.loadLibrary("stitcher")
                Log.i(TAG, "Native library libstitcher.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library libstitcher.so: ${e.message}")
            }
        }
    }

    /**
     * Executes native C++ OpenCV stitching pipeline on captured frame paths.
     * @param framePaths Array of absolute file paths to raw JPEGs
     * @param yaws Array of yaw angles for each frame
     * @param pitches Array of pitch angles for each frame
     * @param rolls Array of roll angles for each frame
     * @param outputPath Path where finished equirectangular JPEG will be saved
     * @param nadirCapOption 0: Auto Inpaint, 1: Vignette Feather, 2: Agency Logo Cap
     */
    external fun nativeStitchFrames(
        framePaths: Array<String>,
        yaws: FloatArray,
        pitches: FloatArray,
        rolls: FloatArray,
        outputPath: String,
        nadirCapOption: Int
    ): StitchResult
}
