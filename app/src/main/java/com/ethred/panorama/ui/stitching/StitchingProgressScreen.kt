package com.ethred.panorama.ui.stitching

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.worker.StitchWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val TIMEOUT_MS = 21L * 60 * 1000   // 21 min (1 min more than native timeout)

@Composable
fun StitchingProgressScreen(
    sessionId: String,
    nadirOption: Int = 0,
    workManager: WorkManager,
    sessionRepository: CaptureSessionRepository,
    onStitchingComplete: () -> Unit,
    onStitchingFailed: () -> Unit
) {
    val context = LocalContext.current

    var stageLabel     by remember { mutableStateOf("Preparing…") }
    var rawProgress    by remember { mutableFloatStateOf(0.05f) }
    var isFailed       by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf("") }
    var workId         by remember { mutableStateOf<java.util.UUID?>(null) }

    // Smooth animated progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(600),
        label = "stitch_progress"
    )

    // Keep screen on while stitching
    DisposableEffect(Unit) {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "ethred:stitching")
            .also { it.acquire(TIMEOUT_MS) }
        onDispose { if (wakeLock.isHeld) wakeLock.release() }
    }

    LaunchedEffect(sessionId, nadirOption) {
        // Enqueue (KEEP prevents duplicate workers)
        val request = OneTimeWorkRequestBuilder<StitchWorker>()
            .setInputData(
                workDataOf(
                    StitchWorker.KEY_SESSION_ID   to sessionId,
                    StitchWorker.KEY_NADIR_OPTION to nadirOption
                )
            )
            .build()
            .also { workId = it.id }

        workManager.enqueueUniqueWork(
            "stitch_$sessionId",
            ExistingWorkPolicy.KEEP,
            request
        )

        // Watch real WorkManager progress
        val timed = withTimeoutOrNull(TIMEOUT_MS) {
            workManager.getWorkInfoByIdFlow(workId!!)
                .collect { info ->
                    if (info == null) return@collect

                    // Update from real setProgressAsync() data
                    val stage    = info.progress.getString(StitchWorker.KEY_STAGE)
                    val progress = info.progress.getFloat(StitchWorker.KEY_PROGRESS, -1f)
                    if (stage != null)    stageLabel  = stage
                    if (progress >= 0f)   rawProgress = progress

                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            rawProgress = 1f
                            stageLabel  = "Complete ✓"
                            delay(600)
                            return@collect   // exits collect
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            isFailed       = true
                            failureMessage = info.outputData.getString("error")
                                ?: "Stitching failed. Please retake."
                            return@collect
                        }
                        WorkInfo.State.RUNNING -> { /* progress already updated above */ }
                        else -> { /* ENQUEUED / BLOCKED */ }
                    }
                }
        }

        if (timed == null) {
            // Timeout exceeded — worker is probably stuck
            isFailed       = true
            failureMessage = "Stitching took too long. Try with fewer frames or a faster device."
        }

        if (isFailed) return@LaunchedEffect

        // Verify DB status
        val session = sessionRepository.getSession(sessionId)
        if (session?.status == CaptureSessionEntity.STATUS_DONE) {
            onStitchingComplete()
        } else {
            isFailed       = true
            failureMessage = "Stitching did not produce an output. Please retake."
        }
    }

    // Navigate away when failed state is set
    LaunchedEffect(isFailed) {
        if (isFailed) {
            delay(3000)
            onStitchingFailed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape    = RoundedCornerShape(24.dp),
            color    = if (isFailed)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isFailed) {
                    Text("✕", fontSize = 40.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(56.dp),
                        color       = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text  = if (isFailed) "Stitching Failed" else "Stitching 360° Panorama",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text      = if (isFailed) failureMessage else stageLabel,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (!isFailed) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "This may take 5–15 minutes depending on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        if (!isFailed) {
            LinearProgressIndicator(
                progress     = { animatedProgress },
                modifier     = Modifier.fillMaxWidth().height(8.dp),
                color        = MaterialTheme.colorScheme.primary,
                trackColor   = MaterialTheme.colorScheme.surface
            )

            Spacer(Modifier.height(28.dp))

            // Cancel button
            OutlinedButton(
                onClick  = {
                    workId?.let { workManager.cancelWorkById(it) }
                },
                shape    = MaterialTheme.shapes.medium
            ) {
                Text("Cancel")
            }
        }
    }
}
