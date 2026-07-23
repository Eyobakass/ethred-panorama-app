package com.ethred.panorama.ui.capture

import android.content.Context
import android.os.Vibrator
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
import java.io.File
import javax.inject.Inject

sealed class CaptureUiEvent {
    object TriggerShutterFlash : CaptureUiEvent()
    data class ShowToast(val message: String) : CaptureUiEvent()
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

    private var currentSessionId: String? = null
    private val vibrator = context.getSystemService(Context.Vibrator::class.java)

    fun initializeSession(sessionId: String) {
        currentSessionId = sessionId
        gridManager.resetGrid()
        sensorProcessor.startListening()
    }

    fun stopSensors() {
        sensorProcessor.stopListening()
    }

    fun evaluateAutoCapture(orientation: DeviceOrientation, onCaptureTrigger: (onSuccess: (filePath: String) -> Unit) -> Unit) {
        val capturedDot = gridManager.evaluateOrientation(orientation.yawDeg, orientation.pitchDeg)
        if (capturedDot != null) {
            // Trigger haptic vibration feedback
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

            viewModelScope.launch {
                _uiEvents.emit(CaptureUiEvent.TriggerShutterFlash)
            }

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

    fun getCapturedCount(): Int = gridManager.getCapturedCount()
    fun getTotalCount(): Int = gridManager.getTotalCount()

    fun isFinishAvailable(): Boolean = getCapturedCount() >= 16

    override fun onCleared() {
        super.onCleared()
        sensorProcessor.stopListening()
    }
}
