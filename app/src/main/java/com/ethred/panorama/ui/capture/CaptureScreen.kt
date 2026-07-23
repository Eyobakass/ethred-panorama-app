package com.ethred.panorama.ui.capture

import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ethred.panorama.sensors.TargetDot
import android.hardware.camera2.CaptureRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isShutterFlashing by remember { mutableStateOf(false) }
    var firstFrameCaptured by remember { mutableStateOf(false) }
    val gyroMissingDialog = remember { !viewModel.sensorProcessor.isGyroscopeAvailable() }

    // Animated pulse scale for IN_ALIGNMENT dots
    val pulseAnim = rememberInfiniteTransition(label = "dot_pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

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

            // Lock AWB + AE after first frame, as per FR-CAP-04
            if (!firstFrameCaptured) {
                firstFrameCaptured = true
                cameraControl?.let { ctrl ->
                    try {
                        val camera2Control = Camera2CameraControl.from(ctrl)
                        camera2Control.setCaptureRequestOptions(
                            CaptureRequestOptions.Builder()
                                .setCaptureRequestOption(
                                    CaptureRequest.CONTROL_AWB_LOCK, true
                                )
                                .setCaptureRequestOption(
                                    CaptureRequest.CONTROL_AE_LOCK, true
                                )
                                .build()
                        )
                    } catch (e: Exception) { /* camera2 not available on this device */ }
                }
            }

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
            text = { Text("This device does not have a hardware gyroscope, which is required for 360° capture.") },
            confirmButton = {
                TextButton(onClick = onFinishCapture) { Text("Close") }
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
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // FR-CAP-04: JPEG quality 95, flash off, minimise latency
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(Size(3264, 2448))  // ~8MP minimum per SRS
                        .setJpegQuality(95)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                        cameraControl = camera.cameraControl

                        // FR-CAP-04: Lock zoom at 1.0× (no optical zoom)
                        camera.cameraControl.setLinearZoom(0f)

                    } catch (e: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // AR Spherical Reticle Canvas with tap-to-capture (FR-CAP-05)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dots) {
                    detectTapGestures { tapOffset ->
                        // Manual tap on dot = force capture (FR-CAP-05)
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val centerX = canvasW / 2f
                        val centerY = canvasH / 2f
                        val radiusScale = canvasW * 0.4f

                        val nearDot = dots.firstOrNull { dot ->
                            val yawDiff = getAngleDiff(orientation.yawDeg, dot.targetYawDeg)
                            val pitchDiff = dot.targetPitchDeg - orientation.pitchDeg
                            val dotX = centerX + (yawDiff / 45f) * radiusScale
                            val dotY = centerY - (pitchDiff / 45f) * radiusScale
                            val dx = tapOffset.x - dotX
                            val dy = tapOffset.y - dotY
                            Math.sqrt((dx * dx + dy * dy).toDouble()) < 60.0 &&
                                dot.state == TargetDot.State.UNCAPTURED
                        }
                        if (nearDot != null) {
                            viewModel.forceCaptureDot(nearDot) { onFrameSaved ->
                                val capture = imageCapture ?: return@forceCaptureDot
                                val outputDir = File(context.filesDir, "raw_frames/$sessionId").apply { if (!exists()) mkdirs() }
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis())
                                val photoFile = File(outputDir, "FRAME_MANUAL_$timeStamp.jpg")
                                val opts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                capture.takePicture(opts, ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            onFrameSaved(photoFile.absolutePath)
                                        }
                                        override fun onError(exc: ImageCaptureException) {}
                                    }
                                )
                            }
                        }
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radiusScale = size.width * 0.4f

            // Center alignment reticle
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = 24.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY)
            )

            dots.forEach { dot ->
                val yawDiff = getAngleDiff(orientation.yawDeg, dot.targetYawDeg)
                val pitchDiff = dot.targetPitchDeg - orientation.pitchDeg
                val dotX = centerX + (yawDiff / 45f) * radiusScale
                val dotY = centerY - (pitchDiff / 45f) * radiusScale

                when (dot.state) {
                    TargetDot.State.UNCAPTURED -> {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = 10.dp.toPx(),
                            center = Offset(dotX, dotY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    TargetDot.State.IN_ALIGNMENT -> {
                        // Pulsing blue rings (animated)
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                            radius = (10.dp.toPx() * pulseScale * 2f),
                            center = Offset(dotX, dotY)
                        )
                        drawCircle(
                            color = Color(0xFF3B82F6),
                            radius = 10.dp.toPx() * pulseScale,
                            center = Offset(dotX, dotY)
                        )
                    }
                    TargetDot.State.CAPTURED -> {
                        drawCircle(
                            color = Color(0xFF16A34A),
                            radius = 10.dp.toPx(),
                            center = Offset(dotX, dotY)
                        )
                        // Checkmark ✓ stroke
                        val s = 5.dp.toPx()
                        drawLine(Color.White, Offset(dotX - s, dotY), Offset(dotX - s * 0.3f, dotY + s * 0.7f), 2.dp.toPx())
                        drawLine(Color.White, Offset(dotX - s * 0.3f, dotY + s * 0.7f), Offset(dotX + s, dotY - s * 0.7f), 2.dp.toPx())
                    }
                }
            }
        }

        // Shutter flash overlay
        AnimatedVisibility(visible = isShutterFlashing, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
        }

        // Top progress overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 48.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val capturedCount = viewModel.getCapturedCount()
            val totalCount = viewModel.getTotalCount()
            val progress = capturedCount.toFloat() / totalCount.toFloat()

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Captured $capturedCount / $totalCount",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        // Bottom finish button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter)
        ) {
            val available = viewModel.isFinishAvailable()
            Button(
                onClick = {
                    viewModel.stopSensors()
                    onFinishCapture()
                },
                enabled = available,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A56DB),
                    disabledContainerColor = Color.White.copy(alpha = 0.2f)
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Finish capture")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (available) "Finish Capture & Stitch" else "Capture at least 16 frames",
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
