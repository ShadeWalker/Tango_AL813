
package com.android.wallpaper.livepicker.test;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import junit.framework.TestSuite;

public class LivePickerInstrumentationTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        InstrumentationTestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(LiveWallpaperActivityTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return LivePickerInstrumentationTestRunner.class.getClassLoader();
    }

}
