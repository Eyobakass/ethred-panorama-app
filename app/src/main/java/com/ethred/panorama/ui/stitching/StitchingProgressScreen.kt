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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.worker.StitchWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.UUID

private const val SCREEN_WAKELOCK_MS = 25L * 60 * 1000   // 25 min

private sealed class StitchState {
    object Idle    : StitchState()
    object Running : StitchState()
    object Done    : StitchState()
    data class Failed(val reason: String) : StitchState()
}

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

    var stitchState by remember { mutableStateOf<StitchState>(StitchState.Idle) }
    var stageLabel  by remember { mutableStateOf("Preparing…") }
    var rawProgress by remember { mutableFloatStateOf(0.02f) }

    val animatedProgress by animateFloatAsState(
        targetValue   = rawProgress,
        animationSpec = tween(700),
        label         = "stitch_progress"
    )

    val isFailed = stitchState is StitchState.Failed
    val isDone   = stitchState is StitchState.Done

    // ── Wake lock — keep screen on during stitching ───────────────────────────
    DisposableEffect(Unit) {
        val wl = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "ethred:stitching"
            ).also { it.acquire(SCREEN_WAKELOCK_MS) }
        onDispose { if (wl.isHeld) wl.release() }
    }

    // ── Launch + observe work ─────────────────────────────────────────────────
    LaunchedEffect(sessionId, nadirOption) {

        // REPLACE: cancel any stale/previous run for this session before re-enqueueing.
        // This prevents the flow from seeing an old FAILED state immediately.
        workManager.cancelUniqueWork("stitch_$sessionId")

        val request = OneTimeWorkRequestBuilder<StitchWorker>()
            .setInputData(
                workDataOf(
                    StitchWorker.KEY_SESSION_ID   to sessionId,
                    StitchWorker.KEY_NADIR_OPTION to nadirOption
                )
            )
            .build()

        val workId: UUID = request.id

        workManager.enqueueUniqueWork(
            "stitch_$sessionId",
            ExistingWorkPolicy.REPLACE,
            request
        )

        stitchState = StitchState.Running

        // Track EXACTLY this work request by ID — not by name — so we never read
        // a stale FAILED state from a previous run.
        workManager.getWorkInfoByIdFlow(workId)
            .filter { it != null }   // skip null emissions before work is registered
            .first { info ->
                // Update progress display from worker's setProgressAsync() data
                val stage    = info.progress.getString(StitchWorker.KEY_STAGE)
                val progress = info.progress.getFloat(StitchWorker.KEY_PROGRESS, -1f)
                if (!stage.isNullOrBlank()) stageLabel  = stage
                if (progress >= 0f)          rawProgress = progress

                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        rawProgress = 1f
                        stageLabel  = "Complete ✓"
                        stitchState = StitchState.Done
                        true  // stop collecting
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        val reason = info.outputData.getString("error")
                            ?: if (info.state == WorkInfo.State.CANCELLED)
                                "Stitching was cancelled."
                            else
                                "Stitching failed. Open Android Studio Logcat and filter by tag 'StitchWorker' to see the exact error."
                        stitchState = StitchState.Failed(reason)
                        true  // stop collecting
                    }
                    else -> false   // ENQUEUED / RUNNING / BLOCKED — keep waiting
                }
            }

        // ── Double-check DB after WorkManager reports success ─────────────────
        if (stitchState is StitchState.Done) {
            val session = sessionRepository.getSession(sessionId)
            if (session?.status == CaptureSessionEntity.STATUS_DONE) {
                delay(500)
                onStitchingComplete()
            } else {
                stitchState = StitchState.Failed(
                    "Panorama file not saved (DB status=${session?.status}). Please retake."
                )
            }
        }
    }

    // ── Auto-navigate back after failure ──────────────────────────────────────
    LaunchedEffect(isFailed) {
        if (isFailed) {
            delay(5000)
            onStitchingFailed()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status icon
        Surface(
            modifier = Modifier.size(100.dp),
            shape    = RoundedCornerShape(24.dp),
            color    = when {
                isFailed -> MaterialTheme.colorScheme.errorContainer
                isDone   -> MaterialTheme.colorScheme.primaryContainer
                else     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    isFailed -> Text("✕", fontSize = 40.sp, color = MaterialTheme.colorScheme.error)
                    isDone   -> Text("✓", fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
                    else -> CircularProgressIndicator(
                        modifier    = Modifier.size(56.dp),
                        color       = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Title
        Text(
            text  = when {
                isFailed -> "Stitching Failed"
                isDone   -> "Panorama Ready!"
                else     -> "Stitching 360° Panorama"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        // Detail message
        Text(
            text      = if (isFailed)
                (stitchState as StitchState.Failed).reason
            else
                stageLabel,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (!isFailed && !isDone) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "This may take 5–15 minutes depending on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                progress     = { animatedProgress },
                modifier     = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color        = MaterialTheme.colorScheme.primary,
                trackColor   = MaterialTheme.colorScheme.surface
            )
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { workManager.cancelUniqueWork("stitch_$sessionId") },
                shape   = MaterialTheme.shapes.medium
            ) {
                Text("Cancel")
            }
        }

        if (isFailed) {
            Spacer(Modifier.height(20.dp))
            Text(
                text  = "Returning to capture screen in 5 s…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}
