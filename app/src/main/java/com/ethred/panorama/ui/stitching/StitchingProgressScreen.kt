package com.ethred.panorama.ui.stitching

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.worker.StitchWorker
import kotlinx.coroutines.delay

@Composable
fun StitchingProgressScreen(
    sessionId: String,
    nadirOption: Int = 0,
    workManager: WorkManager,
    sessionRepository: CaptureSessionRepository,
    onStitchingComplete: () -> Unit
) {
    var stageLabel by remember { mutableStateOf("Decoding frames...") }
    var progress by remember { mutableFloatStateOf(0.1f) }

    LaunchedEffect(sessionId, nadirOption) {
        val stitchRequest = OneTimeWorkRequestBuilder<StitchWorker>()
            .setInputData(
                workDataOf(
                    StitchWorker.KEY_SESSION_ID to sessionId,
                    StitchWorker.KEY_NADIR_OPTION to nadirOption
                )
            )
            .build()

        workManager.enqueue(stitchRequest)

        // Simulated stages matching SRS section 2.3 pipeline steps
        val stages = listOf(
            "Detecting AKAZE feature keypoints..." to 0.25f,
            "Matching keypoints & RANSAC homography..." to 0.50f,
            "Spherical warping & seam carving..." to 0.75f,
            "Multi-band blending & zenith/nadir inpainting..." to 0.90f,
            "Writing GPano XMP metadata..." to 1.00f
        )

        for (stage in stages) {
            stageLabel = stage.first
            progress = stage.second
            delay(1200)
        }

        // Wait until DB status is marked DONE or FAILED
        var session = sessionRepository.getSession(sessionId)
        while (session?.status == CaptureSessionEntity.STATUS_STITCHING || session?.status == CaptureSessionEntity.STATUS_CAPTURING) {
            delay(500)
            session = sessionRepository.getSession(sessionId)
        }

        onStitchingComplete()
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
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Stitching 360° Panorama",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stageLabel,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}
