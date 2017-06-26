
package com.android.musicvis.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.musicvis.vis4.Visualization4;

public class MusicVis4Test extends ServiceTestCase<Visualization4> {

    public MusicVis4Test() {
        super(Visualization4.class);
    }

    @SmallTest
    public void startService() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), Visualization4.class);
        startService(startIntent);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), Visualization4.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
