package com.mediatek.dialer;

import com.mediatek.dialer.activities.CallDetailActivityTest;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivityTest;

import junit.framework.TestSuite;
import android.test.InstrumentationTestRunner;

public class CallLogRegressionTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite testSuite = new TestSuite();
        // CallLog multi-delete
        testSuite.addTestSuite(CallLogMultipleDeleteActivityTest.class);
        testSuite.addTestSuite(CallDetailActivityTest.class);

        return testSuite;
    }
}
