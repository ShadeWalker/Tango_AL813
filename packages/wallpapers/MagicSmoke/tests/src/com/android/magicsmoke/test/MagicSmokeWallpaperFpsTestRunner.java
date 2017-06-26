package com.android.magicsmoke.test;

import android.test.InstrumentationTestRunner;
import junit.framework.TestSuite;

public class MagicSmokeWallpaperFpsTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(MagicSmokeWallpaperFpsTest.class);
        return testSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return MagicSmokeWallpaperFpsTestRunner.class.getClassLoader();
    }
}