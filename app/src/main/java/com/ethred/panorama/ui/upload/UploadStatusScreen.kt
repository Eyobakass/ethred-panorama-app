package com.ethred.panorama.ui.upload

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ethred.panorama.data.local.db.UploadQueueEntity
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.worker.UploadWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(
    propertyId: String,
    workManager: WorkManager,
    uploadQueueRepository: UploadQueueRepository,
    onDashboardReturn: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var queueItems by remember { mutableStateOf<List<UploadQueueEntity>>(emptyList()) }

    LaunchedEffect(propertyId) {
        // FR-SYNC-01: Exponential backoff starting at 30s, max 5 retries
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(false)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag("ethred_upload_$propertyId")
            .build()

        // enqueueUniqueWork prevents duplicate workers for the same property
        workManager.enqueueUniqueWork(
            "upload_$propertyId",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )

        uploadQueueRepository.getQueueForProperty(propertyId).collect { list ->
            queueItems = list
        }
    }

    val isAllDone = queueItems.isNotEmpty() && queueItems.all { it.status == UploadQueueEntity.STATUS_DONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Status", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isAllDone) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "360° Tour Published Successfully!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Your virtual tour is now live on the Ethred web portal.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Text(
                        text = "Syncing Files to Ethred Server",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(queueItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.mediaCategory == "DOCUMENT") "Tour Manifest JSON" else "360° Panorama Image",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Status: ${item.status}",
                                        fontSize = 12.sp,
                                        color = if (item.status == UploadQueueEntity.STATUS_DONE) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (item.status == UploadQueueEntity.STATUS_IN_PROGRESS) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else if (item.status == UploadQueueEntity.STATUS_FAILED) {
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            uploadQueueRepository.processItem(item)
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.error)
                                    }
                                } else if (item.status == UploadQueueEntity.STATUS_DONE) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isAllDone) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ethred.com/properties/$propertyId"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View on Website", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedButton(
                    onClick = onDashboardReturn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to Dashboard", fontSize = 16.sp)
                }
            }
        }
    }
}
