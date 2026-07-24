package com.ethred.panorama.ui.library

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoramaLibraryScreen(
    sessionRepository: CaptureSessionRepository,
    onSelectSession: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context       = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sessions by sessionRepository.getAllCompletedSessions()
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("360° Library", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pv ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(pv)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No completed panoramas yet.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        "Capture and stitch a room to see it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    top = pv.calculateTopPadding() + 12.dp,
                    bottom = pv.calculateBottomPadding() + 12.dp,
                    start = 12.dp, end = 12.dp
                ),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(sessions) { session ->
                    PanoramaCard(
                        session = session,
                        onView = { onSelectSession(session.id) },
                        onDownload = {
                            coroutineScope.launch {
                                val path = session.outputPath
                                if (path != null) {
                                    val saved = savePanoramaToGallery(context, path, session.roomName)
                                    Toast.makeText(
                                        context,
                                        if (saved) "Saved to Pictures/Ethred360 ✔" else "Save failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PanoramaCard(
    session: CaptureSessionEntity,
    onView: () -> Unit,
    onDownload: () -> Unit
) {
    val dateStr = remember(session.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(session.createdAt))
    }
    val fileExists = remember(session.outputPath) {
        session.outputPath?.let { File(it).exists() } ?: false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clickable { onView() },
        shape  = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail — show the equirectangular JPG directly
            if (fileExists && session.outputPath != null) {
                AsyncImage(
                    model = File(session.outputPath),
                    contentDescription = "360° thumbnail of ${session.roomName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Bottom info section
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Text(
                    text  = session.roomName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val score = session.qualityScore.coerceIn(0, 5)
                        repeat(score) {
                            Icon(
                                Icons.Default.Star, contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDownload,
                    enabled = fileExists,
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save JPG", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Copies the 360° equirectangular JPG into the device's public gallery (Pictures/Ethred360). */
private fun savePanoramaToGallery(context: Context, inputFilePath: String, roomName: String): Boolean {
    return try {
        val file = File(inputFilePath)
        if (!file.exists()) return false
        val cleanRoom = roomName.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName  = "360_${cleanRoom}_${System.currentTimeMillis()}.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ethred360")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } else {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Ethred360"
            ).apply { mkdirs() }
            val target = File(targetDir, fileName)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
