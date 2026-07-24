package com.ethred.panorama.ui.capture

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.CameraControl
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.sensors.CaptureGridManager
import com.ethred.panorama.sensors.DeviceOrientation
import com.ethred.panorama.sensors.SensorOrientationProcessor
import com.ethred.panorama.sensors.TargetDot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed class CaptureUiEvent {
    object TriggerShutterFlash : CaptureUiEvent()
    data class ShowError(val message: String) : CaptureUiEvent()
    data class ShowSnackbar(val message: String) : CaptureUiEvent()
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val sensorProcessor: SensorOrientationProcessor,
    val gridManager: CaptureGridManager,
    private val sessionRepository: CaptureSessionRepository
) : ViewModel() {

    val orientationFlow: StateFlow<DeviceOrientation> = sensorProcessor.orientationFlow
    val dotsFlow: StateFlow<List<TargetDot>>          = gridManager.dotsFlow

    private val _uiEvents = MutableSharedFlow<CaptureUiEvent>()
    val uiEvents: SharedFlow<CaptureUiEvent> = _uiEvents.asSharedFlow()

    private val _storageOk = MutableStateFlow(true)
    val storageOk: StateFlow<Boolean> = _storageOk.asStateFlow()

    private var currentSessionId: String? = null

    // AtomicBoolean guard — prevents concurrent captures if sensor fires rapidly
    private val isCaptureInProgress = AtomicBoolean(false)

    // ── Vibrator: API 31+ VibratorManager, below: legacy Vibrator ────────────
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // ── Camera control refs (set from CaptureScreen after camera binds) ───────
    private var _cameraControl: CameraControl? = null

    fun setCameraControl(ctrl: CameraControl?) {
        _cameraControl = ctrl
    }

    fun setExposureCompensation(index: Int) {
        _cameraControl?.setExposureCompensationIndex(index)
    }

    /**
     * Initialize or resume a capture session.
     * Restores dot states from already-persisted frames so agent doesn't re-capture them.
     */
    fun initializeSession(sessionId: String, nodeCount: Int = 28) {
        currentSessionId = sessionId
        viewModelScope.launch {
            val existingFrames = sessionRepository.getFrames(sessionId)
            gridManager.resetGrid(nodeCount)

            existingFrames.forEach { frame ->
                val matchedDot = gridManager.dotsFlow.value.firstOrNull { dot ->
                    val yawDiff = kotlin.math.abs(dot.targetYawDeg - frame.yawDeg) % 360f
                    val safeYaw = if (yawDiff > 180f) 360f - yawDiff else yawDiff
                    val pitchDiff = kotlin.math.abs(dot.targetPitchDeg - frame.pitchDeg)
                    safeYaw <= 8f && pitchDiff <= 8f && dot.state == TargetDot.State.UNCAPTURED
                }
                matchedDot?.let { gridManager.markDotCaptured(it.id) }
            }

            checkStorageAvailability()
        }
        sensorProcessor.startListening()
    }

    private fun checkStorageAvailability() {
        val stats = StatFs(context.filesDir.path)
        val freeBytes = stats.availableBlocksLong * stats.blockSizeLong
        val requiredBytes = 500L * 1024 * 1024
        _storageOk.value = freeBytes >= requiredBytes
        if (!_storageOk.value) {
            viewModelScope.launch {
                _uiEvents.emit(
                    CaptureUiEvent.ShowError(
                        "Low storage — free at least 500 MB before capturing."
                    )
                )
            }
        }
    }

    fun stopSensors() { sensorProcessor.stopListening() }

    /** Auto-capture triggered by gyro alignment (±2° for 300ms). */
    fun evaluateAutoCapture(
        orientation: DeviceOrientation,
        onCaptureTrigger: (onSuccess: (filePath: String) -> Unit) -> Unit
    ) {
        if (!_storageOk.value) return
        if (!isCaptureInProgress.compareAndSet(false, true)) return // skip if already capturing

        val capturedDot = gridManager.evaluateOrientation(orientation.yawDeg, orientation.pitchDeg)
        if (capturedDot != null) {
            hapticFeedback()
            viewModelScope.launch { _uiEvents.emit(CaptureUiEvent.TriggerShutterFlash) }

            onCaptureTrigger { savedFilePath ->
                currentSessionId?.let { sessionId ->
                    viewModelScope.launch {
                        sessionRepository.saveFrame(
                            sessionId = sessionId,
                            filePath  = savedFilePath,
                            yaw       = orientation.yawDeg,
                            pitch     = orientation.pitchDeg,
                            roll      = orientation.rollDeg
                        )
                        isCaptureInProgress.set(false)
                    }
                } ?: isCaptureInProgress.set(false)
            }
        } else {
            isCaptureInProgress.set(false)
        }
    }

    fun onCaptureFailed(reason: String) {
        isCaptureInProgress.set(false)
        viewModelScope.launch {
            _uiEvents.emit(CaptureUiEvent.ShowSnackbar("Frame capture failed: $reason"))
        }
    }

    /** Manual tap-to-capture override (FR-CAP-05). */
    fun forceCaptureDot(
        dot: TargetDot,
        onCaptureTrigger: (onSuccess: (filePath: String) -> Unit) -> Unit
    ) {
        if (!isCaptureInProgress.compareAndSet(false, true)) return
        gridManager.markDotCaptured(dot.id)
        hapticFeedback()
        viewModelScope.launch { _uiEvents.emit(CaptureUiEvent.TriggerShutterFlash) }

        onCaptureTrigger { savedFilePath ->
            currentSessionId?.let { sessionId ->
                viewModelScope.launch {
                    sessionRepository.saveFrame(
                        sessionId = sessionId,
                        filePath  = savedFilePath,
                        yaw       = dot.targetYawDeg,
                        pitch     = dot.targetPitchDeg,
                        roll      = 0f
                    )
                    isCaptureInProgress.set(false)
                }
            } ?: isCaptureInProgress.set(false)
        }
    }

    fun getCapturedCount(): Int  = gridManager.getCapturedCount()
    fun getTotalCount(): Int     = gridManager.getTotalCount()
    fun isFinishAvailable(): Boolean =
        getCapturedCount() >= gridManager.minimumRequiredFrames()

    private fun hapticFeedback() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(50)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorProcessor.stopListening()
    }
}
