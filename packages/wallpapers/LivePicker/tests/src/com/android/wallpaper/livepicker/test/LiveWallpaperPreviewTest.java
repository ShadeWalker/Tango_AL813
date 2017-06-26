
package com.android.wallpaper.livepicker.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.wallpaper.WallpaperService;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.KeyEvent;

import com.android.wallpaper.livepicker.LiveWallpaperPreview;

public class LiveWallpaperPreviewTest extends
        ActivityInstrumentationTestCase2<LiveWallpaperPreview> {

    private static final String TARGET_PACKAGE = "com.android.wallpaper.livepicker";
    private static final String TARGET_CLASS = ".LiveWallpaperPreview";
    private static final String TARGET_WALLPAPER_PKG = "com.android.galaxy4";
    private static final String TARGET_WALLPAPER_CLASS = "com.android.galaxy4.Galaxy4Wallpaper";

    private static final String EXTRA_LIVE_WALLPAPER_INTENT = "android.live_wallpaper.intent";
    private static final String EXTRA_LIVE_WALLPAPER_PACKAGE = "android.live_wallpaper.package";

    private Activity mActivity;
    private Context mContext;
    private Instrumentation mInstrumentation;
    private Intent mIntent;
    private WallpaperManager mWallpaperManager;

    public LiveWallpaperPreviewTest() {
        super(LiveWallpaperPreview.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getTargetContext();

        Intent mWallpaperIntent = new Intent(Intent.ACTION_MAIN);
        mWallpaperIntent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_CLASS));

        mIntent = new Intent(WallpaperService.SERVICE_INTERFACE);
        mIntent.setComponent(new ComponentName(TARGET_WALLPAPER_PKG, TARGET_WALLPAPER_CLASS));

        mWallpaperIntent.putExtra(EXTRA_LIVE_WALLPAPER_INTENT, mIntent);
        mWallpaperIntent.putExtra(EXTRA_LIVE_WALLPAPER_PACKAGE, TARGET_WALLPAPER_PKG);

        setActivityIntent(mWallpaperIntent);
        mActivity = getActivity();

        mWallpaperManager = WallpaperManager.getInstance(mContext);

    }

    @Override
    public void tearDown() throws Exception {

        if (mActivity != null) {
            mActivity.finish();
            mActivity = null;
        }
        super.tearDown();

    }

    @MediumTest
    public void testCase1SetLiveWallpaper() {
        // some action to click Set Wallpaper button;
        mInstrumentation.sendKeyDownUpSync(KeyEvent.ACTION_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String curClassName = mWallpaperManager.getWallpaperInfo().getServiceName();
        String curPackageName = mWallpaperManager.getWallpaperInfo().getPackageName();
        assertEquals(TARGET_WALLPAPER_CLASS, curClassName);
        assertEquals(TARGET_WALLPAPER_PKG, curPackageName);
    }

}
