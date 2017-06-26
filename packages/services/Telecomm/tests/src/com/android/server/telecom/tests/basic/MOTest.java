
package com.android.server.telecom.tests.basic;
import android.content.Context;
import android.test.InstrumentationTestCase;

import com.android.server.telecom.AddCallCommand;
import com.android.server.telecom.AutotestEngine;
import com.android.server.telecom.AutotestEngineUtils;
import com.android.server.telecom.Utils;
import com.android.server.telecom.tests.annotation.InternalApiAnnotation;


public class MOTest extends InstrumentationTestCase {

    private static final String TAG = "MOTest";
    AutotestEngine mAutotestEngine;
    Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        log("setUp");
        mAutotestEngine = AutotestEngine.makeInstance(getInstrumentation());
        mContext = getInstrumentation().getTargetContext();
        getInstrumentation().waitForIdleSync();
    };

    @Override
    protected void tearDown() throws Exception {
        log("tearDown");
        super.tearDown();
        // End the call.
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("End 3"));
    }

    @InternalApiAnnotation
    public void test01_placeCall() throws Exception {
        log("test01_callBySlot0");
        int result = mAutotestEngine.execute(AddCallCommand.FIRST_CALL_USING_SUB1);
        AutotestEngineUtils.assertAndWaitSync(result, true);
    }

    void log(String msg) {
        Utils.log(TAG, msg);
    }
}
