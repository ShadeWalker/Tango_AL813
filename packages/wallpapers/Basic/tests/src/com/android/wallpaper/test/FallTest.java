
package com.android.wallpaper.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.wallpaper.fall.FallWallpaper;

public class FallTest extends ServiceTestCase<FallWallpaper> {

    public FallTest() {
        super(FallWallpaper.class);
    }

    @SmallTest
    public void startService() {
        Intent si = new Intent();
        si.setClass(getContext(), FallWallpaper.class);
        startService(si);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), FallWallpaper.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
