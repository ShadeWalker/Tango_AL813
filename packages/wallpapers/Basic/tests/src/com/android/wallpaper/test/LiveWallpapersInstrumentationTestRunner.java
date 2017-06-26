
package com.android.wallpaper.test;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import junit.framework.TestSuite;

public class LiveWallpapersInstrumentationTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        InstrumentationTestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(FallTest.class);
        suite.addTestSuite(NexusTest.class);
        suite.addTestSuite(FallActivityTest.class);

        suite.addTestSuite(FallStubTest.class);
        suite.addTestSuite(NexusStubTest.class);

        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return LiveWallpapersInstrumentationTestRunner.class.getClassLoader();
    }
}
