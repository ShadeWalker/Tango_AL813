
package com.android.musicvis.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.musicvis.vis2.Visualization2;

public class MusicVis2Test extends ServiceTestCase<Visualization2> {

    public MusicVis2Test() {
        super(Visualization2.class);
    }

    @SmallTest
    public void startService() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), Visualization2.class);
        startService(startIntent);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), Visualization2.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
