
package com.android.wallpaper.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.wallpaper.grass.GrassWallpaper;

public class GrassTest extends ServiceTestCase<GrassWallpaper> {

    public GrassTest() {
        super(GrassWallpaper.class);
    }

    @SmallTest
    public void startService() {
        Intent si = new Intent();
        si.setClass(getContext(), GrassWallpaper.class);
        startService(si);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), GrassWallpaper.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
