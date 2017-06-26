package com.android.dreams.phototable.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MenuItem;
import android.widget.CheckBox;

import com.android.dreams.phototable.FlipperDreamSettings;
import com.jayway.android.robotium.solo.Solo;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class FlipperDreamSettingsTest extends
        ActivityInstrumentationTestCase2<FlipperDreamSettings> {

    private static final String TAG = PhotoTableDreamSettingsTest.class
            .getSimpleName();
    private Context mContext;
    private Instrumentation mInstrumentation;
    private FlipperDreamSettings mStubActivity;
    private Solo mSolo;

    public FlipperDreamSettingsTest() {
        super(FlipperDreamSettings.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mStubActivity = getActivity();
        mSolo = new Solo(mInstrumentation, mStubActivity);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mStubActivity != null) {
            mStubActivity.finish();
            mStubActivity = null;
        }
        mSolo.finishOpenedActivities();
        super.tearDown();
    }

    public void testCase01selectAll() {
        precondition();
        MenuItem menu = (MenuItem) ReflectionHelper.getObjectValue(
                FlipperDreamSettings.class, "mSelectAll", mStubActivity);
        assertNotNull(menu);
        mSolo.clickOnText(menu.getTitle().toString());
        mSolo.sleep(1000);
        mSolo.clickOnText(menu.getTitle().toString());
        mSolo.sleep(1000);
    }

    public void testCase02select() {
        precondition();
        mSolo.sleep(1000);
        ArrayList<CheckBox> list = mSolo.getCurrentViews(CheckBox.class);
        if (list.size() != 0) {
            mSolo.clickOnCheckBox(0);
            mSolo.sleep(600);
            if (!list.get(0).isChecked()) {
                mSolo.clickOnCheckBox(0);
                mSolo.sleep(600);
            }
        }
    }

    public void testCase03apology() {
        precondition();
        mSolo.sleep(1000);
        mStubActivity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mStubActivity.getListView().setAdapter(null);
            }
        });
        final Method method;
        try {
            method = FlipperDreamSettings.class.getDeclaredMethod(
                    "showApology", boolean.class);
            method.setAccessible(true);
            mStubActivity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        method.invoke(mStubActivity, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            mSolo.sleep(1000);
            mStubActivity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        method.invoke(mStubActivity, false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            mSolo.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void precondition() {
        assertNotNull(mInstrumentation);
        assertNotNull(mContext);
        assertNotNull(mStubActivity);
        assertNotNull(mSolo);
    }
}
