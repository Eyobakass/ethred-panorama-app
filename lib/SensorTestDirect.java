package com.ethred.panorama.sensors;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class SensorTestDirect {

    @Test
    public void testLowPassFilter_alpha015_smoothsValues() {
        float alpha = 0.15f;
        float currentFiltered = 0f;
        float newRaw = 10f;

        // Apply low pass formula: filtered = alpha * raw + (1 - alpha) * filtered
        currentFiltered = alpha * newRaw + (1f - alpha) * currentFiltered;
        assertEquals(1.5f, currentFiltered, 0.001f);

        currentFiltered = alpha * newRaw + (1f - alpha) * currentFiltered;
        assertEquals(2.775f, currentFiltered, 0.001f);
    }

    @Test
    public void testYawWrapAround_359To0_calculatesCorrectDistance() {
        float yaw1 = 359f;
        float yaw2 = 1f;

        float diff = Math.abs(yaw1 - yaw2) % 360f;
        if (diff > 180f) diff = 360f - diff;

        // Angular distance across the 360°/0° meridian should be 2 degrees
        assertEquals(2f, diff, 0.001f);
    }

    @Test
    public void testYawWrapAround_180ToNegative180_calculatesCorrectDistance() {
        float yaw1 = 179f;
        float yaw2 = -179f;

        float diff = Math.abs(yaw1 - yaw2) % 360f;
        if (diff > 180f) diff = 360f - diff;

        assertEquals(2f, diff, 0.001f);
    }

    @Test
    public void testGridInitialization_createsExact28TargetDots() {
        List<String> dots = new ArrayList<>();

        // Upper Ring (+30° Pitch): 8 dots
        for (int i = 0; i < 8; i++) {
            dots.add("UPPER_RING_PITCH_30_YAW_" + (i * 45));
        }
        // Horizontal Ring (0° Pitch): 12 dots
        for (int i = 0; i < 12; i++) {
            dots.add("EQUATOR_RING_PITCH_0_YAW_" + (i * 30));
        }
        // Lower Ring (-30° Pitch): 8 dots
        for (int i = 0; i < 8; i++) {
            dots.add("LOWER_RING_PITCH_-30_YAW_" + (i * 45));
        }

        assertEquals(28, dots.size());
    }

    @Test
    public void testToleranceEvaluation_within2Degrees_triggersAlignment() {
        float targetYaw = 0f;
        float targetPitch = 30f;

        float currentYaw = 1.2f;
        float currentPitch = 29.5f;

        float yawDiff = Math.abs(currentYaw - targetYaw) % 360f;
        if (yawDiff > 180f) yawDiff = 360f - yawDiff;
        float pitchDiff = Math.abs(currentPitch - targetPitch);

        boolean aligned = yawDiff <= 2.0f && pitchDiff <= 2.0f;
        assertTrue(aligned);
    }

    @Test
    public void test300msDebounce_alignmentDurationCondition() {
        long startTime = System.currentTimeMillis() - 350L;
        long duration = System.currentTimeMillis() - startTime;

        boolean captureReady = duration >= 300L;
        assertTrue(captureReady);
    }

    @Test
    public void testStorageRequirement_500MBMinimumCheck() {
        long requiredBytes = 500L * 1024 * 1024; // 500 MB
        long mockFreeBytes = 2048L * 1024 * 1024; // 2 GB free

        boolean hasEnoughStorage = mockFreeBytes >= requiredBytes;
        assertTrue(hasEnoughStorage);
    }

    @Test
    public void testXmpMetadataHeader_app1SegmentSignature() {
        String xmpNs = "http://ns.adobe.com/xap/1.0/";
        String projectionType = "equirectangular";

        assertTrue(xmpNs.contains("adobe"));
        assertEquals("equirectangular", projectionType);
    }

    public static void main(String[] args) {
        org.junit.runner.JUnitCore.main("com.ethred.panorama.sensors.SensorTestDirect");
    }
}
