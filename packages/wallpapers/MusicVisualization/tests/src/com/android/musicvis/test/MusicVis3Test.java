
package com.android.musicvis.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.musicvis.vis3.Visualization3;

public class MusicVis3Test extends ServiceTestCase<Visualization3> {

    public MusicVis3Test() {
        super(Visualization3.class);
    }

    @SmallTest
    public void startService() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), Visualization3.class);
        startService(startIntent);
    }

    @MediumTest
    public void bindService() {
        Intent si = new Intent();
        si.setClass(getContext(), Visualization3.class);
        IBinder ib = bindService(si);
        assertNotNull(ib);
    }

}
