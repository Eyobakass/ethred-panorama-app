package com.ethred.panorama.ui.preview

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "PanoramaCache"

/**
 * Copies the panorama JPEG from internal files storage to the app's
 * external cache directory so Android WebView can load it via file://.
 *
 * WebViews loaded from file:///android_asset/ cannot cross into
 * file:///data/data/<package>/files/ (different origin, blocked by
 * Android security). The externalCacheDir is accessible via file://
 * without requiring storage permissions and without WebView origin issues.
 *
 * @param context     Application context
 * @param sourcePath  Absolute path in filesDir (e.g. /data/data/.../files/panoramas/...)
 * @return            A file:// URL string accessible from the WebView,
 *                    or null if the copy failed.
 */
fun getPanoramaCacheUrl(context: Context, sourcePath: String): String? {
    return try {
        val src = File(sourcePath)
        if (!src.exists()) {
            Log.e(TAG, "Source panorama not found: $sourcePath")
            return null
        }

        // Use externalCacheDir which is accessible to WebView file:// URLs
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val dst = File(cacheDir, "panorama_preview.jpg")

        // Only copy if source is newer than cached copy (saves time on re-opens)
        if (!dst.exists() || src.lastModified() > dst.lastModified() || dst.length() != src.length()) {
            src.copyTo(dst, overwrite = true)
            Log.i(TAG, "Panorama copied to cache: ${dst.absolutePath} (${dst.length()} bytes)")
        } else {
            Log.i(TAG, "Using existing cache: ${dst.absolutePath}")
        }

        "file://${dst.absolutePath}"
    } catch (e: Exception) {
        Log.e(TAG, "Failed to cache panorama: ${e.message}", e)
        null
    }
}
