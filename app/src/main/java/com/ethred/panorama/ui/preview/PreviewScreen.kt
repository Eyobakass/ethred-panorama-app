package com.ethred.panorama.ui.preview

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    sessionId: String,
    sessionRepository: CaptureSessionRepository,
    uploadQueueRepository: UploadQueueRepository,
    onRetake: (nadirOption: Int) -> Unit,
    onAddAnotherRoom: () -> Unit,
    onLinkRooms: (propertyId: String) -> Unit,
    onUploadNow: (propertyId: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var session             by remember { mutableStateOf<CaptureSessionEntity?>(null) }
    var isLoading           by remember { mutableStateOf(true) }
    var selectedNadirOption by remember { mutableIntStateOf(0) }
    var showRetakeDialog    by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        session   = sessionRepository.getSession(sessionId)
        isLoading = false
    }

    // ── Loading state ─────────────────────────────────────────────────────────
    if (isLoading) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentSession = session

    // ── No session / failed ───────────────────────────────────────────────────
    if (currentSession == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load panorama.", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAddAnotherRoom) { Text("Return to Dashboard") }
            }
        }
        return
    }

    // ── Retake confirmation ───────────────────────────────────────────────────
    if (showRetakeDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeDialog = false },
            title   = { Text("Retake Panorama?") },
            text    = { Text("The current panorama will be discarded and you'll be returned to stitching.") },
            confirmButton = {
                TextButton(onClick = { showRetakeDialog = false; onRetake(selectedNadirOption) }) {
                    Text("Retake", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetakeDialog = false }) { Text("Keep this one") }
            }
        )
    }

    // ── Stitching failed state ────────────────────────────────────────────────
    if (currentSession.outputPath == null) {
        Scaffold { pv ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(pv).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Stitching did not produce output",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Feature matching failed. Try retaking with slower rotation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { onRetake(0) }) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retake")
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSession.roomName, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // Quality star rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        val score = currentSession.qualityScore.coerceIn(0, 5)
                        repeat(5) { index ->
                            Icon(
                                Icons.Default.Star, contentDescription = null,
                                tint = if (index < score) Color(0xFFFFB800) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
            // ── Pannellum 360 WebView ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val safeFileUrl = remember(currentSession.outputPath) {
                    // Safely quote the path for JS injection to prevent injection bugs
                    try { JSONObject.quote("file://${currentSession.outputPath}") }
                    catch (_: Exception) { "\"file://${currentSession.outputPath}\"" }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess   = true
                            settings.allowContentAccess = true
                        }
                    },
                    update = { webView ->
                        // update{} runs every recomposition when session changes
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript("loadPanorama($safeFileUrl);", null)
                            }
                        }
                        if (webView.url == null) {
                            webView.loadUrl("file:///android_asset/pannellum/index.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Low Quality Warning Banner
                if (currentSession.qualityScore in 1..2) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(containerColor = WarningAmber)
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Low quality — retake recommended for better results.",
                                color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── Bottom action panel ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Floor (Nadir) Treatment",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface)

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        listOf("Auto Inpaint", "Vignette", "Agency Logo").forEachIndexed { idx, opt ->
                            FilterChip(
                                selected = selectedNadirOption == idx,
                                onClick  = { selectedNadirOption = idx },
                                label    = { Text(opt, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action buttons row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showRetakeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape    = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retake", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick  = onAddAnotherRoom,
                            modifier = Modifier.weight(1f),
                            shape    = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Room", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick  = { onLinkRooms(currentSession.propertyId) },
                            modifier = Modifier.weight(1f),
                            shape    = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Link, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tour", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                uploadQueueRepository.enqueuePanoramaWithThumbnail(
                                    sessionId        = currentSession.id,
                                    propertyId       = currentSession.propertyId,
                                    panoramaFilePath = currentSession.outputPath,
                                    sortOrder        = System.currentTimeMillis().toInt()
                                )
                                onUploadNow(currentSession.propertyId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upload to Ethred", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
