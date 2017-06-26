package com.android.wallpaper.test;

import android.test.InstrumentationTestRunner;
import junit.framework.TestSuite;

public class NexusWallpaperFpsTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(NexusWallpaperFpsTest.class);
        return testSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return NexusWallpaperFpsTestRunner.class.getClassLoader();
    }
}