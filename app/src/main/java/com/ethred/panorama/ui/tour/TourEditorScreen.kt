package com.ethred.panorama.ui.tour

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.AutoMirrored.Filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.local.db.HotspotEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.domain.usecase.GenerateTourManifestUseCase
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class AndroidHotspotBridge(
    private val onLongPress: (pitch: Float, yaw: Float) -> Unit
) {
    @JavascriptInterface
    fun onPanoramaLongPress(pitch: Double, yaw: Double) {
        // JS bridge calls on background thread — must post to main
        Handler(Looper.getMainLooper()).post {
            onLongPress(pitch.toFloat(), yaw.toFloat())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourEditorScreen(
    propertyId: String,
    sessionRepository: CaptureSessionRepository,
    uploadQueueRepository: UploadQueueRepository,
    generateTourManifestUseCase: GenerateTourManifestUseCase,
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarState  = remember { SnackbarHostState() }

    var sessions         by remember { mutableStateOf<List<CaptureSessionEntity>>(emptyList()) }
    var selectedSession  by remember { mutableStateOf<CaptureSessionEntity?>(null) }
    var currentHotspots  by remember { mutableStateOf<List<HotspotEntity>>(emptyList()) }
    var showHotspotDialog by remember { mutableStateOf(false) }
    var clickedPitch     by remember { mutableFloatStateOf(0f) }
    var clickedYaw       by remember { mutableFloatStateOf(0f) }
    var isPublishing     by remember { mutableStateOf(false) }

    LaunchedEffect(propertyId) {
        sessionRepository.getSessionsForProperty(propertyId).collect { list ->
            sessions = list
            if (selectedSession == null && list.isNotEmpty()) {
                selectedSession = list.first()
            }
        }
    }

    LaunchedEffect(selectedSession) {
        selectedSession?.let { s ->
            currentHotspots = sessionRepository.getHotspots(s.id)
        }
    }

    val currentSession = selectedSession

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text("Tour Builder", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // ── Room Selection Chips ────────────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { s ->
                    FilterChip(
                        selected = currentSession?.id == s.id,
                        onClick  = { selectedSession = s },
                        label    = { Text(s.roomName) }
                    )
                }
            }

            // ── 360 WebView ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    sessions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No completed rooms yet. Capture and stitch a room first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                    currentSession?.outputPath == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This room has no panorama yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                    else -> {
                        val safeFileUrl = remember(currentSession.outputPath) {
                            try { JSONObject.quote("file://${currentSession.outputPath}") }
                            catch (_: Exception) { "\"file://${currentSession.outputPath}\"" }
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled  = true
                                    settings.allowFileAccess    = true
                                    settings.allowContentAccess = true

                                    addJavascriptInterface(
                                        AndroidHotspotBridge { pitch, yaw ->
                                            if (currentHotspots.size >= 5) {
                                                coroutineScope.launch {
                                                    snackbarState.showSnackbar("Maximum 5 hotspots per room.")
                                                }
                                            } else {
                                                clickedPitch      = pitch
                                                clickedYaw        = yaw
                                                showHotspotDialog = true
                                            }
                                        },
                                        "AndroidBridge"
                                    )
                                }
                            },
                            update = { webView ->
                                // Reload panorama when selected session changes
                                webView.webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.evaluateJavascript("loadPanorama($safeFileUrl);", null)
                                    }
                                }
                                if (webView.url == null) {
                                    webView.loadUrl("file:///android_asset/pannellum/index.html")
                                } else {
                                    webView.evaluateJavascript("loadPanorama($safeFileUrl);", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // ── Bottom Bar: hotspot count + publish ─────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${currentHotspots.size}/5 hotspots  ·  Long-press scene to add",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isPublishing = true
                                val manifestResult = generateTourManifestUseCase.execute(
                                    propertyId       = propertyId,
                                    sessions         = sessions,
                                    outputDirectory  = File(context.filesDir, "manifests")
                                )
                                isPublishing = false
                                manifestResult.fold(
                                    onSuccess = { manifestFile ->
                                        // Use selectedSession.id (not sessions.first().id)
                                        val uploadSessionId = currentSession?.id
                                            ?: sessions.firstOrNull()?.id
                                            ?: return@fold
                                        uploadQueueRepository.enqueueUpload(
                                            sessionId      = uploadSessionId,
                                            propertyId     = propertyId,
                                            localFilePath  = manifestFile.absolutePath,
                                            mediaCategory  = "DOCUMENT",
                                            sortOrder      = 0
                                        )
                                        onPublishSuccess()
                                    },
                                    onFailure = { err ->
                                        snackbarState.showSnackbar(
                                            "Publish failed: ${err.message ?: "Unknown error"}"
                                        )
                                    }
                                )
                            }
                        },
                        enabled = !isPublishing && sessions.any { it.outputPath != null },
                        shape   = MaterialTheme.shapes.small
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Publish, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Publish Tour", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // ── Hotspot Link Dialog ────────────────────────────────────────────────────
    if (showHotspotDialog && currentSession != null) {
        val otherSessions = sessions.filter { it.id != currentSession.id && it.outputPath != null }
        AlertDialog(
            onDismissRequest = { showHotspotDialog = false },
            title   = { Text("Add Hotspot Link") },
            text    = {
                if (otherSessions.isEmpty()) {
                    Text("No other completed rooms to link to.")
                } else {
                    LazyColumn {
                        items(otherSessions) { targetSession ->
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        sessionRepository.addHotspot(
                                            fromSessionId = currentSession.id,
                                            toSessionId   = targetSession.id,
                                            pitch         = clickedPitch,
                                            yaw           = clickedYaw,
                                            label         = "→ ${targetSession.roomName}"
                                        )
                                        currentHotspots = sessionRepository.getHotspots(currentSession.id)
                                        showHotspotDialog = false
                                        // Notify WebView to draw the new hotspot
                                        snackbarState.showSnackbar("Hotspot added to ${targetSession.roomName}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddLocation, null)
                                Spacer(Modifier.width(8.dp))
                                Text(targetSession.roomName, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton  = {},
            dismissButton  = {
                TextButton(onClick = { showHotspotDialog = false }) { Text("Cancel") }
            }
        )
    }
}
