
package com.android.musicvis.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.musicvis.vis5.Visualization5;

public class MusicVis5Test extends ServiceTestCase<Visualization5> {

    public MusicVis5Test() {
        super(Visualization5.class);
    }

    @SmallTest
    public void startService() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), Visualization5.class);
        startService(startIntent);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), Visualization5.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
