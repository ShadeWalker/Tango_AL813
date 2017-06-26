package com.android.wallpaper.test;

import android.test.InstrumentationTestRunner;
import junit.framework.TestSuite;

public class FallWallpaperFpsTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(FallWallpaperFpsTest.class);
        return testSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return FallWallpaperFpsTestRunner.class.getClassLoader();
    }
}