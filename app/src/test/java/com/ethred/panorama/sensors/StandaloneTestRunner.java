package com.ethred.panorama.sensors;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class StandaloneTestRunner {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   ETHRED 360 PANORAMA - DIRECT TEST RUNNER      ");
        System.out.println("=================================================");
        System.out.println("Running Unit Tests...");

        Result result = JUnitCore.runClasses(
            SensorOrientationProcessorTest.class,
            CaptureGridManagerTest.class
        );

        System.out.println("\n-------------------------------------------------");
        System.out.println("TEST RESULTS SUMMARY:");
        System.out.println("Total Tests Executed: " + result.getRunCount());
        System.out.println("Failed Tests: " + result.getFailureCount());
        System.out.println("Ignored Tests: " + result.getIgnoreCount());
        System.out.println("Execution Time: " + result.getRunTime() + " ms");
        System.out.println("-------------------------------------------------");

        if (result.wasSuccessful()) {
            System.out.println("\n✅ SUCCESS: ALL UNIT TESTS PASSED CLEANLY!");
        } else {
            System.out.println("\n❌ FAILURES DETECTED:");
            for (Failure failure : result.getFailures()) {
                System.out.println(" -> " + failure.toString());
                System.out.println("    " + failure.getTrace());
            }
        }
        System.out.println("=================================================");
    }
}
