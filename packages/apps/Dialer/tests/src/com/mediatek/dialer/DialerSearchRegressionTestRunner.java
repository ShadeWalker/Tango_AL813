package com.mediatek.dialer;

import com.mediatek.dialer.dialersearch.DialerSearchAutoTest;

import android.test.InstrumentationTestRunner;


import junit.framework.TestSuite;

public class DialerSearchRegressionTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new TestSuite();

//        suite.addTestSuite(PrepareDialerSearchPerformanceAutoTest.class);
        suite.addTestSuite(DialerSearchAutoTest.class);
        return suite;
    }
}

