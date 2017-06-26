package com.android.server.telecom.tests.basic;

import android.test.InstrumentationTestCase;

import com.android.server.telecom.AddCallCommand;
import com.android.server.telecom.AutotestEngine;
import com.android.server.telecom.AutotestEngineUtils;
import com.android.server.telecom.Utils;
import com.android.server.telecom.tests.annotation.InternalApiAnnotation;

public class CallButtonTest extends InstrumentationTestCase {

    private static final String TAG = "CallButtonTest";
    AutotestEngine mAutotestEngine;

    protected void setUp() throws Exception {
        super.setUp();
        log("setUp start");
        mAutotestEngine = AutotestEngine.makeInstance(getInstrumentation());
        getInstrumentation().waitForIdleSync();
        // Add a call.
        int result = mAutotestEngine.execute(AddCallCommand.FIRST_CALL_USING_SUB1);
        AutotestEngineUtils.assertAndWaitSync(result, true);
    };

    @Override
    protected void tearDown() throws Exception {
        log("tearDown");
        // End the call.
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("End 3"));
        super.tearDown();
    }

    @InternalApiAnnotation
    public void test01_mute() throws InterruptedException {
        log("test01_mute");
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Mute"));
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Mute"));
    }

    @InternalApiAnnotation
    public void test02_speaker() throws InterruptedException {
        log("test02_speaker");
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Speaker"));
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Speaker"));
    }

    @InternalApiAnnotation
    public void test03_hold() {
        log("test03_hold");
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Hold"));
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Hold"));
    }

    @InternalApiAnnotation
    public void test04_add() {
        log("test03_hold");
        addSecondCallUsingSub1();
    }

    @InternalApiAnnotation
    public void test05_swap() throws InterruptedException {
        log("test04_swap");
        addSecondCallUsingSub1();
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Swap"));
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Swap"));
    }

/*    @InternalApiAnnotation
    public void test06_merge() throws InterruptedException {
        log("test05_merge");
        addSecondCall();
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Merge"));
    }*/

    //@InternalApiAnnotation
/*    public void test06_seperate() throws InterruptedException {
        log("test06_seperate");
        addSecondCall();
        int result = mAutotestEngine.execute("Merge");
        AutotestEngineUtils.assertAndWaitSync(result);
        if(result != ICommand.RESULT_COMMAND_NOT_SUPPORT) {
            AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Separate 0"));
            AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Merge"));
            AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("Separate 1"));
        }
    }*/

    private void addSecondCallUsingSub1() {
        int result = mAutotestEngine.execute(AddCallCommand.SECOND_CALL_USING_SUB1);
        AutotestEngineUtils.assertAndWaitSync(result, true);
    }
    private void log(String msg) {
        Utils.log(TAG, msg);
    }

}
