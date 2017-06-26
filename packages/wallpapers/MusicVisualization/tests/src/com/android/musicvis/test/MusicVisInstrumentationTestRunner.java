
package com.android.musicvis.test;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import junit.framework.TestSuite;

public class MusicVisInstrumentationTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        InstrumentationTestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(MusicVis3Test.class);
        suite.addTestSuite(MusicVis3StubTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return MusicVisInstrumentationTestRunner.class.getClassLoader();
    }

}
