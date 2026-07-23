package com.ethred.panorama.sensors

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CaptureGridManagerTest {

    private lateinit var gridManager: CaptureGridManager

    @Before
    fun setUp() {
        gridManager = CaptureGridManager()
    }

    @Test
    fun testGridInitialization_createsExact28TargetDots() {
        val dots = gridManager.dotsFlow.value
        assertEquals(28, dots.size)

        // Upper Ring (+30° Pitch): 8 dots
        val upperRing = dots.filter { it.targetPitchDeg == 30f }
        assertEquals(8, upperRing.size)

        // Horizontal Ring (0° Pitch): 12 dots
        val horizontalRing = dots.filter { it.targetPitchDeg == 0f }
        assertEquals(12, horizontalRing.size)

        // Lower Ring (-30° Pitch): 8 dots
        val lowerRing = dots.filter { it.targetPitchDeg == -30f }
        assertEquals(8, lowerRing.size)
    }

    @Test
    fun testEvaluateOrientation_outOfTolerance_doesNotCapture() {
        // Evaluate yaw 100°, pitch 50° (no dot aligned)
        val captured = gridManager.evaluateOrientation(100f, 50f)
        assertNull(captured)
        assertEquals(0, gridManager.getCapturedCount())
    }

    @Test
    fun testEvaluateOrientation_withinTolerance_alignmentStateSet() {
        // Dot #1 is at yaw 0°, pitch 30°
        gridManager.evaluateOrientation(0.5f, 30.2f)
        val dots = gridManager.dotsFlow.value
        val dot1 = dots.first { it.id == 1 }

        // State should be IN_ALIGNMENT during first check (< 300ms)
        assertTrue(dot1.state == TargetDot.State.IN_ALIGNMENT || dot1.state == TargetDot.State.CAPTURED)
    }

    @Test
    fun testMarkDotCaptured_incrementsCapturedCount() {
        gridManager.markDotCaptured(1)
        gridManager.markDotCaptured(2)

        assertEquals(2, gridManager.getCapturedCount())
        assertEquals(28, gridManager.getTotalCount())
    }

    @Test
    fun testResetGrid_clearsAllCapturedDots() {
        gridManager.markDotCaptured(1)
        gridManager.markDotCaptured(2)
        assertEquals(2, gridManager.getCapturedCount())

        gridManager.resetGrid()
        assertEquals(0, gridManager.getCapturedCount())
        assertEquals(28, gridManager.getTotalCount())
    }
}
