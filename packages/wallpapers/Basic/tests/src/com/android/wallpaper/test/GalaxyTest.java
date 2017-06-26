
package com.android.wallpaper.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.wallpaper.galaxy.GalaxyWallpaper;

public class GalaxyTest extends ServiceTestCase<GalaxyWallpaper> {

    public GalaxyTest() {
        super(GalaxyWallpaper.class);
    }

    @SmallTest
    public void startService() {
        Intent si = new Intent();
        si.setClass(getContext(), GalaxyWallpaper.class);
        startService(si);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), GalaxyWallpaper.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
