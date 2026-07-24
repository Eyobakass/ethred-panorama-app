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

    /** Current node count for the session (set by resetGrid) */
    private var _nodeCount: Int = 28
    val nodeCount: Int get() = _nodeCount

    init {
        resetGrid(28)
    }

    /**
     * Builds a spherical dot grid for the given [nodeCount].
     *
     * | nodeCount ≤ 12 | Equatorial ring only:  12 dots @ 30° spacing          |
     * | nodeCount ≤ 20 | Upper + equatorial:     8 + 12 dots                   |
     * | else           | Full sphere:            8 + 12 + 8 dots (default 28)  |
     * | custom > 28    | Dense 4-ring layout up to 36 dots                     |
     */
    fun resetGrid(nodeCount: Int = 28) {
        _nodeCount = nodeCount.coerceIn(12, 36)
        val dots = mutableListOf<TargetDot>()
        var id = 1

        when {
            _nodeCount <= 12 -> {
                // 12 equatorial dots at 30° spacing
                for (i in 0 until 12) {
                    dots.add(TargetDot(id++, targetYawDeg = i * 30f, targetPitchDeg = 0f))
                }
            }
            _nodeCount <= 20 -> {
                // 8 upper + 12 equatorial = 20 dots
                for (i in 0 until 8)  dots.add(TargetDot(id++, i * 45f, 35f))
                for (i in 0 until 12) dots.add(TargetDot(id++, i * 30f, 0f))
            }
            _nodeCount <= 28 -> {
                // Standard full sphere: 8 + 12 + 8 = 28
                for (i in 0 until 8)  dots.add(TargetDot(id++, i * 45f, 30f))
                for (i in 0 until 12) dots.add(TargetDot(id++, i * 30f, 0f))
                for (i in 0 until 8)  dots.add(TargetDot(id++, i * 45f, -30f))
            }
            else -> {
                // Dense 4-ring for 29–36 dots: upper60, upper30, equator, lower30
                val perRing = _nodeCount / 4
                val rem = _nodeCount % 4
                val rings = listOf(60f, 30f, 0f, -30f)
                rings.forEachIndexed { ri, pitch ->
                    val count = perRing + if (ri < rem) 1 else 0
                    val step = 360f / count
                    for (i in 0 until count) {
                        dots.add(TargetDot(id++, i * step, pitch))
                    }
                }
            }
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

            val yawDiff   = getAngleDifference(yaw, dot.targetYawDeg)
            val pitchDiff = abs(pitch - dot.targetPitchDeg)

            if (yawDiff <= 2.0f && pitchDiff <= 2.0f) {
                foundAlignedInThisFrame = true

                if (currentAlignedDotId != dot.id) {
                    currentAlignedDotId  = dot.id
                    alignmentStartTime   = System.currentTimeMillis()
                }

                currentDots[i] = dot.copy(state = TargetDot.State.IN_ALIGNMENT)

                if (System.currentTimeMillis() - alignmentStartTime >= 300L) {
                    currentDots[i]       = dot.copy(state = TargetDot.State.CAPTURED)
                    newlyCapturedDot     = currentDots[i]
                    currentAlignedDotId  = null
                    alignmentStartTime   = 0L
                }
            } else if (dot.state == TargetDot.State.IN_ALIGNMENT) {
                currentDots[i] = dot.copy(state = TargetDot.State.UNCAPTURED)
            }
        }

        if (!foundAlignedInThisFrame && currentAlignedDotId != null) {
            currentAlignedDotId = null
            alignmentStartTime  = 0L
        }

        _dotsFlow.value = currentDots
        return newlyCapturedDot
    }

    fun markDotCaptured(dotId: Int) {
        _dotsFlow.value = _dotsFlow.value.map {
            if (it.id == dotId) it.copy(state = TargetDot.State.CAPTURED) else it
        }
    }

    fun getCapturedCount(): Int = _dotsFlow.value.count { it.state == TargetDot.State.CAPTURED }
    fun getTotalCount(): Int    = _dotsFlow.value.size

    /** 85% of chosen node count, minimum 10. */
    fun minimumRequiredFrames(): Int = (_nodeCount * 0.85).toInt().coerceAtLeast(10)

    private fun getAngleDifference(angle1: Float, angle2: Float): Float {
        val diff = abs(angle1 - angle2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
