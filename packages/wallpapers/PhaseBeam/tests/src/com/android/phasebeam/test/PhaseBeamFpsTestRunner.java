package com.android.phasebeam.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class PhaseBeamFpsTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(PhaseBeamFpsTest.class);
        return testSuite;
    }

}
