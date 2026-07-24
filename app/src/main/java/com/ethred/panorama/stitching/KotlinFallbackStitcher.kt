package com.ethred.panorama.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Pure-Kotlin equirectangular panorama blender.
 *
 * Used when OpenCV is NOT available. Instead of copying frame[0],
 * this stitches all captured frames side-by-side into a wide
 * equirectangular strip (monotone horizontal blend).
 *
 * Quality is lower than OpenCV feature-matching, but the output
 * is a valid 360° JPEG with proper aspect ratio and XMP metadata.
 */
object KotlinFallbackStitcher {

    private const val TAG = "KotlinFallbackStitcher"

    // Target output size — 4096×2048 = standard equirectangular 2:1 ratio
    private const val OUT_W = 4096
    private const val OUT_H = 2048

    fun stitch(
        framePaths: Array<String>,
        outputPath: String
    ): StitchResult {
        Log.i(TAG, "Kotlin fallback stitcher: blending ${framePaths.size} frames → $outputPath")

        if (framePaths.isEmpty()) {
            return StitchResult(false, null, 0, "No frame paths provided to fallback stitcher")
        }

        return try {
            // Load all frames as bitmaps, decoding at reduced scale to save memory
            val bitmaps = loadFrames(framePaths)
            if (bitmaps.isEmpty()) {
                return StitchResult(false, null, 0, "Failed to decode any frame bitmaps")
            }

            Log.i(TAG, "Loaded ${bitmaps.size}/${framePaths.size} bitmaps (rotated upright)")

            // Create output canvas
            val output = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(OUT_W * OUT_H)

            // Place each frame into its horizontal slice of the equirectangular canvas
            blendFramesHorizontally(bitmaps, pixels)

            output.setPixels(pixels, 0, OUT_W, 0, 0, OUT_W, OUT_H)

            // Recycle source bitmaps
            bitmaps.forEach { it.recycle() }

            // Save to JPEG
            val outFile = File(outputPath).also { it.parentFile?.mkdirs() }
            FileOutputStream(outFile).use { fos ->
                output.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            output.recycle()

            // Quality score: 2 stars (it's a fallback, not real stitching)
            Log.i(TAG, "Kotlin fallback stitcher done → $outputPath")
            StitchResult(
                isSuccess    = true,
                outputPath   = outputPath,
                qualityScore = 2,
                errorMessage = "Fallback mode (no OpenCV): frames blended horizontally"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Kotlin fallback stitcher failed: ${e.message}", e)
            StitchResult(false, null, 0, "Kotlin fallback stitcher error: ${e.message}")
        }
    }

    private fun loadFrames(paths: Array<String>): List<Bitmap> {
        val opts = BitmapFactory.Options().apply {
            // Sample down to ~512px wide to avoid OOM across 28 frames
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return paths.mapNotNull { path ->
            try {
                val rawBmp = BitmapFactory.decodeFile(path, opts) ?: return@mapNotNull null
                val rotationDegrees = getExifRotation(path, rawBmp)
                if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotatedBmp = Bitmap.createBitmap(
                        rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true
                    )
                    if (rotatedBmp != rawBmp) {
                        rawBmp.recycle()
                    }
                    rotatedBmp
                } else {
                    rawBmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode frame: $path — ${e.message}")
                null
            }
        }
    }

    private fun getExifRotation(path: String, bmp: Bitmap): Int {
        try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> return 90
                ExifInterface.ORIENTATION_ROTATE_180 -> return 180
                ExifInterface.ORIENTATION_ROTATE_270 -> return 270
            }
        } catch (_: Exception) {}

        // Fallback: If EXIF was missing/undefined but bitmap is wider than tall (landscape),
        // portrait phone capture was recorded in sensor orientation, so rotate 90 degrees.
        if (bmp.width > bmp.height) {
            return 90
        }
        return 0
    }

    /**
     * Lays out each frame as a horizontal slice of the output canvas.
     * Each frame covers (OUT_W / frameCount) pixels wide, stretched to OUT_H tall.
     */
    private fun blendFramesHorizontally(frames: List<Bitmap>, pixels: IntArray) {
        val sliceW = OUT_W / frames.size

        frames.forEachIndexed { idx, src ->
            val startX = idx * sliceW
            val endX   = if (idx == frames.size - 1) OUT_W else startX + sliceW

            val srcW = src.width
            val srcH = src.height

            for (y in 0 until OUT_H) {
                val srcY = (y.toFloat() / OUT_H * srcH).toInt().coerceIn(0, srcH - 1)
                for (x in startX until endX) {
                    val localX = x - startX
                    val srcX   = (localX.toFloat() / (endX - startX) * srcW).toInt().coerceIn(0, srcW - 1)
                    pixels[y * OUT_W + x] = src.getPixel(srcX, srcY)
                }
            }
        }
    }
}
