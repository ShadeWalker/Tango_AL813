
package com.android.wallpaper.test;

import android.app.Activity;
import android.os.Bundle;
import android.test.LaunchPerformanceBase;

import com.android.wallpaper.PreviewStubActivity;

public class LiveWallpapersLaunchPerformance extends LaunchPerformanceBase {

    private static final String TAG = "FallStubTest";
    private static final String TARGET_PACKAGE = "com.android.wallpaper";
    private static final String TARGET_PREVIEW_CLASS = "com.android.wallpaper.PreviewStubActivity";
    private static final String TARGET_WALLPAPER_CLASS = "com.android.wallpaper.fall.FallWallpaper";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        mIntent.setClassName(getTargetContext(), TARGET_PREVIEW_CLASS);
        mIntent.putExtra(PreviewStubActivity.PACKAGE_NAME, TARGET_PACKAGE);
        mIntent.putExtra(PreviewStubActivity.CLASS_NAME, TARGET_WALLPAPER_CLASS);
        start();
    }

    /**
     * Calls LaunchApp and finish.
     */
    @Override
    public void onStart() {
        super.onStart();

        LaunchApp();
        finish(Activity.RESULT_OK, mResults);
    }

}
