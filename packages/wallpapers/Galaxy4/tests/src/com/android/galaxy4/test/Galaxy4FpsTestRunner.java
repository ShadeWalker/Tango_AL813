package com.android.galaxy4.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class Galaxy4FpsTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(Galaxy4FpsTest.class);
        return testSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return Galaxy4FpsTestRunner.class.getClassLoader();
    }
}
