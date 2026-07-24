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

        /**
         * True when libstitcher.so loaded successfully.
         * If false, calling nativeStitchFrames() would throw UnsatisfiedLinkError.
         */
        var isLibraryLoaded: Boolean = false
            private set

        init {
            isLibraryLoaded = try {
                System.loadLibrary("stitcher")
                Log.i(TAG, "Native library libstitcher.so loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libstitcher.so: ${e.message}")
                false
            }
        }
    }

    /**
     * Executes native C++ stitching pipeline.
     * Returns a failure StitchResult immediately if the native library isn't loaded.
     *
     * @param framePaths    Array of absolute file paths to raw JPEGs
     * @param yaws          Array of yaw angles for each frame
     * @param pitches       Array of pitch angles for each frame
     * @param rolls         Array of roll angles for each frame
     * @param outputPath    Path where the equirectangular JPEG will be saved
     * @param nadirCapOption 0: Auto Inpaint, 1: Vignette Feather, 2: Agency Logo Cap
     */
    fun stitch(
        framePaths: Array<String>,
        yaws: FloatArray,
        pitches: FloatArray,
        rolls: FloatArray,
        outputPath: String,
        nadirCapOption: Int
    ): StitchResult {
        if (!isLibraryLoaded) {
            return StitchResult(
                isSuccess    = false,
                outputPath   = null,
                qualityScore = 0,
                errorMessage = "Native stitching library failed to load. The APK may not include libstitcher.so for this CPU architecture."
            )
        }
        return nativeStitchFrames(framePaths, yaws, pitches, rolls, outputPath, nadirCapOption)
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
