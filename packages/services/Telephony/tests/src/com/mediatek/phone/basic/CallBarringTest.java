package com.mediatek.phone.basic;

import android.app.Instrumentation;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.phone.CallFeaturesSetting;
import com.android.phone.PhoneUtils;

/**
 * class to test call barring feature.
 */
public class CallBarringTest extends ActivityInstrumentationTestCase2<CallFeaturesSetting> {
    private static final String TAG = "CallBarringTest";
    private static final String PASSWORD = "1234";
    private static final String PASSWORD1 = "0000";
    private boolean mIsDone = false;
    private boolean mResult = false;
    private Instrumentation mInstrumentation;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    MyHandler mHandler = new MyHandler();
    private Phone mPhone;
    private int mIndex = 0;

    private static final String[] FACILITYS = {CommandsInterface.CB_FACILITY_BAOC,
        CommandsInterface.CB_FACILITY_BAOIC, CommandsInterface.CB_FACILITY_BAOICxH,
        CommandsInterface.CB_FACILITY_BAIC, CommandsInterface.CB_FACILITY_BAICr,
        CommandsInterface.CB_FACILITY_BA_ALL};

    /**
     * constructor.
     */
    public CallBarringTest() {
        super(CallFeaturesSetting.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
    }

    /**
     * prepare the test environment.
     */
    private void lunchCallBarringSetting() {
        int[] list = SubscriptionManager.from(
                mInstrumentation.getContext()).getActiveSubscriptionIdList();
        Log.d(TAG, "lunchCallBarringSetting: length =" + list.length);
        if (list.length > 0) {
            mSubId = list[0];
            Log.d(TAG, "lunchCallBarringSetting: mSubId =" + mSubId);
            mPhone = PhoneUtils.getPhoneUsingSubId(mSubId);
        }
    }

    /**
     * get Call Barring State.
     */
    private void getCallBarringState() {
        Log.d(TAG, "getCallBarringState: mIndex =" + mIndex);
        Message message = mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALLBARRING_STATE);
        mPhone.getFacilityLock(FACILITYS[mIndex], "", message);
    }

    /**
     * set Call Barring State.
     * @param index to show the facility
     * @param isChecked the state is enable or not
     */
    private void setCallBarringState(int index, boolean isChecked) {
        Log.d(TAG, "setCallBarringState: index =" + index);
        Message message = mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALLBARRING_STATE);
        mPhone.setFacilityLock(FACILITYS[index], isChecked, PASSWORD, message);
    }

    /**
     * change Call Barring Password.
     * @param oldPassword Call Barring Password
     * @param newPassword Call Barring new Password
     */
    private void changeBarringPassword(String oldPassword, String newPassword) {
        Log.d(TAG, "changeBarringPassword");
        Message message = mHandler.obtainMessage(MyHandler.MESSAGE_CALLBARRING_CHANGE_PASSWORD);
        mPhone.changeBarringPassword(
                CommandsInterface.CB_FACILITY_BA_ALL, oldPassword, newPassword, message);
    }

    /**
     * test get Call Barring State.
     */
    public void test_ScreenDisplay() {
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test_ScreenDisplay: mIndex =" + mIndex);
                return SubscriptionManager.from(
                        mInstrumentation.getContext()).getActiveSubscriptionIdList().length > 0;
            }
        }, 20, 200);
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        for (; mIndex < FACILITYS.length - 1; mIndex++) {
            mIsDone = false;
            mResult = false;
            getCallBarringState();
            TestUtils.waitUntil(TAG, new TestUtils.Condition() {
                @Override
                public boolean isMet() {
                    Log.d(TAG, "getCallBarringState: mIndex =" + mIndex);
                    return mIsDone;
                }
            }, 30, 500);
        }
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BAOC Call Barring State.
     */
    public void test01_BAOC() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(0, true);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test01_BAOC: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BAOIC Call Barring State.
     */
    public void test02_BAOIC() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(1, true);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test02_BAOIC: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BAOICxH Call Barring State.
     */
    public void test03_BAOICxH() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(2, true);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test03_BAOICxH: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BAIC Call Barring State.
     */
    public void test04_BAIC() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(3, true);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test04_BAIC: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BAICr Call Barring State.
     */
    public void test05_BAICr() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(4, true);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test05_BAICr: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set BA_ALL Call Barring State.
     */
    public void test06_BA_ALL() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        setCallBarringState(5, false);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test06_BA_ALL: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    /**
     * test set Change Password Call Barring.
     */
    public void test07_ChangePassword() {
        lunchCallBarringSetting();
        assertNotNull(mPhone);
        mIsDone = false;
        mResult = false;
        changeBarringPassword(PASSWORD, PASSWORD1);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test07_ChangePassword: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
        mIsDone = false;
        mResult = false;
        changeBarringPassword(PASSWORD1, PASSWORD);
        TestUtils.waitUntil(TAG, new TestUtils.Condition() {
            @Override
            public boolean isMet() {
                Log.d(TAG, "test07_ChangePassword1: mIsDone =" + mIsDone);
                return mIsDone;
            }
        }, 30, 500);
        assertTrue(mIsDone);
        assertTrue(mResult);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * handler of Call Barring Response.
     */
    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_CALLBARRING_STATE = 0;
        private static final int MESSAGE_SET_CALLBARRING_STATE = 1;
        private static final int MESSAGE_CALLBARRING_CHANGE_PASSWORD = 2;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_GET_CALLBARRING_STATE:
                handleGetCallBarringResponse(msg);
                break;
            case MESSAGE_SET_CALLBARRING_STATE:
                handleSetCallBarringResponse(msg);
                break;
            case MESSAGE_CALLBARRING_CHANGE_PASSWORD:
                handleChangePasswordResponse(msg);
                break;
            default:
                break;
            }
        }

        /**
         * handle get Call Barring Response.
         * @param msg message return
         */
        private void handleGetCallBarringResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            mIsDone = true;
            Log.d(TAG, "handleGetCallBarringResponse: ar.exception=" + ar.exception);
            if (ar.exception != null || ar.result == null || ((int[]) ar.result).length <= 0) {
                mResult = false;
            } else {
                mResult = true;
            }
        }

        /**
         * handle set Call Barring Response.
         * @param msg message return
         */
        private void handleSetCallBarringResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            mIsDone = true;
            Log.d(TAG, "handleSetCallBarringResponse: ar.exception=" + ar.exception);
            if (ar.exception != null) {
                mResult = false;
            } else {
                mResult = true;
            }
        }

        /**
         * handle change password Call Barring Response.
         * @param msg message return
         */
        private void handleChangePasswordResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            mIsDone = true;
            Log.d(TAG, "handleSetCallBarringResponse: ar.exception=" + ar.exception);
            if (ar.exception != null || (ar.userObj instanceof Throwable)) {
                mResult = false;
            } else {
                mResult = true;
            }
        }
    }
}
