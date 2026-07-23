package com.ethred.panorama.sensors

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CaptureGridManager @Inject constructor() {

    private val _dotsFlow = MutableStateFlow<List<TargetDot>>(emptyList())
    val dotsFlow: StateFlow<List<TargetDot>> = _dotsFlow.asStateFlow()

    private var alignmentStartTime: Long = 0L
    private var currentAlignedDotId: Int? = null

    init {
        resetGrid()
    }

    fun resetGrid() {
        val dots = mutableListOf<TargetDot>()
        var idCounter = 1

        // Upper Ring (+30° Pitch): 8 dots
        for (i in 0 until 8) {
            dots.add(
                TargetDot(
                    id = idCounter++,
                    targetYawDeg = i * 45f,
                    targetPitchDeg = 30f
                )
            )
        }

        // Horizontal Ring (0° Pitch): 12 dots
        for (i in 0 until 12) {
            dots.add(
                TargetDot(
                    id = idCounter++,
                    targetYawDeg = i * 30f,
                    targetPitchDeg = 0f
                )
            )
        }

        // Lower Ring (-30° Pitch): 8 dots
        for (i in 0 until 8) {
            dots.add(
                TargetDot(
                    id = idCounter++,
                    targetYawDeg = i * 45f,
                    targetPitchDeg = -30f
                )
            )
        }

        _dotsFlow.value = dots
        alignmentStartTime = 0L
        currentAlignedDotId = null
    }

    /**
     * Evaluates current orientation against remaining target dots.
     * Returns the TargetDot if auto-capture conditions (±2° tolerance for ≥300ms) are satisfied.
     */
    fun evaluateOrientation(yaw: Float, pitch: Float): TargetDot? {
        val currentDots = _dotsFlow.value.toMutableList()
        var newlyCapturedDot: TargetDot? = null
        var foundAlignedInThisFrame = false

        for (i in currentDots.indices) {
            val dot = currentDots[i]
            if (dot.state == TargetDot.State.CAPTURED) continue

            val yawDiff = getAngleDifference(yaw, dot.targetYawDeg)
            val pitchDiff = abs(pitch - dot.targetPitchDeg)

            // Check tolerance of ±2°
            if (yawDiff <= 2.0f && pitchDiff <= 2.0f) {
                foundAlignedInThisFrame = true

                if (currentAlignedDotId != dot.id) {
                    currentAlignedDotId = dot.id
                    alignmentStartTime = System.currentTimeMillis()
                }

                currentDots[i] = dot.copy(state = TargetDot.State.IN_ALIGNMENT)

                // Check 300ms debounce condition
                if (System.currentTimeMillis() - alignmentStartTime >= 300L) {
                    currentDots[i] = dot.copy(state = TargetDot.State.CAPTURED)
                    newlyCapturedDot = currentDots[i]
                    currentAlignedDotId = null
                    alignmentStartTime = 0L
                }
            } else if (dot.state == TargetDot.State.IN_ALIGNMENT) {
                currentDots[i] = dot.copy(state = TargetDot.State.UNCAPTURED)
            }
        }

        if (!foundAlignedInThisFrame && currentAlignedDotId != null) {
            currentAlignedDotId = null
            alignmentStartTime = 0L
        }

        _dotsFlow.value = currentDots
        return newlyCapturedDot
    }

    fun markDotCaptured(dotId: Int) {
        _dotsFlow.value = _dotsFlow.value.map {
            if (it.id == dotId) it.copy(state = TargetDot.State.CAPTURED) else it
        }
    }

    fun getCapturedCount(): Int {
        return _dotsFlow.value.count { it.state == TargetDot.State.CAPTURED }
    }

    fun getTotalCount(): Int {
        return _dotsFlow.value.size
    }

    private fun getAngleDifference(angle1: Float, angle2: Float): Float {
        val diff = abs(angle1 - angle2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
