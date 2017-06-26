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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;

import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneGlobals.SubInfoUpdateListener;
import com.android.phone.R;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

public class NetworkEditor extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, TextWatcher, SubInfoUpdateListener {

    private static final String TAG = "Settings/NetworkEditor";
    private static final int MENU_DELETE = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_DISCARD = Menu.FIRST + 2;
    private static final int DIALOG_NETWORK_ID = 0;

    private final static String KEY_NETWORK_TYPE = "key_network_type";
    private static final String BUTTON_NETWORK_ID_KEY = "network_id_key";
    private static final String PHONE_PREFERENCE_NAME = "com.android.phone_preferences";

    public static final String PLMN_NAME = "plmn_name";
    public static final String PLMN_CODE = "plmn_code";
    public static final String PLMN_PRIORITY = "plmn_priority";
    public static final String PLMN_SERVICE = "plmn_service";
    public static final String PLMN_ADD = "plmn_add";
    public static final String PLMN_SUB = "plmn_sub";

    public static final int RESULT_MODIFY = 100;
    public static final int RESULT_DELETE = 200;

    private static final int INDEX_2G = 0;
    private static final int INDEX_3G = 1;
    private static final int INDEX_4G = 2;
    private static final int INDEX_2G_3G = 3;
    private static final int INDEX_2G_4G = 4;
    private static final int INDEX_3G_4G = 5;
    private static final int INDEX_2G_3G_4G = 6;

    public static final int RIL_NONE = 0x0;
    public static final int RIL_NONE2 = 0x2;
    public static final int RIL_2G = 0x1;
    public static final int RIL_3G = 0x4;
    public static final int RIL_4G = 0x8;
    public static final int RIL_2G_3G = 0x5;
    public static final int RIL_2G_4G = 0x9;
    public static final int RIL_3G_4G = 0xC;
    public static final int RIL_2G_3G_4G = 0xD;

    private Preference mNetworkId = null;
    private NetworkTypePreference mNetworkMode = null;

    private String mInitNetworkId;
    private String mInitNetworkMode;

    private String mNotSet = null;
    private String mPLMNName;
    private boolean mAirplaneModeEnabled = false;
    private IntentFilter mIntentFilter;
    private int mSubId;
    private int mAct;
    private boolean mActSupport = true;
    private EditText mNetworkIdText;
    private AlertDialog mIdDialog = null;
    private NetworkInfo mNetworkInfo;

    private PhoneStateListener mPhoneStateListener;

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

    private OnClickListener mNetworkIdListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                /// M: ALPS00726774 @{
                // save number in order to ust it on activity re-onresume.
                mNetworkInfo.setNetworkId(checkNull(mNetworkIdText.getText().toString()));
                mNetworkId.setSummary(checkNull(mNetworkIdText.getText().toString()));
                // for ALPS01021086
                // invalidate the menu here, or the "save" item will not be enabled
                invalidateOptionsMenu();
                /// @}
            }
        }
    };

    protected void onCreate(Bundle icicle) {
        setTheme(R.style.Theme_Material_Settings);
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.mtk_plmn_editor);
        mNotSet = getResources().getString(
                R.string.voicemail_number_not_set);
        mNetworkId = (Preference) findPreference(BUTTON_NETWORK_ID_KEY);
        mNetworkMode = (NetworkTypePreference) findPreference(KEY_NETWORK_TYPE);
        mIntentFilter = new IntentFilter(
                Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_MSIM_MODE_CHANGED);
        mNetworkInfo = new NetworkInfo();
        createNetworkInfo(getIntent());
        mNetworkMode.initCheckState(mAct);
        mNetworkMode.setOnPreferenceChangeListener(this);
        mPhoneStateListener = new PhoneStateListener(mSubId) {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                switch(state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    setScreenEnabled();
                    break;
                default:
                    break;
                }
            }
        };
        TelephonyManager.getDefault().listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
        registerReceiver(mReceiver, mIntentFilter);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in
            // onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mNetworkId) {
            removeDialog(DIALOG_NETWORK_ID);
            showDialog(DIALOG_NETWORK_ID);
            validate();
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    protected void onResume() {
        super.onResume();
        mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        setScreenEnabled();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        TelephonyManager.getDefault().listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (!getIntent().getBooleanExtra(PLMN_ADD, false)) {
            menu.add(0, MENU_DELETE, 0, com.android.internal.R.string.delete);
        }
        menu.add(0, MENU_SAVE, 0, R.string.save);
        menu.add(0, MENU_DISCARD, 0, com.android.internal.R.string.cancel);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean enable = isShouldEnable();
        boolean isEmpty = mNotSet.equals(mNetworkId.getSummary());
        if (menu != null) {
            menu.setGroupEnabled(0, enable);
            if (getIntent().getBooleanExtra(PLMN_ADD, true)) {
                menu.getItem(0).setEnabled(enable && !isEmpty);
            } else {
                /// for ALPS01497555 @{
                // when network info changed, enable save menu
                Log.d(TAG, "networkID: " + mNetworkId.getSummary()
                        + ", networkmode: " + mNetworkMode.getSummary());
                boolean isSame = mInitNetworkId.equals(mNetworkId.getSummary())
                        && mInitNetworkMode.equals(mNetworkMode.getSummary());
                menu.getItem(1).setEnabled(enable && !isSame && !isEmpty);
                /// @}
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isShouldEnable() {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManager.getDefault().getCallState(
                mSubId) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && TelephonyUtils.isRadioOn(mSubId);
        return isShouldEnabled;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE:
            setRemovedNetwork();
            break;
        case MENU_SAVE:
            validateAndSetResult();
            break;
        case MENU_DISCARD:
            break;
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        finish();
        return super.onOptionsItemSelected(item);
    }

    private void validateAndSetResult() {
        Intent intent = new Intent(this, PLMNListPreference.class);
        setResult(RESULT_MODIFY, intent);
        genNetworkInfo(intent);
    }

    private void genNetworkInfo(Intent intent) {
        intent.putExtra(NetworkEditor.PLMN_NAME, checkNotSet(mPLMNName));
        intent.putExtra(NetworkEditor.PLMN_CODE, mNetworkId.getSummary());
        intent.putExtra(NetworkEditor.PLMN_PRIORITY, mNetworkInfo.getPriority());
        try {
            intent.putExtra(NetworkEditor.PLMN_SERVICE, mAct);
        } catch (NumberFormatException e) {
            intent.putExtra(NetworkEditor.PLMN_SERVICE, covertApNW2Ril(0));
        }
    }

    private void setRemovedNetwork() {
        Intent intent = new Intent(this, PLMNListPreference.class);
        setResult(RESULT_DELETE, intent);
        genNetworkInfo(intent);
    }

    /**
     * convert Network Ril to index of plmn_prefer_network_type_choices.
     * <bit3, bit2, bit1, bit0> --> <E-UTRAN_ACT, UTRAN_ACT, GSM_COMPACT_ACT, GSM_ACT> -->
     * <4G, 3G, not use, 2G> when read from modem, bit1 may be 0 / 1; but when write, we
     * always write it as 0.
     */
    public static int covertRilNW2Ap(Context context, int rilNW, int subId) {
        int result = INDEX_2G;
        boolean is2GEnable = (rilNW & NetworkEditor.RIL_2G) != 0;
        boolean is3GEnable = (rilNW & NetworkEditor.RIL_3G) != 0;
        boolean is4GEnable = TelephonyUtils.isUSIMCard(context, subId) &&
                ((rilNW & NetworkEditor.RIL_4G) != 0) && FeatureOption.isMtkLteSupport();

        if (is2GEnable && is3GEnable && is4GEnable) {
            result = INDEX_2G_3G_4G;
        } else if (!is2GEnable && is3GEnable && is4GEnable) {
            result = INDEX_3G_4G;
        } else if (is2GEnable && !is3GEnable && is4GEnable) {
            result = INDEX_2G_4G;
        } else if (is2GEnable && is3GEnable && !is4GEnable) {
            result = INDEX_2G_3G;
        } else if (!is2GEnable && !is3GEnable && is4GEnable) {
            result = INDEX_4G;
        } else if (!is2GEnable && is3GEnable && !is4GEnable) {
            result = INDEX_3G;
        } else if (is2GEnable && !is3GEnable && !is4GEnable) {
            result = INDEX_2G;
        }

        return result;
    }

    public static int covertApNW2Ril(int modeIndex) {
        int result = 0;
        switch (modeIndex) {
        case INDEX_2G:
            result = RIL_2G;
            break;
        case INDEX_3G:
            result = RIL_3G;
            break;
        case INDEX_4G:
            result = RIL_4G;
            break;
        case INDEX_2G_3G:
            result = RIL_2G_3G;
            break;
        case INDEX_2G_4G:
            result = RIL_2G_4G;
            break;
        case INDEX_3G_4G:
            result = RIL_3G_4G;
            break;
        case INDEX_2G_3G_4G:
            result = RIL_2G_3G_4G;
            break;
        default:
            break;
        }
        return result;
    }

    private void createNetworkInfo(Intent intent) {
        mPLMNName = intent.getStringExtra(PLMN_NAME);
        mSubId = intent.getIntExtra(PLMN_SUB, -1);
        mAct = intent.getIntExtra(PLMN_SERVICE, 0);
        // mAct == 0 || mAct == 2 means new a plmn or modem pass us a none-set
        // value(please refer covertRilNW2Ap(...) to see its define), set 2G as default.
        if (mAct == RIL_NONE || mAct == RIL_NONE2) {
            mAct = RIL_2G;
        }
        updateNetWorkInfo(intent);
    }

    private String checkNotSet(String value) {
        if (value == null || value.equals(mNotSet)) {
            return "";
        } else {
            return value;
        }
    }

    private String checkNull(String value) {
        if (value == null || value.length() == 0) {
            return mNotSet;
        } else {
            return value;
        }
    }

    private void setScreenEnabled() {
        boolean enable = isShouldEnable();
        getPreferenceScreen().setEnabled(enable);
        invalidateOptionsMenu();
        mNetworkMode.setEnabled(mActSupport && enable);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_NETWORK_ID) {
            mNetworkIdText = new EditText(this);
            if (!mNotSet.equals(mNetworkId.getSummary())) {
                mNetworkIdText.setText(mNetworkId.getSummary());
            }
            mNetworkIdText.addTextChangedListener(this);
            mNetworkIdText.setInputType(InputType.TYPE_CLASS_NUMBER);
            mIdDialog = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.network_id))
                .setView(mNetworkIdText)
                .setPositiveButton(getResources().getString(
                        com.android.internal.R.string.ok), mNetworkIdListener)
                .setNegativeButton(getResources().getString(
                        com.android.internal.R.string.cancel), null)
                .create();
            mIdDialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            return mIdDialog;
        }
        return null;
    }

    public void validate() {
        int len = mNetworkIdText.getText().toString().length();
        boolean state = true;
        if (len < 5 || len > 6) {
            state = false;
        }
        if (mIdDialog != null) {
            mIdDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(state);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        validate();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
              int after) {
        // work done in afterTextChanged
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // work done in afterTextChanged
    }

    /**
     * Add for ALPS00759515 Commit String value to Phone Preference.
     * @param key
     * @param value
     */
    private void commitPreferenceStringValue(String key, String value) {
        SharedPreferences mPreferences = this.getSharedPreferences(
                PHONE_PREFERENCE_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * Add for ALPS00759515 update the preference screen.
     * @param intent
     */
    private void updateNetWorkInfo(Intent intent) {
        Log.d(TAG, "---updateNetWorkInfo-- " + mNetworkInfo.getPriority() + " : "
                + mNetworkInfo.getNetworkId() + " : " + mNetworkInfo.getNetWorkMode());
        // NetworkId:
        if (TextUtils.isEmpty(mNetworkInfo.getNetworkId())) {
            mInitNetworkId = intent.getStringExtra(PLMN_CODE);
            Log.d(TAG, "mInitNetworkId: " + mInitNetworkId);
            mNetworkInfo.setNetworkId(mInitNetworkId);
        }
        mNetworkId.setSummary(checkNull(mNetworkInfo.getNetworkId()));
        // Priority:
        if (mNetworkInfo.mPriority == -1) {
            mNetworkInfo.setPriority(intent.getIntExtra(PLMN_PRIORITY, 0));
        }

        // NetworkMode
        if (TextUtils.isEmpty(mNetworkInfo.getNetWorkMode())) {
            int act = intent.getIntExtra(PLMN_SERVICE, 0);
            //if act is not supported, disable mNetworkMode
            Log.d(TAG, "act = " + act);
            if (!getIntent().getBooleanExtra(PLMN_ADD, true)) {
                mActSupport = act != 0;
            }
            Log.d(TAG, "mActSupport = " + mActSupport);
            act = covertRilNW2Ap(this, act, mSubId);
            mInitNetworkMode = getResources().getStringArray(
                    R.array.plmn_prefer_network_type_choices)[act];
            Log.d(TAG, "mInitNetworkMode: " + mInitNetworkMode);
            mNetworkInfo.setNetWorkMode(mInitNetworkMode);
        }
        mNetworkMode.setSummary(mNetworkInfo.getNetWorkMode());
    }

    /**
     * Add for ALPS00759515
     * Keep NetworkEditor info.
     */
    class NetworkInfo {
        private String mNetworkId;
        private int mPriority;
        private String mNetWorkMode;

        public NetworkInfo() {
            mNetworkId = null;
            mPriority = -1;
            mNetWorkMode = null;
        }

        public String getNetworkId() {
            return mNetworkId;
        }

        public void setNetworkId(String networkId) {
            this.mNetworkId = networkId;
        }

        public int getPriority() {
            return mPriority;
        }

        public void setPriority(int priority) {
            this.mPriority = priority;
        }

        public String getNetWorkMode() {
            return mNetWorkMode;
        }

        public void setNetWorkMode(String netWorkMode) {
            this.mNetWorkMode = netWorkMode;
        }
    }

    private void updateNetworkType(int act) {
        int index = covertRilNW2Ap(this, act, mSubId);
        Log.d(TAG, "updateNetworkType: act = " + act + ", index = " + index);
        mNetworkInfo.setNetWorkMode(getResources().getStringArray(
                R.array.plmn_prefer_network_type_choices)[index]);
        mNetworkMode.setSummary(mNetworkInfo.getNetWorkMode());
    }

    @Override
    public void handleSubInfoUpdate() {
        finish();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Log.d(TAG, "key: " + key);
        if (KEY_NETWORK_TYPE.equals(key)) {
            mAct = (Integer) newValue;
            updateNetworkType(mAct);
            invalidateOptionsMenu();
            return true;
        }
        return false;
    }

}
