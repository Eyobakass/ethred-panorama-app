package com.ethred.panorama.ui.preview

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import kotlinx.coroutines.launch
import java.io.File

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var session by remember { mutableStateOf<CaptureSessionEntity?>(null) }
    var selectedNadirOption by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessionId) {
        session = sessionRepository.getSession(sessionId)
    }

    val currentSession = session ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSession.roomName, fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        repeat(5) { index ->
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index < currentSession.qualityScore) Color(0xFFFFB800) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
            // Interactive 360 Pannellum WebView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentSession.outputPath?.let { path ->
                                        val fileUrl = "file://$path"
                                        view?.evaluateJavascript("loadPanorama('$fileUrl');", null)
                                    }
                                }
                            }
                            loadUrl("file:///android_asset/pannellum/index.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Low Quality Warning Banner as per FR-VIEW-02 & UC-04
                if (currentSession.qualityScore in 1..2) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Low Quality Panorama — Feature matching was low. Retake recommended.",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Nadir Cap Selector & Action Buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Floor (Nadir) Cap Option", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val options = listOf("Auto Inpaint", "Vignette", "Agency Logo")
                        options.forEachIndexed { idx, opt ->
                            FilterChip(
                                selected = selectedNadirOption == idx,
                                onClick = { selectedNadirOption = idx },
                                label = { Text(opt, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onRetake(selectedNadirOption) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retake", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onAddAnotherRoom,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Room", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    currentSession.outputPath?.let { path ->
                                        uploadQueueRepository.enqueuePanoramaWithThumbnail(
                                            sessionId = currentSession.id,
                                            propertyId = currentSession.propertyId,
                                            panoramaFilePath = path,
                                            sortOrder = 1
                                        )
                                        onUploadNow(currentSession.propertyId)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
