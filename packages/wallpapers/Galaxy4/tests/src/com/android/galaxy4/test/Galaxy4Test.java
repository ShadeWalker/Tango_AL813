
package com.android.galaxy4.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.galaxy4.Galaxy4;

public class Galaxy4Test extends ActivityInstrumentationTestCase2<Galaxy4> {
    private Galaxy4 mG4;
    private Context mContext;
    private Instrumentation mInstrumentation;

    public Galaxy4Test() {
        super(Galaxy4.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mG4 = getActivity();

    }

    @Override
    protected void tearDown() throws Exception {
        if (mG4 != null) {
            mG4.finish();
            mG4 = null;
        }
        super.tearDown();

    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     */
    @MediumTest
    public void testCase1StartActivity() {
        assertNotNull("activity should be launched successfully", mG4);
        assertNotNull(mInstrumentation);
        assertNotNull(mContext);

    }
}
