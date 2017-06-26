
package com.android.magicsmoke.test;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.magicsmoke.MagicSmokeSelector;

public class MagicSmokeWallpaperFpsTest extends ActivityInstrumentationTestCase2<MagicSmokeSelector> {

    private static final int FPS_TEST_PERIOD = 20000;
    private MagicSmokeSelector mMs;

    public MagicSmokeWallpaperFpsTest() {
        super(MagicSmokeSelector.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
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

    @MediumTest
    public void testWallpaperFps() {
        assertNotNull(mMs);
        assertTrue(mMs.isResumed());
        try {
            Thread.sleep(FPS_TEST_PERIOD);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertNotNull(mMs);
        assertTrue(mMs.isResumed());
    }
}
