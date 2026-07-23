package com.ethred.panorama.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI

data class DeviceOrientation(
    val yawDeg: Float = 0f,   // 0° to 360° horizontal orientation
    val pitchDeg: Float = 0f, // -90° (down) to +90° (up) vertical tilt
    val rollDeg: Float = 0f   // -180° to +180° rotation around camera axis
)

@Singleton
class SensorOrientationProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _orientationFlow = MutableStateFlow(DeviceOrientation())
    val orientationFlow: StateFlow<DeviceOrientation> = _orientationFlow.asStateFlow()

    private var previousYaw = 0f
    private var previousPitch = 0f
    private var previousRoll = 0f
    private val alpha = 0.15f // Low-pass filter smoothing coefficient as per FR-CAP-01

    fun isGyroscopeAvailable(): Boolean {
        return rotationSensor != null
    }

    fun startListening() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert radians to degrees
        var rawYaw = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
        val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Apply low pass filter to eliminate sensor jitter
        val filteredYaw = alpha * rawYaw + (1f - alpha) * previousYaw
        val filteredPitch = alpha * rawPitch + (1f - alpha) * previousPitch
        val filteredRoll = alpha * rawRoll + (1f - alpha) * previousRoll

        previousYaw = filteredYaw
        previousPitch = filteredPitch
        previousRoll = filteredRoll

        _orientationFlow.value = DeviceOrientation(
            yawDeg = filteredYaw,
            pitchDeg = filteredPitch,
            rollDeg = filteredRoll
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
