package com.ethred.panorama.ui.preview

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.File

private const val TAG = "PanoramaWebView"

/**
 * Virtual host used by WebViewAssetLoader.
 * Files served under https://appassets.androidplatform.net/panorama/
 * are always accessible from any WebView page, bypassing cross-origin
 * file:// restrictions that block filesDir paths on Android 7+.
 */
private const val ASSET_HOST = "appassets.androidplatform.net"
private const val PANORAMA_PATH_PREFIX = "/panorama/"

/**
 * Returns the https:// URL the WebView should use for this panorama file.
 * Example: https://appassets.androidplatform.net/panorama/equirectangular_360.jpg
 */
fun getPanoramaAssetUrl(fileName: String = "equirectangular_360.jpg"): String {
    return "https://$ASSET_HOST$PANORAMA_PATH_PREFIX$fileName"
}

/**
 * Builds a [WebViewAssetLoader] that maps
 *   https://appassets.androidplatform.net/panorama/<filename>
 * to
 *   <filesDir>/panoramas/<sessionId>/<filename>
 *
 * This is the official Android way to serve internal storage files to a WebView
 * without any cross-origin file:// issues.
 *
 * @param context   Application context
 * @param outputPath  Absolute path to the panorama JPEG in internal storage
 */
fun buildPanoramaAssetLoader(
    context: Context,
    outputPath: String
): WebViewAssetLoader {
    // The asset loader needs a directory, not a file
    val panoramaDir = File(outputPath).parentFile
        ?: context.filesDir

    Log.i(TAG, "AssetLoader serving from: ${panoramaDir.absolutePath}")

    return WebViewAssetLoader.Builder()
        .setDomain(ASSET_HOST)
        .setHttpAllowed(false) // https only
        .addPathHandler(
            PANORAMA_PATH_PREFIX,
            WebViewAssetLoader.InternalStoragePathHandler(context, panoramaDir)
        )
        .addPathHandler(
            "/assets/",
            WebViewAssetLoader.AssetsPathHandler(context)
        )
        .build()
}

/**
 * Convenience: creates a [WebViewClient] that intercepts requests through [assetLoader].
 * Provide an optional [onPageFinished] callback for post-load JS injection.
 */
fun makeAssetWebViewClient(
    assetLoader: WebViewAssetLoader,
    onPageFinished: ((WebView?) -> Unit)? = null
): WebViewClient = object : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        return assetLoader.shouldInterceptRequest(request.url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageFinished?.invoke(view)
    }
}
