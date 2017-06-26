
package com.android.magicsmoke.test;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import junit.framework.TestSuite;

public class MagicSmokeInstrumentationTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        InstrumentationTestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(MagicSmokeSelectorTest.class);
        suite.addTestSuite(MagicSmokeTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return MagicSmokeInstrumentationTestRunner.class.getClassLoader();
    }

}
