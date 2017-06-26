
package com.android.galaxy4.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class Galaxy4TestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(Galaxy4Test.class);
        suite.addTestSuite(Galaxy4StubTest.class);
        return suite;
    }
}
