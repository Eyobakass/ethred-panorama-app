package com.ethred.panorama.ui.capture

import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ethred.panorama.sensors.DeviceOrientation
import com.ethred.panorama.sensors.TargetDot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CaptureScreen(
    sessionId: String,
    viewModel: CaptureViewModel,
    onFinishCapture: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val orientation by viewModel.orientationFlow.collectAsState()
    val dots by viewModel.dotsFlow.collectAsState()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isShutterFlashing by remember { mutableStateOf(false) }
    var gyroMissingDialog by remember { mutableStateOf(!viewModel.sensorProcessor.isGyroscopeAvailable()) }

    LaunchedEffect(sessionId) {
        viewModel.initializeSession(sessionId)

        viewModel.uiEvents.collect { event ->
            when (event) {
                is CaptureUiEvent.TriggerShutterFlash -> {
                    isShutterFlashing = true
                    kotlinx.coroutines.delay(100)
                    isShutterFlashing = false
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(orientation) {
        viewModel.evaluateAutoCapture(orientation) { onFrameSaved ->
            val capture = imageCapture ?: return@evaluateAutoCapture
            val outputDir = File(context.filesDir, "raw_frames/$sessionId").apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis())
            val photoFile = File(outputDir, "FRAME_$timeStamp.jpg")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onFrameSaved(photoFile.absolutePath)
                    }

                    override fun onError(exc: ImageCaptureException) {}
                }
            )
        }
    }

    if (gyroMissingDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Hardware Gyroscope Required") },
            text = { Text("This device does not have a hardware gyroscope sensor, which is required for 360° capture.") },
            confirmButton = {
                TextButton(onClick = onFinishCapture) {
                    Text("Close")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // CameraX Viewfinder
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(Size(3264, 2448))
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // AR Spherical Reticle Canvas Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radiusScale = size.width * 0.4f

            // Draw center alignment reticle
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 24.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw floating 28 target dots
            dots.forEach { dot ->
                val yawDiff = getAngleDiff(orientation.yawDeg, dot.targetYawDeg)
                val pitchDiff = dot.targetPitchDeg - orientation.pitchDeg

                // Project 3D spherical angle differences onto 2D viewfinder canvas
                val dx = (yawDiff / 45f) * radiusScale
                val dy = -(pitchDiff / 45f) * radiusScale

                val dotX = centerX + dx
                val dotY = centerY + dy

                val dotColor = when (dot.state) {
                    TargetDot.State.UNCAPTURED -> Color.Gray.copy(alpha = 0.7f)
                    TargetDot.State.IN_ALIGNMENT -> Color(0xFF3B82F6) // Pulsing Blue
                    TargetDot.State.CAPTURED -> Color(0xFF16A34A)   // Success Green
                }

                val dotRadius = if (dot.state == TargetDot.State.IN_ALIGNMENT) 16.dp.toPx() else 10.dp.toPx()

                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Shutter Flash Overlay Animation
        AnimatedVisibility(
            visible = isShutterFlashing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // Top Header Progress Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Captured: ${viewModel.getCapturedCount()} / ${viewModel.getTotalCount()}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onFinishCapture,
                enabled = viewModel.isFinishAvailable(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A56DB))
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (viewModel.isFinishAvailable()) "Finish Capture & Stitch" else "Capture at least 16 frames",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getAngleDiff(angle1: Float, angle2: Float): Float {
    var diff = angle2 - angle1
    while (diff < -180f) diff += 360f
    while (diff > 180f) diff -= 360f
    return diff
}
