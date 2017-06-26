
package com.android.magicsmoke.test;

import android.app.Activity;
import android.os.Bundle;
import android.test.LaunchPerformanceBase;

public class MagicSmokeLaunchPerformance extends LaunchPerformanceBase {

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        mIntent.setClassName(getTargetContext(), "com.android.magicsmoke.MagicSmokeSelector");
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
