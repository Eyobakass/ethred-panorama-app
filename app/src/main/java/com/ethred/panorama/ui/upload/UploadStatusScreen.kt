package com.ethred.panorama.ui.upload

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.ethred.panorama.data.local.db.UploadQueueEntity
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.ui.theme.SuccessGreen
import com.ethred.panorama.worker.UploadWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// Ethred property portal base URL — swap via BuildConfig in production
private const val ETHRED_PORTAL_BASE = "https://ethred.com/properties"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(
    propertyId: String,
    workManager: WorkManager,
    uploadQueueRepository: UploadQueueRepository,
    onDashboardReturn: () -> Unit
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var queueItems by remember { mutableStateOf<List<UploadQueueEntity>>(emptyList()) }
    var isLoadingQueue by remember { mutableStateOf(true) }

    // Enqueue only once in a side effect (not on every recomposition)
    LaunchedEffect(propertyId) {
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(false)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag("ethred_upload_$propertyId")
            .build()

        workManager.enqueueUniqueWork(
            "upload_$propertyId",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )

        uploadQueueRepository.getQueueForProperty(propertyId).collect { list ->
            queueItems     = list
            isLoadingQueue = false
        }
    }

    val isAllDone = queueItems.isNotEmpty() && queueItems.all {
        it.status == UploadQueueEntity.STATUS_DONE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Status", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onDashboardReturn) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Dashboard")
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ── Status Header ─────────────────────────────────────────────────
            when {
                isLoadingQueue -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Preparing upload…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                    }
                }
                isAllDone -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint     = SuccessGreen,
                            modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("360° Tour Published!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("Your virtual tour is now live on the Ethred portal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Syncing to Ethred Server…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Keep the app open and stay connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Queue Items ───────────────────────────────────────────────────
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(queueItems) { item ->
                    val (statusLabel, statusColor) = when (item.status) {
                        UploadQueueEntity.STATUS_PENDING     -> "Waiting…"     to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        UploadQueueEntity.STATUS_IN_PROGRESS -> "Uploading…"   to MaterialTheme.colorScheme.primary
                        UploadQueueEntity.STATUS_DONE        -> "Uploaded ✓"   to SuccessGreen
                        UploadQueueEntity.STATUS_FAILED      -> "Failed — tap retry" to MaterialTheme.colorScheme.error
                        else                                 -> item.status    to MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.medium,
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text  = if (item.mediaCategory == "DOCUMENT") "Tour Manifest" else "360° Panorama Image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(statusLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor)
                            }

                            when (item.status) {
                                UploadQueueEntity.STATUS_IN_PROGRESS ->
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                UploadQueueEntity.STATUS_DONE ->
                                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                                UploadQueueEntity.STATUS_FAILED ->
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            // Re-enqueue via WorkManager for proper retry
                                            workManager.enqueueUniqueWork(
                                                "upload_retry_${item.id}",
                                                ExistingWorkPolicy.REPLACE,
                                                OneTimeWorkRequestBuilder<UploadWorker>()
                                                    .setConstraints(
                                                        Constraints.Builder()
                                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, "Retry",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                else -> {}
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Action Buttons ────────────────────────────────────────────────
            if (isAllDone) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("$ETHRED_PORTAL_BASE/$propertyId"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View on Ethred Portal", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick  = onDashboardReturn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium
            ) {
                Text("Return to Dashboard", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
