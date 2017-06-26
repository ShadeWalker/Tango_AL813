package com.android.wallpaper.holospiral.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class HoloSpiralFpsTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(HoloSpiralFpsTest.class);
        return testSuite;
    }

}
