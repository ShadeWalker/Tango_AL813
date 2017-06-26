package com.android.musicvis.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class MusicVisualizationFpsTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(MusicVisualizationFpsTest.class);
        return testSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return MusicVisualizationFpsTestRunner.class.getClassLoader();
    }
}
