
package com.android.galaxy4.test;

import android.app.Activity;
import android.os.Bundle;
import android.test.LaunchPerformanceBase;

public class Galaxy4LaunchPerformance extends LaunchPerformanceBase {

    private static final String TAG = "Galaxy4StubTest";
    private static final String TARGET_PACKAGE = "com.android.galaxy4";
    private static final String TARGET_PREVIEW_CLASS = "com.android.galaxy4.PreviewStubActivity";
    private static final String TARGET_WALLPAPER_CLASS = "com.android.galaxy4.Galaxy4Wallpaper";

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
