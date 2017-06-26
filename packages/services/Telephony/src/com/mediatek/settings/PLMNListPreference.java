/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.settings;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.MobileNetworkSettings;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneGlobals.SubInfoUpdateListener;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.android.phone.TimeConsumingPreferenceActivity;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;
import com.mediatek.phone.TimeConsumingPreferenceListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PLMNListPreference extends TimeConsumingPreferenceActivity
        implements SubInfoUpdateListener {

    private ArrayList<NetworkInfoWithAcT> mPLMNList;
    private int mNumbers = 0;
    private PreferenceScreen mPLMNListContainer;

    private static final String LOG_TAG = "Settings/PLMNListPreference";
    private static final String BUTTON_PLMN_LIST_KEY = "button_plmn_list_key";
    private static final boolean DBG = true;

    private int mSubId = SubscriptionInfoHelper.NO_SUB_ID;
    private Phone mPhone = null;

    private SIMCapability mCapability = new SIMCapability(0, 0, 0, 0);
    private Map<Preference, NetworkInfoWithAcT> mPreferenceMap =
            new LinkedHashMap<Preference, NetworkInfoWithAcT>();
    private NetworkInfoWithAcT mOldInfo;

    private MyHandler mHandler = new MyHandler();

    ArrayList<String> mListPriority = new ArrayList<String>();
    ArrayList<String> mListService = new ArrayList<String>();

    private static final int REQUEST_ADD = 100;
    private static final int REQUEST_EDIT = 200;
    private static final int MENU_ADD = Menu.FIRST;

    private boolean mAirplaneModeEnabled = false;
    private IntentFilter mIntentFilter;

    private boolean mListItemClicked = false;
    private boolean mFirstResume = false;

    private PhoneStateListener mPhoneStateListener;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                finish();
            } else if (action.equals(Intent.ACTION_MSIM_MODE_CHANGED)) {
                setScreenEnabled();
            }
        }
    };

    protected void onCreate(Bundle icicle) {
        setTheme(R.style.Theme_Material_Settings);
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.mtk_plmn_list);
        mPLMNListContainer = (PreferenceScreen) findPreference(BUTTON_PLMN_LIST_KEY);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mPhone = mSubscriptionInfoHelper.getPhone();
        mSubId = mPhone != null ? mPhone.getSubId() : SubscriptionInfoHelper.NO_SUB_ID;

        mIntentFilter = new IntentFilter(
                Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_MSIM_MODE_CHANGED);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in
            // onOptionsItemSelected()
            mSubscriptionInfoHelper.setActionBarTitle(
                    getActionBar(), getResources(), R.string.plmn_list_setting_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        registerReceiver(mReceiver, mIntentFilter);
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        if (!PhoneUtils.isValidSubId(mSubId)) {
            Log.i(LOG_TAG, "mSubId is invalid,activity finish!!!");
            finish();
            return;
        }

        mFirstResume = true;
        registerCallBacks();
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
        mListItemClicked = false;
        setScreenEnabled();
        if (mFirstResume) {
            mFirstResume = false;
            initUi();
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SubscriptionInfoHelper.SUB_ID_EXTRA, mSubId);
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference pref) {
        Log.d(LOG_TAG, "onPreferenceTreeClick()... preference: " + pref
                + ", mListItemClicked: " + mListItemClicked);
        if (mListItemClicked) {
            return true;
        }
        mListItemClicked = true;
        setScreenEnabled();
        Intent intent = new Intent(this, NetworkEditor.class);
        NetworkInfoWithAcT info = mPreferenceMap.get(pref);
        /// M: ALPS00541579
        if (null == info) {
            return false;
        }
        mOldInfo = info;
        extractInfoFromNetworkInfo(intent, info);
        startActivityForResult(intent, REQUEST_EDIT);
        return true;
    }

    @Override
    protected void onDestroy() {
        unRegisterCallBacks();
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    private void initUi() {
        getSIMCapability();
        initPlmnList(this, false);
        mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        Log.d(LOG_TAG, "onResume()... mListItemClicked: " + mListItemClicked);
        mListItemClicked = false;
        setScreenEnabled();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ADD, 0, R.string.plmn_list_setting_add_plmn)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(LOG_TAG, "onPrepareOptionsMenu: mSubId " + mSubId);
        boolean isShouldEnabled = false;
        if (PhoneUtils.isValidSubId(mSubId)) {
            boolean isIdle = (TelephonyManager.getDefault().getCallState(
                    mSubId) == TelephonyManager.CALL_STATE_IDLE);
            isShouldEnabled = isIdle && TelephonyUtils.isRadioOn(mSubId);
        }
        if (menu != null) {
            menu.setGroupEnabled(0, (isShouldEnabled && (!mAirplaneModeEnabled)));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ADD:
            Intent intent = new Intent(this, NetworkEditor.class);
            intent.putExtra(NetworkEditor.PLMN_NAME, "");
            intent.putExtra(NetworkEditor.PLMN_CODE, "");
            intent.putExtra(NetworkEditor.PLMN_PRIORITY, 0);
            intent.putExtra(NetworkEditor.PLMN_SERVICE, 0);
            intent.putExtra(NetworkEditor.PLMN_ADD, true);
            intent.putExtra(NetworkEditor.PLMN_SUB, mSubId);
            startActivityForResult(intent, REQUEST_ADD);
            break;
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initPlmnList(TimeConsumingPreferenceListener listener, boolean skipReading) {
        Log.d(LOG_TAG, "init with skipReading = " + skipReading);
        if (!skipReading) {
            mPhone.getPol(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PLMN_LIST, 0, MyHandler.MESSAGE_GET_PLMN_LIST));
            if (listener != null) {
                /// ALPS01767285 Add customer dialog title.
                setDialogTitle(getString(R.string.plmn_list_setting_title));
                listener.onStarted(mPLMNListContainer, true);
            }
        }
    }

    public void onFinished(Preference preference, boolean reading) {
        super.onFinished(preference, reading);
        setScreenEnabled();
    }

    private void getSIMCapability() {
        mPhone.getPolCapability(mHandler.obtainMessage(MyHandler.MESSAGE_GET_PLMN_CAPIBILITY, 0,
                MyHandler.MESSAGE_GET_PLMN_CAPIBILITY));
    }

    private void refreshPreference(ArrayList<NetworkInfoWithAcT> list) {
        if (mPLMNListContainer.getPreferenceCount() != 0) {
            mPLMNListContainer.removeAll();
        }
        if (this.mPreferenceMap != null) {
            mPreferenceMap.clear();
        }

        if (mPLMNList != null) {
            mPLMNList.clear();
        }
        mPLMNList = list;
        if (list == null || list.size() == 0) {
            Log.d(LOG_TAG, "refreshPreference : NULL PLMN list!");
            if (list == null) {
                mPLMNList = new ArrayList<NetworkInfoWithAcT>();
            }
            return;
        }
        Collections.sort(list, new NetworkCompare());

        for (NetworkInfoWithAcT network : list) {
            addPLMNPreference(network);
            Log.d(LOG_TAG, "Plmnlist: " + network);
        }
    }

    class NetworkCompare implements Comparator<NetworkInfoWithAcT> {

        public int compare(NetworkInfoWithAcT object1, NetworkInfoWithAcT object2) {
            return (object1.getPriority() - object2.getPriority());
        }
    }

    private void addPLMNPreference(NetworkInfoWithAcT network) {
        int act = network.getAccessTechnology();
        /// for ALPS01659203 @{
        // when modem capability is not 4G, we should not show 4G PLMN.
        Log.d(LOG_TAG, "act: " + act);
        if (TelephonyUtils.isUSIMCard(this, mSubId) || act != NetworkEditor.RIL_4G) {
            String plmnName = network.getOperatorAlphaName();
            String extendName = getNWString(act);
            Preference pref = new Preference(this);
            pref.setTitle(plmnName + "(" + extendName + ")");
            mPLMNListContainer.addPreference(pref);
            mPreferenceMap.put(pref, network);
        }
        /// @}
    }

    private void extractInfoFromNetworkInfo(Intent intent, NetworkInfoWithAcT info) {
        intent.putExtra(NetworkEditor.PLMN_CODE, info.getOperatorNumeric());
        intent.putExtra(NetworkEditor.PLMN_NAME, info.getOperatorAlphaName());
        intent.putExtra(NetworkEditor.PLMN_PRIORITY, info.getPriority());
        intent.putExtra(NetworkEditor.PLMN_SERVICE, info.getAccessTechnology());
        intent.putExtra(NetworkEditor.PLMN_ADD, false);
        intent.putExtra(NetworkEditor.PLMN_SUB, mSubId);
    }

    protected void onActivityResult(final int requestCode,
            final int resultCode, final Intent intent) {
        /// M: for alps00572417 @{
        // only  sim is ready, we modify PLMN.
        if (intent != null && TelephonyUtils.isSimStateReady(
                SubscriptionManager.getSlotId(mSubId)) && mPLMNList != null) {
        /// @}
            NetworkInfoWithAcT newInfo = createNetworkInfo(intent);
            if (resultCode == NetworkEditor.RESULT_DELETE) {
                handlePLMNListDelete(mOldInfo);
            } else if (resultCode == NetworkEditor.RESULT_MODIFY) {
                if (requestCode == REQUEST_ADD) {
                    handlePLMNListAdd(newInfo);
                } else if (requestCode == REQUEST_EDIT) {
                    handlePLMNListEdit(newInfo);
                }
            }
        }
    }

    private NetworkInfoWithAcT createNetworkInfo(Intent intent) {
        String numberName = intent.getStringExtra(NetworkEditor.PLMN_CODE);
        String operatorName = intent.getStringExtra(NetworkEditor.PLMN_NAME);
        int priority = intent.getIntExtra(NetworkEditor.PLMN_PRIORITY, 0);
        int act = intent.getIntExtra(NetworkEditor.PLMN_SERVICE, 0);
        return new NetworkInfoWithAcT(operatorName, numberName, act, priority);
    }

    private void handleSetPLMN(ArrayList<NetworkInfoWithAcT> list) {
        mNumbers = list.size();
        // set to true for dialog show
        mIsForeground = true;
        onStarted(this.mPLMNListContainer, false);
        for (int i = 0; i < list.size(); i++) {
            NetworkInfoWithAcT ni = list.get(i);
            Log.d(LOG_TAG, "handleSetPLMN: set network: " + ni);
            mPhone.setPolEntry(ni, mHandler.obtainMessage(
                    MyHandler.MESSAGE_SET_PLMN_LIST, 0, MyHandler.MESSAGE_SET_PLMN_LIST));
        }
    }

    private void handlePLMNListAdd(NetworkInfoWithAcT newInfo) {
        Log.d(LOG_TAG, "handlePLMNListAdd: add new network: " + newInfo);
        dumpNetworkInfo(mPLMNList);
        mPLMNList.add(0, newInfo);
        adjustPriority(mPLMNList);
        dumpNetworkInfo(mPLMNList);
        handleSetPLMN(mPLMNList);
    }

    private void dumpNetworkInfo(List<NetworkInfoWithAcT> list) {
        if (!DBG) {
            return;
        }
        if (list == null) {
            Log.d(LOG_TAG, "dumpNetworkInfo : list is null");
            return;
        }
        Log.d(LOG_TAG, "dumpNetworkInfo : **********start*******");
        for (int i = 0; i < list.size(); i++) {
            Log.d(LOG_TAG, "dumpNetworkInfo : " + list.get(i));
        }
        Log.d(LOG_TAG, "dumpNetworkInfo : ***********stop*******");
    }

    private void handlePLMNListEdit(NetworkInfoWithAcT info) {
        Log.d(LOG_TAG, "handlePLMNListEdit: change : " + info);
        dumpNetworkInfo(mPLMNList);
        NetworkInfoWithAcT tempInfo = mPLMNList.get(info.getPriority());
        tempInfo.setOperatorAlphaName(info.getOperatorAlphaName());
        tempInfo.setOperatorNumeric(info.getOperatorNumeric());
        tempInfo.setAccessTechnology(info.getAccessTechnology());
        dumpNetworkInfo(mPLMNList);
        handleSetPLMN(mPLMNList);
    }

    private void adjustPriority(ArrayList<NetworkInfoWithAcT> list) {
        int priority = 0;
        for (NetworkInfoWithAcT info : list) {
            info.setPriority(priority++);
        }
    }

    private void handlePLMNListDelete(NetworkInfoWithAcT network) {
        Log.d(LOG_TAG, "handlePLMNListDelete : " + network);
        dumpNetworkInfo(mPLMNList);

        int oldLength = mPLMNList.size();
        mPLMNList.remove(network.getPriority());
        ArrayList<NetworkInfoWithAcT> list = new ArrayList<NetworkInfoWithAcT>();
        for (int i = 0; i < mPLMNList.size(); i++) {
            list.add(mPLMNList.get(i));
        }

        for (int i = list.size(); i < oldLength; i++) {
            NetworkInfoWithAcT ni = new NetworkInfoWithAcT("", null, 1, i);
            list.add(ni);
        }
        adjustPriority(list);
        dumpNetworkInfo(list);
        handleSetPLMN(list);
    }

    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_PLMN_LIST = 0;
        private static final int MESSAGE_SET_PLMN_LIST = 1;
        private static final int MESSAGE_GET_PLMN_CAPIBILITY = 2;

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PLMN_LIST:
                    handleGetPLMNResponse(msg);
                    break;
                case MESSAGE_SET_PLMN_LIST:
                    handleSetPLMNResponse(msg);
                    break;
                case MESSAGE_GET_PLMN_CAPIBILITY:
                    handleGetPLMNCapibilityResponse(msg);
                    break;
                default:
                    break;
            }
        }

        public void handleGetPLMNResponse(Message msg) {
            Log.d(LOG_TAG, "handleGetPLMNResponse: done");

            if (msg.arg2 == MyHandler.MESSAGE_GET_PLMN_LIST) {
                onFinished(mPLMNListContainer, true);
            } else {
                onFinished(mPLMNListContainer, false);
            }

            AsyncResult ar = (AsyncResult) msg.obj;
            boolean isUserException = false;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleGetPLMNResponse with exception = " + ar.exception);
                if (mPLMNList == null) {
                    mPLMNList = new ArrayList<NetworkInfoWithAcT>();
                }
            } else {
                refreshPreference((ArrayList<NetworkInfoWithAcT>) ar.result);
            }
        }

        public void handleSetPLMNResponse(Message msg) {
            Log.d(LOG_TAG, "handleSetPLMNResponse: done");
            mNumbers --;

            AsyncResult ar = (AsyncResult) msg.obj;
            boolean isUserException = false;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleSetPLMNResponse with exception = " + ar.exception);
            } else {
                Log.d(LOG_TAG, "handleSetPLMNResponse: with OK result!");
            }

            if (mNumbers == 0) {
                Log.d(LOG_TAG, "handleSetPLMNResponse: MESSAGE_GET_PLMN_LIST");
                mPhone.getPol(mHandler.obtainMessage(
                        MyHandler.MESSAGE_GET_PLMN_LIST, 0, MyHandler.MESSAGE_SET_PLMN_LIST));
            }
        }

        public void handleGetPLMNCapibilityResponse(Message msg) {
            Log.d(LOG_TAG, "handleGetPLMNCapibilityResponse: done");

            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleGetPLMNCapibilityResponse with exception = " + ar.exception);
            } else {
                mCapability.setCapability((int[]) ar.result);
            }
        }
    }

    private class SIMCapability {
        int mFirstIndex;
        int mLastIndex;
        int mFirstFormat;
        int mLastFormat;

        public SIMCapability(int startIndex, int stopIndex, int startFormat, int stopFormat) {
            mFirstIndex = startIndex;
            mLastIndex = stopIndex;
            mFirstFormat = startFormat;
            mLastFormat = stopFormat;
        }

        public void setCapability(int r[]) {
            if (r.length < 4) {
                return;
            }
            mFirstIndex = r[0];
            mLastIndex = r[1];
            mFirstFormat = r[2];
            mLastFormat = r[3];
            Log.d(LOG_TAG, "SIM PLMN List capability length: " + mLastIndex);
        }
    }

    private String getNWString(int rilNW) {
        int index = NetworkEditor.covertRilNW2Ap(this, rilNW, mSubId);
        String summary = "";
        /// M: ALPS00945171 change PLMN listview UI is the same to PLMN Edit UI
        summary = getResources().getStringArray(
                R.array.plmn_prefer_network_type_choices)[index];
        return summary;
    }

    private void setScreenEnabled() {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManager.getDefault().getCallState(
                mSubId) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && TelephonyUtils.isRadioOn(mSubId);
        getPreferenceScreen().setEnabled(isShouldEnabled);
        Log.d(LOG_TAG, "setScreenEnabled()... + mListItemClicked: " + mListItemClicked);
        mPLMNListContainer.setEnabled(!mListItemClicked && isShouldEnabled);
        invalidateOptionsMenu();
    }

    private void registerCallBacks() {
        mPhoneStateListener = new PhoneStateListener(mSubId) {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                Log.d(LOG_TAG, "onCallStateChanged ans state is " + state);
                setScreenEnabled();
            }
        };
        TelephonyManager.getDefault().listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unRegisterCallBacks() {
        if (PhoneUtils.isValidSubId(mSubId)) {
            TelephonyManager.getDefault().listen(
                    mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        unregisterReceiver(mReceiver);
    }

    @Override
    public void handleSubInfoUpdate() {
        Log.d(LOG_TAG, "handleSubInfoUpdate...");
        finish();
    }
}
