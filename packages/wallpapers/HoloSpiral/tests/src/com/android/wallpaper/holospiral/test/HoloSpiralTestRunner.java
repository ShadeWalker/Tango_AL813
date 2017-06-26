
package com.android.wallpaper.holospiral.test;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

public class HoloSpiralTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(HoloSpiralTest.class);

        suite.addTestSuite(HoloSpiralStubTest.class);
        return suite;
    }
}
