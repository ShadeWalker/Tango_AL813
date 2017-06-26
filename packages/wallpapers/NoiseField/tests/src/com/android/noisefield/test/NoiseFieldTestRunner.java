
package com.android.noisefield.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class NoiseFieldTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(NoiseFieldStubTest.class);
        return suite;
    }
}
