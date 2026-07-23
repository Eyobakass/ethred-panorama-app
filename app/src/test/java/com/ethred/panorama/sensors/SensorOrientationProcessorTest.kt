package com.ethred.panorama.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorOrientationProcessorTest {

    @Test
    fun testLowPassFilter_alpha015_smoothsValues() {
        val alpha = 0.15f
        var currentFiltered = 0f
        val newRaw = 10f

        // Apply low pass formula: filtered = alpha * raw + (1 - alpha) * filtered
        currentFiltered = alpha * newRaw + (1f - alpha) * currentFiltered
        assertEquals(1.5f, currentFiltered, 0.001f)

        currentFiltered = alpha * newRaw + (1f - alpha) * currentFiltered
        assertEquals(2.775f, currentFiltered, 0.001f)
    }

    @Test
    fun testYawWrapAround_359To0_calculatesCorrectDistance() {
        val yaw1 = 359f
        val yaw2 = 1f

        var diff = Math.abs(yaw1 - yaw2) % 360f
        if (diff > 180f) diff = 360f - diff

        // Angular distance across the 360°/0° meridian should be 2 degrees
        assertEquals(2f, diff, 0.001f)
    }

    @Test
    fun testYawWrapAround_180ToNegative180_calculatesCorrectDistance() {
        val yaw1 = 179f
        val yaw2 = -179f

        var diff = Math.abs(yaw1 - yaw2) % 360f
        if (diff > 180f) diff = 360f - diff

        assertEquals(2f, diff, 0.001f)
    }
}
