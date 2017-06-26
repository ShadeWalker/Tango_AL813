
package com.android.magicsmoke.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import com.android.magicsmoke.MagicSmokeSelector;

public class MagicSmokeSelectorTest extends ActivityInstrumentationTestCase2<MagicSmokeSelector> {
    private MagicSmokeSelector mMs;
    private Context mContext;
    private Instrumentation mInstr;

    public MagicSmokeSelectorTest() {
        super(MagicSmokeSelector.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstr = getInstrumentation();
        mContext = mInstr.getTargetContext();
        mMs = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mMs != null) {
            mMs.finish();
            mMs = null;
        }
        super.tearDown();
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this test
     * doesn't pass, the test case was not set up properly and it might explain
     * any and all failures in other tests. This is not guaranteed to run before
     * other tests, as junit uses reflection to find the tests.
     */
    @MediumTest
    public void testPreconditions() {
        assertNotNull(mMs);
        assertNotNull(mInstr);
        assertNotNull(mContext);
    }

    /**
     * Verifies that activity under test can be launched.
     */
    @SmallTest
    public void testActivityTestCaseSetUpProperly() {
        assertNotNull("Activity should be launched successfully", getActivity());
    }

    @MediumTest
    public void testSettings() throws InterruptedException {
        // Button btn = (Button)ms.findViewByID(R.layout.ok);
        // assertNotNull("press ok/to set MagicSmoke wallpaper",btn);
        mInstr.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(1000);
        mInstr.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        Thread.sleep(2000);
    }

    @MediumTest
    public void testTap() throws InterruptedException {
        int x = mMs.getWallpaper().getMinimumWidth() / 2;
        int y = mMs.getWallpaper().getMinimumHeight() / 2;
        TestUtils.tapAtPoint(mInstr, x, y);
        Thread.sleep(1000);
    }

}
