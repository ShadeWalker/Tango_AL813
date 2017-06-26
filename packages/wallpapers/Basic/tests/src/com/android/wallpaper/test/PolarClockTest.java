
package com.android.wallpaper.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.wallpaper.polarclock.PolarClockWallpaper;

public class PolarClockTest extends ServiceTestCase<PolarClockWallpaper> {

    public PolarClockTest() {
        super(PolarClockWallpaper.class);
    }

    @SmallTest
    public void startService() {
        Intent si = new Intent();
        si.setClass(getContext(), PolarClockWallpaper.class);
        startService(si);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), PolarClockWallpaper.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
