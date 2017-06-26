
package com.android.wallpaper.livepicker.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.KeyEvent;

import com.android.wallpaper.livepicker.LiveWallpaperActivity;

public class LiveWallpaperActivityTest extends
        ActivityInstrumentationTestCase2<LiveWallpaperActivity> {
    private LiveWallpaperActivity mLwpList;
    private Instrumentation mInstr;
    private Context mContext;

    public LiveWallpaperActivityTest() {
        super(LiveWallpaperActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mInstr = getInstrumentation();
        mContext = mInstr.getContext();
        mLwpList = getActivity();
        Log.d("setUp", "start");
    }

    @Override
    public void tearDown() throws Exception {
        if (mLwpList != null) {
            mLwpList.finish();
            mLwpList = null;
        }
        Log.d("tearDown", "pass");
        super.tearDown();
    }

    @MediumTest
    public void testPreconditions() {
        assertNotNull(mLwpList);
        assertNotNull(mContext);
        assertNotNull(mInstr);
    }

    @MediumTest
    public void testOnItemClick() throws Exception {

        int x = 100 + 120;
        int y = 100 + 30;
        Log.d("LOC", "LOC " + String.valueOf(x));
        Log.d("LOC", "LOC " + String.valueOf(y));
        TestUtils.tapAtPoint(mInstr, x, y);

        Thread.sleep(3000);
        mInstr.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        Thread.sleep(2000);
    }

    @MediumTest
    public void testDragAndSetWallpaper() throws Exception {

        int x = 150 + 130;
        int y = 150 + 40;

        TestUtils.drag(mInstr, x, x, y, y + 100, 5);
        Thread.sleep(3000);
        TestUtils.tapAtPoint(mInstr, x, y);
        Thread.sleep(3000);
        mInstr.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(2000);
        mInstr.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        Thread.sleep(3000);
    }
}
