
package com.android.phasebeam.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class PhaseBeamTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(PhaseBeamStubTest.class);
        return suite;
    }
}
