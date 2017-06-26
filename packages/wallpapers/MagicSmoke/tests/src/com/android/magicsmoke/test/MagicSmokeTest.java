
package com.android.magicsmoke.test;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.magicsmoke.MagicSmoke;

public class MagicSmokeTest extends ServiceTestCase<MagicSmoke> {

    public MagicSmokeTest() {
        super(MagicSmoke.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    public void testPreconditions() {
    }

    @SmallTest
    public void testStartable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), MagicSmoke.class);
        startService(startIntent);
    }

    @MediumTest
    public void testBindable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), MagicSmoke.class);
        IBinder service = bindService(startIntent);
        assertNotNull(service);
    }
}
