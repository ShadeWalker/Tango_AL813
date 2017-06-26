package com.android.noisefield.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class NoiseFieldFpsTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(NoiseFieldFpsTest.class);
        return testSuite;
    }

}
