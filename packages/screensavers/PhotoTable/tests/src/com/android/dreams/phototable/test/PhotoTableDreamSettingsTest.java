package com.android.dreams.phototable.test;

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.CheckBox;

import com.android.dreams.phototable.PhotoTableDreamSettings;
import com.jayway.android.robotium.solo.Solo;

import java.util.ArrayList;

public class PhotoTableDreamSettingsTest extends
        ActivityInstrumentationTestCase2<PhotoTableDreamSettings> {

    private static final String TAG = PhotoTableDreamSettingsTest.class
            .getSimpleName();
    private Context mContext;
    private Instrumentation mInstrumentation;
    private PhotoTableDreamSettings mStubActivity;
    private Solo mSolo;

    public PhotoTableDreamSettingsTest() {
        super(PhotoTableDreamSettings.class);
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

    private void precondition() {
        assertNotNull(mInstrumentation);
        assertNotNull(mContext);
        assertNotNull(mStubActivity);
        assertNotNull(mSolo);
    }

}
