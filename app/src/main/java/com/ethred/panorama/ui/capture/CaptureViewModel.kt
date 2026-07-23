package com.ethred.panorama.ui.capture

import android.content.Context
import android.os.Environment
import android.os.StatFs
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
import javax.inject.Inject

sealed class CaptureUiEvent {
    object TriggerShutterFlash : CaptureUiEvent()
    data class ShowToast(val message: String) : CaptureUiEvent()
    data class ShowError(val message: String) : CaptureUiEvent()
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val sensorProcessor: SensorOrientationProcessor,
    val gridManager: CaptureGridManager,
    private val sessionRepository: CaptureSessionRepository
) : ViewModel() {

    val orientationFlow: StateFlow<DeviceOrientation> = sensorProcessor.orientationFlow
    val dotsFlow: StateFlow<List<TargetDot>> = gridManager.dotsFlow

    private val _uiEvents = MutableSharedFlow<CaptureUiEvent>()
    val uiEvents: SharedFlow<CaptureUiEvent> = _uiEvents.asSharedFlow()

    private val _storageOk = MutableStateFlow(true)
    val storageOk: StateFlow<Boolean> = _storageOk.asStateFlow()

    private var currentSessionId: String? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

    /** Initialize or resume a capture session.
     *  Restored frames from DB set dot states so user doesn't re-capture already done positions. */
    fun initializeSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            // FR-CAP-06: Restore dot states from persisted frames
            val existingFrames = sessionRepository.getFrames(sessionId)
            gridManager.resetGrid()

            existingFrames.forEach { frame ->
                // Find the closest dot to restore
                val matchedDot = gridManager.dotsFlow.value.firstOrNull { dot ->
                    val yawDiff = kotlin.math.abs(dot.targetYawDeg - frame.yawDeg) % 360f
                    val safeYawDiff = if (yawDiff > 180f) 360f - yawDiff else yawDiff
                    val pitchDiff = kotlin.math.abs(dot.targetPitchDeg - frame.pitchDeg)
                    safeYawDiff <= 8f && pitchDiff <= 8f && dot.state == TargetDot.State.UNCAPTURED
                }
                matchedDot?.let { gridManager.markDotCaptured(it.id) }
            }

            // NFR-MEM: Check storage availability (500MB minimum per SRS)
            checkStorageAvailability()
        }
        sensorProcessor.startListening()
    }

    private fun checkStorageAvailability() {
        val stats = StatFs(context.filesDir.path)
        val freeBytes = stats.availableBlocksLong * stats.blockSizeLong
        val requiredBytes = 500L * 1024 * 1024 // 500 MB
        _storageOk.value = freeBytes >= requiredBytes
        if (!_storageOk.value) {
            viewModelScope.launch {
                _uiEvents.emit(CaptureUiEvent.ShowError(
                    "Insufficient storage. Please free at least 500 MB before capturing."
                ))
            }
        }
    }

    fun stopSensors() {
        sensorProcessor.stopListening()
    }

    /** Auto-capture triggered by gyro alignment (±2° for 300ms). */
    fun evaluateAutoCapture(
        orientation: DeviceOrientation,
        onCaptureTrigger: (onSuccess: (filePath: String) -> Unit) -> Unit
    ) {
        if (!_storageOk.value) return

        val capturedDot = gridManager.evaluateOrientation(orientation.yawDeg, orientation.pitchDeg)
        if (capturedDot != null) {
            hapticFeedback()
            viewModelScope.launch { _uiEvents.emit(CaptureUiEvent.TriggerShutterFlash) }

            onCaptureTrigger { savedFilePath ->
                currentSessionId?.let { sessionId ->
                    viewModelScope.launch {
                        sessionRepository.saveFrame(
                            sessionId = sessionId,
                            filePath = savedFilePath,
                            yaw = orientation.yawDeg,
                            pitch = orientation.pitchDeg,
                            roll = orientation.rollDeg
                        )
                    }
                }
            }
        }
    }

    /** Manual tap-to-capture override (FR-CAP-05). */
    fun forceCaptureDot(
        dot: TargetDot,
        onCaptureTrigger: (onSuccess: (filePath: String) -> Unit) -> Unit
    ) {
        gridManager.markDotCaptured(dot.id)
        hapticFeedback()
        viewModelScope.launch { _uiEvents.emit(CaptureUiEvent.TriggerShutterFlash) }

        onCaptureTrigger { savedFilePath ->
            currentSessionId?.let { sessionId ->
                viewModelScope.launch {
                    sessionRepository.saveFrame(
                        sessionId = sessionId,
                        filePath = savedFilePath,
                        yaw = dot.targetYawDeg,
                        pitch = dot.targetPitchDeg,
                        roll = 0f
                    )
                }
            }
        }
    }

    fun getCapturedCount(): Int = gridManager.getCapturedCount()
    fun getTotalCount(): Int = gridManager.getTotalCount()
    fun isFinishAvailable(): Boolean = getCapturedCount() >= 16

    private fun hapticFeedback() {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorProcessor.stopListening()
    }
}
