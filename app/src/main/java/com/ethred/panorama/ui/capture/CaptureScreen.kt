package com.ethred.panorama.ui.capture

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ethred.panorama.sensors.TargetDot
import com.ethred.panorama.ui.theme.SuccessGreen
import com.ethred.panorama.ui.theme.WarningAmber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CaptureScreen(
    sessionId: String,
    nodeCount: Int = 28,
    viewModel: CaptureViewModel,
    onFinishCapture: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── All state vars declared FIRST ────────────────────────────────────────
    val orientation by viewModel.orientationFlow.collectAsState()
    val dots        by viewModel.dotsFlow.collectAsState()
    val storageOk   by viewModel.storageOk.collectAsState()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isShutterFlashing   by remember { mutableStateOf(false) }
    var firstFrameCaptured  by remember { mutableStateOf(false) }
    var showBackDialog      by remember { mutableStateOf(false) }
    var showCameraControls  by remember { mutableStateOf(false) }
    var exposureIndex       by remember { mutableIntStateOf(0) }
    var focusDistance       by remember { mutableFloatStateOf(0f) }   // 0 = auto
    var isManualFocus       by remember { mutableStateOf(false) }

    val gyroMissing = remember { !viewModel.sensorProcessor.isGyroscopeAvailable() }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract  = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult  = { granted -> hasCameraPermission = granted }
    )

    val pulseAnim = rememberInfiniteTransition(label = "dot_pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue  = 1.6f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // ── Effects ───────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(android.Manifest.permission.CAMERA)
        viewModel.initializeSession(sessionId, nodeCount)
    }

    LaunchedEffect(sessionId) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is CaptureUiEvent.TriggerShutterFlash -> {
                    isShutterFlashing = true
                    kotlinx.coroutines.delay(100)
                    isShutterFlashing = false
                }
                is CaptureUiEvent.ShowError    ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Long)
                is CaptureUiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Apply manual exposure index whenever it changes
    LaunchedEffect(exposureIndex) {
        if (cameraControl != null) viewModel.setExposureCompensation(exposureIndex)
    }

    // Apply manual focus distance via Camera2 when toggled on
    LaunchedEffect(isManualFocus, focusDistance, cameraControl) {
        cameraControl?.let { ctrl ->
            try {
                val cam2 = Camera2CameraControl.from(ctrl)
                if (isManualFocus) {
                    cam2.setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_OFF
                            )
                            .setCaptureRequestOption(
                                CaptureRequest.LENS_FOCUS_DISTANCE,
                                focusDistance
                            )
                            .build()
                    )
                } else {
                    cam2.setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            .build()
                    )
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(orientation) {
        viewModel.evaluateAutoCapture(orientation) { onFrameSaved ->
            val capture = imageCapture ?: return@evaluateAutoCapture

            if (!firstFrameCaptured) {
                firstFrameCaptured = true
                cameraControl?.let { ctrl: CameraControl ->
                    try {
                        Camera2CameraControl.from(ctrl).setCaptureRequestOptions(
                            CaptureRequestOptions.Builder()
                                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                                .build()
                        )
                    } catch (_: Exception) {}
                }
            }

            val outputDir  = File(context.filesDir, "raw_frames/$sessionId").apply { mkdirs() }
            val timeStamp  = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis())
            val photoFile  = File(outputDir, "FRAME_$timeStamp.jpg")

            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onFrameSaved(photoFile.absolutePath)
                    }
                    override fun onError(exc: ImageCaptureException) {
                        viewModel.onCaptureFailed(exc.message ?: "Unknown error")
                    }
                }
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (gyroMissing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Gyroscope Required") },
            text  = { Text("This device has no hardware gyroscope, which is required for 360° capture.") },
            confirmButton = { TextButton(onClick = onFinishCapture) { Text("Close") } }
        )
    }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title   = { Text("Discard Capture?") },
            text    = { Text("All captured frames for this session will be discarded. Are you sure?") },
            confirmButton = {
                TextButton(onClick = { viewModel.stopSensors(); onNavigateBack() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) { Text("Continue Capturing") }
            }
        )
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { _ ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // ── Camera Viewfinder ─────────────────────────────────────────────
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { previewView ->
                            ProcessCameraProvider.getInstance(ctx).let { future ->
                                future.addListener({
                                    val provider = future.get()
                                    val preview  = Preview.Builder()
                                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                        .build()
                                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                    imageCapture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                        .setJpegQuality(95)
                                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                                        .build()

                                    try {
                                        provider.unbindAll()
                                        val cam = provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview, imageCapture
                                        )
                                        cameraControl = cam.cameraControl
                                        viewModel.setCameraControl(cam.cameraControl)
                                        cam.cameraControl.setLinearZoom(0f)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission required", color = Color.White,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }) { Text("Grant Permission") }
                    }
                }
            }

            // ── AR Dot Overlay ────────────────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dots) {
                        detectTapGestures { tapOffset ->
                            val centerX     = size.width / 2f
                            val centerY     = size.height / 2f
                            val radiusScale = size.width * 0.4f
                            val nearDot = dots.firstOrNull { dot: TargetDot ->
                                val yawDiff   = getAngleDiff(orientation.yawDeg, dot.targetYawDeg)
                                val pitchDiff = dot.targetPitchDeg - orientation.pitchDeg
                                val dx        = tapOffset.x - (centerX + (yawDiff / 45f) * radiusScale)
                                val dy        = tapOffset.y - (centerY - (pitchDiff / 45f) * radiusScale)
                                kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) < 60.0 &&
                                    dot.state == TargetDot.State.UNCAPTURED
                            }
                            if (nearDot != null) {
                                viewModel.forceCaptureDot(nearDot) { onFrameSaved ->
                                    val capture  = imageCapture ?: return@forceCaptureDot
                                    val outputDir = File(context.filesDir, "raw_frames/$sessionId")
                                        .apply { mkdirs() }
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
                                        .format(System.currentTimeMillis())
                                    val photoFile = File(outputDir, "FRAME_MANUAL_$timeStamp.jpg")
                                    capture.takePicture(
                                        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                                                onFrameSaved(photoFile.absolutePath)
                                            }
                                            override fun onError(exc: ImageCaptureException) {
                                                viewModel.onCaptureFailed(exc.message ?: "Error")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rs = size.width * 0.4f

                // Center reticle
                drawCircle(Color.White.copy(alpha = 0.7f), 24.dp.toPx(), Offset(cx, cy),
                    style = Stroke(2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.3f), 6.dp.toPx(), Offset(cx, cy))

                dots.forEach { dot: TargetDot ->
                    val yawDiff   = getAngleDiff(orientation.yawDeg, dot.targetYawDeg)
                    val pitchDiff = dot.targetPitchDeg - orientation.pitchDeg
                    val dotX      = cx + (yawDiff / 45f) * rs
                    val dotY      = cy - (pitchDiff / 45f) * rs

                    when (dot.state) {
                        TargetDot.State.UNCAPTURED -> drawCircle(
                            Color.White.copy(alpha = 0.5f), 10.dp.toPx(), Offset(dotX, dotY),
                            style = Stroke(1.5.dp.toPx())
                        )
                        TargetDot.State.IN_ALIGNMENT -> {
                            drawCircle(Color(0xFF3B82F6).copy(alpha = 0.25f),
                                10.dp.toPx() * pulseScale * 2f, Offset(dotX, dotY))
                            drawCircle(Color(0xFF3B82F6), 10.dp.toPx() * pulseScale, Offset(dotX, dotY))
                        }
                        TargetDot.State.CAPTURED -> {
                            drawCircle(SuccessGreen, 10.dp.toPx(), Offset(dotX, dotY))
                            val s = 5.dp.toPx()
                            drawLine(Color.White, Offset(dotX - s, dotY),
                                Offset(dotX - s * 0.3f, dotY + s * 0.7f), 2.dp.toPx())
                            drawLine(Color.White, Offset(dotX - s * 0.3f, dotY + s * 0.7f),
                                Offset(dotX + s, dotY - s * 0.7f), 2.dp.toPx())
                        }
                    }
                }
            }

            // ── Shutter flash ─────────────────────────────────────────────────
            AnimatedVisibility(visible = isShutterFlashing, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
            }

            // ── Top bar: back + progress ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showBackDialog = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = Color.White)
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        val captured = viewModel.getCapturedCount()
                        val total    = viewModel.getTotalCount()
                        val progress = if (total > 0) captured.toFloat() / total else 0f
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$captured / $total frames", color = Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.width(140.dp).height(3.dp),
                                color    = Color(0xFF3B82F6),
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                    IconButton(onClick = { showCameraControls = !showCameraControls }) {
                        Icon(Icons.Default.Settings, contentDescription = "Camera Controls",
                            tint = Color.White)
                    }
                }

                // Low storage warning
                if (!storageOk) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.9f)),
                        shape  = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color.White,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Storage low — capture paused", color = Color.White,
                                fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Camera Controls Panel (collapsible) ───────────────────────────
            AnimatedVisibility(
                visible = showCameraControls,
                enter   = fadeIn(),
                exit    = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.82f)
                ) {
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Camera Controls", color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        // Exposure compensation
                        Text("Exposure  ${if (exposureIndex >= 0) "+" else ""}$exposureIndex EV",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        Slider(
                            value         = exposureIndex.toFloat(),
                            onValueChange = { exposureIndex = it.toInt() },
                            valueRange    = -6f..6f,
                            steps         = 11,
                            colors        = SliderDefaults.colors(
                                thumbColor       = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6)
                            )
                        )

                        // Focus mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manual Focus", color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp)
                            Switch(
                                checked         = isManualFocus,
                                onCheckedChange = { isManualFocus = it }
                            )
                        }

                        AnimatedVisibility(visible = isManualFocus) {
                            Column {
                                Text(
                                    "Focus: ${
                                        when {
                                            focusDistance < 0.1f -> "∞ Far"
                                            focusDistance < 0.5f -> "Mid"
                                            else                 -> "Near"
                                        }
                                    }",
                                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp
                                )
                                Slider(
                                    value         = focusDistance,
                                    onValueChange = { focusDistance = it },
                                    valueRange    = 0f..10f,
                                    colors        = SliderDefaults.colors(
                                        thumbColor       = Color(0xFF0EA5E9),
                                        activeTrackColor = Color(0xFF0EA5E9)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Bottom: finish button ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                val available = viewModel.isFinishAvailable()
                val minFrames = viewModel.gridManager.minimumRequiredFrames()
                Button(
                    onClick = { viewModel.stopSensors(); onFinishCapture() },
                    enabled = available,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFF1A56DB),
                        disabledContainerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Finish")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text     = if (available) "Finish & Stitch" else "Need ${minFrames - viewModel.getCapturedCount()} more frames",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
