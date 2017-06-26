
package com.android.wallpaper.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.wallpaper.fall.Fall;

public class FallActivityTest extends ActivityInstrumentationTestCase2<Fall> {
    private static final String TAG = "FallActivityTest";
    private static final int SLEEP_TIME = 2000;
    private Fall mLwp;
    private Context mContext;
    private Instrumentation mInstr;

    public FallActivityTest() {
        super(Fall.class);
    }

    @Override
    public void setUp() throws Exception {
        Log.d(TAG, TAG + " SetUP");
        super.setUp();
        mInstr = getInstrumentation();
        mContext = mInstr.getTargetContext();
        //mLwp = getActivity();  // process will be crashed here
    }

    @Override
    public void tearDown() throws Exception {
        Log.d(TAG, TAG + " tearDown");
        if (mLwp != null) {
            mLwp.finish();
            mLwp = null;
        }
        super.tearDown();
    }

    @MediumTest
    public void testCase1FallActivityPrecondition() {
        assertNotNull(mInstr);
        assertNotNull(mContext);
        //assertNotNull(mLwp);
        Log.d(TAG, TAG + " Fall");
        try {
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {

        }
    }

}
