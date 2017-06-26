
package com.mediatek.dialer.dialersearch;

import android.app.ActionBar;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.widget.SearchEditTextLayout;
import com.android.phone.common.dialpad.DialpadKeyButton;
import com.jayway.android.robotium.solo.Solo;
import com.mediatek.dialer.util.TestUtils;

import java.util.ArrayList;


public class DialerSearchAutoTest extends ActivityInstrumentationTestCase2<DialtactsActivity> {

    private static final String TAG = "DialerSearchAutoTest";
    private static final int ONE_SECOND = 1000;
    private static final int HALF_SECOND = 500;

    private DialtactsActivity mMainActivity;
    private Solo mSolo;
    private Instrumentation mInstr;
    private ContentResolver mResolver;
    private boolean mIsDataReady = false;

    public DialerSearchAutoTest() {
         super(DialtactsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.i(TAG, "setUp()");

        mInstr = getInstrumentation();
        mMainActivity = this.getActivity();
        mSolo = new Solo(mInstr, mMainActivity);
        mResolver = mInstr.getTargetContext().getContentResolver();

        TestUtils.createRawContact(mResolver, "中国移动", "13458629789");
        TestUtils.createRawContact(mResolver, "中国移动", "13458629123");
        TestUtils.createRawContact(mResolver, "china mobile", "13458629456");
        TestUtils.createRawContact(mResolver, "移动mobile", "13458629159");

    }

    @Override
    protected void tearDown() throws Exception {
        mSolo.finishOpenedActivities();
        try {
            mSolo.finalize();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        super.tearDown();
    }

    public void test_DialerSearch_001() {
        String tag = "test_DialerSearch_001";

        Log.i(TAG, "test_DialerSearch_001 starts");

        long rawContactId = TestUtils.createRawContact(mResolver, "中国移动", "13458629789");
        Log.d(tag, "rawContactId: " + rawContactId);

        mSolo.sleep(HALF_SECOND);

        final ActionBar actionBar = mMainActivity.getActionBar();
        SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) actionBar.getCustomView();
        mSolo.clickOnView(searchEditTextLayout.findViewById(R.id.search_box_start_search));
        mSolo.sleep(HALF_SECOND);

        final EditText searchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);

        mSolo.sleep(HALF_SECOND);
        mSolo.enterText(searchView, "中国");
        mSolo.sleep(HALF_SECOND);

        if (mSolo.searchText("中国移动", true)) {
            Log.d(tag, "--- success ---");
        } else {
            logCurrentTexts(tag);
            fail();
        }

       // TestUtils.deleteContact(mResolver, rawContactId);
        TestUtils.sleep();
    }

    public void test_DialerSearch_002() {
        String tag = "test_DialerSearch_002";

        Log.i(TAG, "test_DialerSearch_002 starts");

        long rawContactId = TestUtils.createRawContact(mResolver, "中国移动", "13458629123");
        Log.d(tag, "rawContactId: " + rawContactId);

        TestUtils.sleep();

        showDialpad();
        mSolo.sleep(ONE_SECOND);

        //digit 9 indicates character "Z", and "Z" is the first character of hanzi "中"
        DialpadKeyButton mButtonDig9 = (DialpadKeyButton) mSolo.getView(R.id.nine);
        mSolo.clickOnView(mButtonDig9);
        mSolo.sleep(HALF_SECOND);

        //digit 4 indicates character "G", and "G" is the first character of hanzi "国"
        DialpadKeyButton mButtonDig4 = (DialpadKeyButton) mSolo.getView(R.id.four);
        mSolo.clickOnView(mButtonDig4);
        mSolo.sleep(HALF_SECOND);

        //digit 9 indicates character "Y", and "Y" is the first character of hanzi "移"
        mSolo.clickOnView(mButtonDig9);
        mSolo.sleep(HALF_SECOND);

        //digit 3 indicates character "D", and "D" is the first character of hanzi "动"
        DialpadKeyButton mButtonDig3 = (DialpadKeyButton) mSolo.getView(R.id.three);
        mSolo.clickOnView(mButtonDig3);
        mSolo.sleep(HALF_SECOND);

        if (mSolo.searchText("中国移动", true)) {
            Log.d(tag, "--- success ---");
        } else {
            logCurrentTexts(tag);
            fail();
        }

       // TestUtils.deleteContact(mResolver, rawContactId);
        TestUtils.sleep();
    }

    public void test_DialerSearch_003() {
        String tag = "test_DialerSearch_003";

        Log.i(TAG, "test_DialerSearch_003 starts");

        long rawContactId = TestUtils.createRawContact(mResolver, "china mobile", "13458629456");
        Log.d(tag, "rawContactId: " + rawContactId);

        TestUtils.sleep();

        showDialpad();
        mSolo.sleep(ONE_SECOND);

        //digit 2 indicates character "C"
        DialpadKeyButton mButtonDig2 = (DialpadKeyButton) mSolo.getView(R.id.two);
        mSolo.clickOnView(mButtonDig2);
        mSolo.sleep(HALF_SECOND);

        //digit 4 indicates character "H"
        DialpadKeyButton mButtonDig4 = (DialpadKeyButton) mSolo.getView(R.id.four);
        mSolo.clickOnView(mButtonDig4);
        mSolo.sleep(HALF_SECOND);

        //digit 4 indicates character "I"
        mSolo.clickOnView(mButtonDig4);
        mSolo.sleep(HALF_SECOND);

        //digit 6 indicates character "N"
        DialpadKeyButton mButtonDig6 = (DialpadKeyButton) mSolo.getView(R.id.six);
        mSolo.clickOnView(mButtonDig6);
        mSolo.sleep(HALF_SECOND);

        //digit 2 indicates character "A"
        mSolo.clickOnView(mButtonDig2);
        mSolo.sleep(HALF_SECOND);

        if (mSolo.searchText("china mobile", true)) {
            Log.d(tag, "--- success ---");
        } else {
            logCurrentTexts(tag);
            fail();
        }

       // TestUtils.deleteContact(mResolver, rawContactId);
        TestUtils.sleep();
    }

    public void test_DialerSearch_011() {
        String tag = "test_DialerSearch_011";

        Log.i(TAG, "test_DialerSearch_011 starts");

        long rawContactId = TestUtils.createRawContact(mResolver, "移动mobile", "13458629159");
        Log.d(tag, "rawContactId: " + rawContactId);

        TestUtils.sleep();

        showDialpad();
        mSolo.sleep(ONE_SECOND);

        //digit 9 indicates character "C", and "C" means hanzi "移"
        DialpadKeyButton mButtonDig9 = (DialpadKeyButton) mSolo.getView(R.id.nine);
        mSolo.clickOnView(mButtonDig9);
        mSolo.sleep(HALF_SECOND);

        //digit 3 indicates character "D", and "D" means hanzi "动"
        DialpadKeyButton mButtonDig3 = (DialpadKeyButton) mSolo.getView(R.id.three);
        mSolo.clickOnView(mButtonDig3);
        mSolo.sleep(HALF_SECOND);

        //digit 6 indicates character "M"
        DialpadKeyButton mButtonDig6 = (DialpadKeyButton) mSolo.getView(R.id.six);
        mSolo.clickOnView(mButtonDig6);
        mSolo.sleep(HALF_SECOND);

        //digit 6 indicates character "O"
        mSolo.clickOnView(mButtonDig6);
        mSolo.sleep(HALF_SECOND);

        //digit 2 indicates character "B"
        DialpadKeyButton mButtonDig2 = (DialpadKeyButton) mSolo.getView(R.id.two);
        mSolo.clickOnView(mButtonDig2);
        mSolo.sleep(HALF_SECOND);

        //digit 4 indicates character "I"
        DialpadKeyButton mButtonDig4 = (DialpadKeyButton) mSolo.getView(R.id.four);
        mSolo.clickOnView(mButtonDig4);
        mSolo.sleep(HALF_SECOND);

        //digit 5 indicates character "L"
        DialpadKeyButton mButtonDig5 = (DialpadKeyButton) mSolo.getView(R.id.five);
        mSolo.clickOnView(mButtonDig5);
        mSolo.sleep(HALF_SECOND);

        //digit 3 indicates character "E"
        mSolo.clickOnView(mButtonDig3);
        mSolo.sleep(HALF_SECOND);

        if (mSolo.searchText("移动mobile", true)) {
            Log.d(tag, "--- success ---");
        } else {
            logCurrentTexts(tag);
            fail();
        }

       // TestUtils.deleteContact(mResolver, rawContactId);
        TestUtils.sleep();
    }

    private void logCurrentTexts(String tag) {
        ArrayList<TextView> currentTvs = mSolo.getCurrentViews(TextView.class);
        if (currentTvs == null || currentTvs.size() < 1) {
            Log.d(tag, "no TextView found, current Activity is: " + mSolo.getCurrentActivity());
            return;
        }
        ArrayList<String> texts = new ArrayList<String>();
        for (TextView tv : currentTvs) {
            if (!TextUtils.isEmpty(tv.getText()) && tv.getVisibility() == View.VISIBLE) {
                String text = tv.getText().toString();
                if (!tv.isEnabled()) {
                    text = text + "(disabled)";
                }
                texts.add(text);
            }
        }
        Log.d(tag, "current " + texts.size() + " visible texts in " + currentTvs.size() + " TextViews:");

        if (texts.size() < 1) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            sb.append("<").append(text).append(">, ");
        }
        Log.d(tag, "--> " + sb.toString());
    }

    private void showDialpad() {
        if (!mMainActivity.isDialpadShown()) {
			//modified by guofeiyao
//            ImageButton floatingActionButton = (ImageButton)mMainActivity.findViewById(R.id.floating_action_button);
            ImageButton floatingActionButton = (ImageButton)mMainActivity.findViewById(R.id.ib_dialpad);
            mSolo.clickOnView(floatingActionButton);
            Log.d(TAG, "inner--- showDialpad ---");
        }

        Log.d(TAG, "outer--- showDialpad ---");
    }
}
