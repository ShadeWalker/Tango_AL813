
package com.android.wallpaper.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.wallpaper.nexus.NexusWallpaper;

public class NexusTest extends ServiceTestCase<NexusWallpaper> {

    public NexusTest() {
        super(NexusWallpaper.class);
    }

    @SmallTest
    public void startService() {
        Intent si = new Intent();
        si.setClass(getContext(), NexusWallpaper.class);
        startService(si);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), NexusWallpaper.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
