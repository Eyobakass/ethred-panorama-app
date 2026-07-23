package com.ethred.panorama.ui.tour

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import java.io.File

class AndroidHotspotBridge(
    private val onLongPress: (pitch: Float, yaw: Float) -> Unit
) {
    @JavascriptInterface
    fun onPanoramaLongPress(pitch: Double, yaw: Double) {
        onLongPress(pitch.toFloat(), yaw.toFloat())
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<CaptureSessionEntity>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<CaptureSessionEntity?>(null) }
    var currentHotspots by remember { mutableStateOf<List<HotspotEntity>>(emptyList()) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var clickedPitch by remember { mutableFloatStateOf(0f) }
    var clickedYaw by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(propertyId) {
        sessionRepository.getSessionsForProperty(propertyId).collect { list ->
            sessions = list
            if (selectedSession == null && list.isNotEmpty()) {
                selectedSession = list.first()
            }
        }
    }

    LaunchedEffect(selectedSession) {
        selectedSession?.let { session ->
            currentHotspots = sessionRepository.getHotspots(session.id)
        }
    }

    val currentSession = selectedSession

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interactive Tour Builder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Room Selection Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { s ->
                    FilterChip(
                        selected = currentSession?.id == s.id,
                        onClick = { selectedSession = s },
                        label = { Text(s.roomName) }
                    )
                }
            }

            // WebView 360 viewer with hotspot placement
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (currentSession?.outputPath != null) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = true

                                addJavascriptInterface(
                                    AndroidHotspotBridge { pitch, yaw ->
                                        // FR-TOUR-02: Maximum 5 hotspots per room limit check
                                        if (currentHotspots.size >= 5) {
                                            Toast.makeText(context, "Maximum 5 hotspots per room allowed.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            clickedPitch = pitch
                                            clickedYaw = yaw
                                            showBottomSheet = true
                                        }
                                    },
                                    "AndroidBridge"
                                )

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val fileUrl = "file://${currentSession.outputPath}"
                                        view?.evaluateJavascript("loadPanorama('$fileUrl');", null)
                                    }
                                }
                                loadUrl("file:///android_asset/pannellum/index.html")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Bar: Hotspot count + Publish Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Hotspots: ${currentHotspots.size}/5 (Long-press scene)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val manifestResult = generateTourManifestUseCase.execute(
                                    propertyId = propertyId,
                                    sessions = sessions,
                                    outputDirectory = File(context.filesDir, "manifests")
                                )

                                manifestResult.fold(
                                    onSuccess = { manifestFile ->
                                        uploadQueueRepository.enqueueUpload(
                                            sessionId = sessions.first().id,
                                            propertyId = propertyId,
                                            localFilePath = manifestFile.absolutePath,
                                            mediaCategory = "DOCUMENT",
                                            sortOrder = 0
                                        )
                                        onPublishSuccess()
                                    },
                                    onFailure = {}
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish Tour", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showBottomSheet && currentSession != null) {
        AlertDialog(
            onDismissRequest = { showBottomSheet = false },
            title = { Text("Add Hotspot Link") },
            text = {
                Column {
                    Text("Select target room this doorway links to:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    sessions.filter { it.id != currentSession.id }.forEach { targetSession ->
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    sessionRepository.addHotspot(
                                        fromSessionId = currentSession.id,
                                        toSessionId = targetSession.id,
                                        pitch = clickedPitch,
                                        yaw = clickedYaw,
                                        label = "Go to ${targetSession.roomName}"
                                    )
                                    currentHotspots = sessionRepository.getHotspots(currentSession.id)
                                    showBottomSheet = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddLocation, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(targetSession.roomName, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBottomSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
