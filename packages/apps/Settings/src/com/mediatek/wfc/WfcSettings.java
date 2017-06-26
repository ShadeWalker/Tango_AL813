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

package com.mediatek.wfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SwitchBar;

import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.IWfcSettingsExt;

import java.util.List;
import java.util.ArrayList;


/* Preference to show WFC Settings screen */
public class WfcSettings extends SettingsPreferenceFragment implements
        SwitchBar.OnSwitchChangeListener, View.OnClickListener {

    private static final boolean DBG = true;
    private static final String TAG = "WfcSettings";
    private static final int RESULT_WFC_PREFERENCE = 1011;
    private static final String FIRST_TIME = "first_time_in_wfcSetting";
    private static final String SELECTED_WFC_PREFERENCE_KEY = "selected_wfc_preference";
    private static final String WFC_PREF_LIST = "wfc_preference_list";
    private static final String TUTORIALS = "Tutorials";
    private static final String TOP_QUESTIONS = "Top_questions";
    private static final int WFC_PREFERENCE_DIALOG_ID = 1;
    private static final String ACTION_WIFI_ONLY_MODE_CHANGED = "android.intent.action.ACTION_WIFI_ONLY_MODE";
    private static final String EXTRA_WIFI_ONLY_MODE_CHANGED = "state";
    private static final boolean WIFI_ONLY_MODE_OFF = false;
    private static final boolean WIFI_ONLY_MODE_ON = true;
    
	// keys for data in bundle required by operator
	private static final String KEY_WIFI_PREF = "wifi_preferred";
	private static final String KEY_SUMMARY_WIFI_PREF = "summary_wifi_preferred";
	private static final String KEY_LINE_UNDER_WIFI_PREF = "line_under_wifi_preferred";
	private static final String KEY_CELLULAR_PREF = "cellular_network_preferred";
	private static final String KEY_SUMMARY_CELLULAR_PREF = "summary_cellular_network_preferred";
	private static final String KEY_LINE_UNDER_CELLULAR_PREF = "line_under_cellular_preferred";
	private static final String KEY_WIFI_ONLY = "never_use_cellular_network";
	private static final String KEY_SUMMARY_WIFI_ONLY = "summary_never_use_cellular_network";
    
    private enum WfcSwitchState {
        SWITCH_OFF,
        SWITCH_TURNING_ON,
        SWITCH_TURNING_OFF,
        SWITCH_ON
    };

    private Context mContext;
    private SettingsActivity mActivity;
    private SwitchBar mSwitch;

    private Preference mWfcPreferenceList;
    private Preference mTutorials;
    private Preference mTopQuestions;

    private int mPrevCallState = TelephonyManager.CALL_STATE_IDLE;

    private CallStateListener mCallListener;

    // For preference dialog    
    private RadioButton mWifiPreferredButton;
    private RadioButton mCellularPreferredButton;
    private RadioButton mWifiOnlyButton;
    private AlertDialog mWifiPreferenceDialog;

    private IntentFilter mIntentFilter;

    private OnPreferenceChangeListener mPrefChangeListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // TODO Auto-generated method stub
            if (preference.equals(mWfcPreferenceList)) {
                if (DBG) Log.d(TAG, "in onPreferenceChange, new value:" +
                        Integer.parseInt(newValue.toString()));
                /* Turn OFF radio, if wfc preference is WIFI_ONLY */
                if ((Integer.parseInt(newValue.toString())) == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
                    //setRadioPower(false);
                    if (DBG) Log.d(TAG, "Turn OFF radio, as wfc pref selected is wifi_only");
                    sendWifiOnlyModeIntent(mContext, true);
                }
                /* Turn ON radio, if wfc preference was WIFI_ONLY */
                if (getWfcPreferenceFromDb() == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
                    //setRadioPower(true);
                    if (DBG) Log.d(TAG, "Turn ON radio, as wfc pref selected was wifi_only & now is not");
                    sendWifiOnlyModeIntent(mContext, false);
                }
                setWfcPreferenceInDb(Integer.parseInt(newValue.toString()));
                return true;
            }
            return false;
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE.equals(action)
                    || (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)
                    && IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)))) {
                if (!ImsManager.isImsSupportByCarrier(context)) {
                    getActivity().finish();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wfc_settings);

        mActivity = (SettingsActivity) getActivity();
        mContext = mActivity.getApplicationContext();

        mWfcPreferenceList = (Preference) findPreference(WFC_PREF_LIST);
        mTutorials = findPreference(TUTORIALS);

        mTopQuestions = findPreference(TOP_QUESTIONS);

        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onActivityCreated(savedInstanceState);
        mSwitch = mActivity.getSwitchBar();
        init();
        mWfcPreferenceList.setOnPreferenceChangeListener(mPrefChangeListener);
        mSwitch.addOnSwitchChangeListener(this);
        mSwitch.show();
        mCallListener = new CallStateListener();
        ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).listen
                (mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
        getActivity().registerReceiver(mBroadcastReceiver, mIntentFilter);
        if (DBG) Log.d(TAG, "onActivityCreated exit");
    }

    @Override
    public void onDestroyView() {
        // TODO Auto-generated method stub
        mSwitch.removeOnSwitchChangeListener(this);
        mSwitch.hide();
        ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).listen
                (mCallListener, PhoneStateListener.LISTEN_NONE);
        getActivity().unregisterReceiver(mBroadcastReceiver);
        super.onDestroyView();
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        /* TODO handle switch state change */

        /* Revert user action with toast, if IMS is enabling or disabling */
        if (isInSwitchProcess()) {
            if (DBG) Log.d(TAG, "[onClick] Switching process ongoing");
            Toast.makeText(mContext, R.string.Switch_not_in_use_string, Toast.LENGTH_SHORT)
                    .show();
            mSwitch.setChecked(!isChecked);
            return;
        }

        int user_choice = TelephonyManager.WifiCallingChoices.ALWAYS_USE;
        WfcSwitchState state;
        /* Set value in settings db */
        if (isChecked) {
            user_choice = TelephonyManager.WifiCallingChoices.ALWAYS_USE;
            /* When user turns ON WFC, sync db with shared preference */
            setWfcPreferenceInDb(getWfcPreferenceFromSharedPreference());
            /* Turn OFF radio power, on turning WFC ON, if preference selected is WIFI_ONLY */
            if (getWfcPreferenceFromDb() == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
                //setRadioPower(false);
                if (DBG) Log.d(TAG, "Turn OFF radio, as wfc is getting ON & pref is wifi_only");
                sendWifiOnlyModeIntent(mContext, true);
            }
        } else {
            /* Turn ON radio power, on turning WFC OFF, if preference selected is WIFI_ONLY */
            if (getWfcPreferenceFromDb() == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
                //setRadioPower(true);
                if (DBG) Log.d(TAG, "Turn ON radio, as wfc is getting OFF & pref was wifi_only");
                sendWifiOnlyModeIntent(mContext, false);
            }
            user_choice = TelephonyManager.WifiCallingChoices.NEVER_USE;
            /* Set db value in sharedPreference, to handle case where sp is still empty:
             * By default WFC ON & any 1/2/3 set in db as wfc mode. User turns WFC off,
             * sp will remain empty & db set as 0 for WFC mode. When user next time enters
             * wfc setting, db value is lost. So, store db value in sp before settings db as 0.
             */
            setWfcPreferenceInSharedPreference(getWfcPreferenceFromDb());
            /* When user turns ON WFC, set in db Cellular_only to be propogated to Modem */
            setWfcPreferenceInDb(TelephonyManager.WifiCallingPreferences.CELLULAR_ONLY);
        }
        setWfcOnOffinDb(user_choice);
        if (DBG) Log.d(TAG, "switch on/off" + isChecked);
        changeStateOfPreference(isChecked);
        /* Enable/disable IMS */
        turnOnOffIms(isChecked);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (DBG) Log.d(TAG, "in onpreftreeClick");
        if (preference.equals(mWfcPreferenceList)) {
            if (DBG) Log.d(TAG, "in wfc pref");
            showDialog(WFC_PREFERENCE_DIALOG_ID);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == WFC_PREFERENCE_DIALOG_ID) {
            AlertDialog.Builder altBld = new AlertDialog.Builder(mActivity);
            // set title
            altBld.setTitle(R.string.connection_preference);
            // inflate custom view
            View dlgView = mActivity.getLayoutInflater().inflate(R.layout.wfc_preference_dlg_layout, null);
            altBld.setView(dlgView);
			
            // create bundle for op modification
            Bundle dialogData = new Bundle();
            dialogData.putInt(KEY_WIFI_PREF, R.id.wifi_preferred);
            dialogData.putInt(KEY_SUMMARY_WIFI_PREF, R.id.summary_wifi_preferred);
            dialogData.putInt(KEY_LINE_UNDER_WIFI_PREF, R.id.line_under_wifi_preferred);
            dialogData.putInt(KEY_CELLULAR_PREF, R.id.cellular_network_preferred);
            dialogData.putInt(KEY_SUMMARY_CELLULAR_PREF, R.id.summary_cellular_network_preferred);
            dialogData.putInt(KEY_LINE_UNDER_CELLULAR_PREF, R.id.line_under_cellular_preferred);
            dialogData.putInt(KEY_WIFI_ONLY, R.id.never_use_cellular_network);
            dialogData.putInt(KEY_SUMMARY_WIFI_ONLY, R.id.summary_never_use_cellular_network);
            
            IWfcSettingsExt mIWfcSettingsExt = UtilsExt.getWfcSettingsExtPlugin(mActivity);
            mIWfcSettingsExt.modifyWfcPreferenceDialog(mActivity, dlgView, dialogData);
            
            RadioGroup wfcPreferenceRadioGroup = (RadioGroup) dlgView.findViewById(R.id.wfc_preference_button_group);
            TextView wifiPreferredSummary = (TextView) dlgView.findViewById(R.id.summary_wifi_preferred);
            if (wifiPreferredSummary != null) {
            wifiPreferredSummary.setOnClickListener(this);
            }
            TextView cellularPreferredSummary = (TextView) dlgView.findViewById(R.id.summary_cellular_network_preferred);
            if (cellularPreferredSummary != null) {
            cellularPreferredSummary.setOnClickListener(this);
            }
            TextView wifiOnlySummary = (TextView) dlgView.findViewById(R.id.summary_never_use_cellular_network);
            if (wifiOnlySummary != null) {
            wifiOnlySummary.setOnClickListener(this);
            }
            wfcPreferenceRadioGroup.check(getSelectedButtonId(getWfcPreferenceFromSharedPreference()));
            wfcPreferenceRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    // TODO Auto-generated method stub
                    if (DBG) Log.d(TAG, "in checkChangeListener, checkedId:" + checkedId);
                    int selected = getWfcPreferenceFromSharedPreference();
                    switch(checkedId) {
                        case R.id.wifi_preferred:
                            if (DBG) Log.d(TAG, "first button clicked");
                            selected = TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED;
                            break;
                        case R.id.cellular_network_preferred:
                            if (DBG) Log.d(TAG, "second button clicked");
                            selected = TelephonyManager.WifiCallingPreferences.CELLULAR_PREFERRED;
                            break;
                        case R.id.never_use_cellular_network:
                            if (DBG) Log.d(TAG, "third button clicked");
                            selected = TelephonyManager.WifiCallingPreferences.WIFI_ONLY;
                            break;
                        default:
                            if (DBG) Log.d(TAG, "in default:" + checkedId);
                            break;
                    }
                    handleWfcPreferenceChange(selected);
                    if (DBG) Log.d(TAG, "Preference selection done return back screen");
                    mWifiPreferenceDialog.dismiss();
                }
            });
            mWifiPreferredButton = (RadioButton) dlgView.findViewById(R.id.wifi_preferred);
            mCellularPreferredButton = (RadioButton) dlgView.findViewById(R.id.cellular_network_preferred);
            mWifiOnlyButton = (RadioButton) dlgView.findViewById(R.id.never_use_cellular_network);
            // set button
            altBld.setNegativeButton(R.string.cancel, new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                    case DialogInterface.BUTTON_NEGATIVE:
                        if (DBG) Log.d(TAG, "negative clicked, activity finish");
                    default:
                        dialog.dismiss();
                    break;
               }

                }
            });
            mWifiPreferenceDialog = altBld.create();
            return mWifiPreferenceDialog;
        }
        return null;
    }

    @Override
    public void onClick(View v) {
        if (DBG) Log.d(TAG, "in View onClick");
        /* Note here, radioGroup.check(radioButtonId) is not being used to check/uncheck the buttons
         * because Android calls thrice the onCheckedChanged listener on setting buttons with this method.
         * Instead RadioButton.setChecked(true) is being used, as it calls listener only once.
         */
        if (v instanceof TextView) {
            switch(v.getId()) {
            case R.id.summary_wifi_preferred:
                if (DBG) Log.d(TAG, "first textview pressed");
                mWifiPreferredButton.setChecked(true);
                break;
            case R.id.summary_cellular_network_preferred:
                if (DBG) Log.d(TAG, "second textview pressed");
                mCellularPreferredButton.setChecked(true);
                break;
            case R.id.summary_never_use_cellular_network:
                if (DBG) Log.d(TAG, "third textview pressed");
                mWifiOnlyButton.setChecked(true);
                break;
            default:
                if (DBG) Log.d(TAG, "case in default");
                break;
            }
        }

    }

    private int getSelectedButtonId(int wfc_preference) {
        if (DBG) Log.d(TAG, "in getSelectedButtonId, preference selected:" + wfc_preference);
        switch(wfc_preference) {
            case TelephonyManager.WifiCallingPreferences.CELLULAR_PREFERRED:
                return R.id.cellular_network_preferred;

            case TelephonyManager.WifiCallingPreferences.WIFI_ONLY:
                return R.id.never_use_cellular_network;

            case TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED:
            default:
                return R.id.wifi_preferred;
        }
    }

    /* Initialize Settings */
    private void init() {
        if (DBG) Log.d(TAG, "init");
        int switchStateDb = getWfcOnOffFromDb();
        if (DBG) Log.d(TAG, "init, switchState from db:" + switchStateDb);
        if (DBG) Log.d(TAG, "In db,switch state:" + switchStateDb);

        boolean switchState = switchStateDb == TelephonyManager.WifiCallingChoices.NEVER_USE;
        /* Turn off switch & disable preference if its NEVER_USE */
        mSwitch.setChecked(!switchState);
        changeStateOfPreference(!switchState);
        setWfcPreferenceSummary();
        if (DBG) Log.d(TAG, "init exit");
    }

    private void turnOnOffIms(boolean switchState) {
        /* Enable/disable IMS */
        // TODO: maintain switch state & wfc preference state, to differ user from playin with switch
        ImsManager.setWfcSettings(switchState, mContext);
    }

    private void maintainState(WfcSwitchState oldState, WfcSwitchState newState) {
        if (DBG) Log.d(TAG, "maintainState oldState = " + oldState);
        if (DBG) Log.d(TAG, "maintainState newState = " + newState);
        boolean enablePref = true;
        boolean enableSwitch = true;

        /* Switch turned ON or OFF*/
        if ((oldState == WfcSwitchState.SWITCH_OFF && newState == WfcSwitchState.SWITCH_TURNING_ON)
                || (oldState == WfcSwitchState.SWITCH_ON && newState == WfcSwitchState.SWITCH_TURNING_OFF)) {
            enablePref = false;
            enableSwitch = false;
        } else if (oldState == WfcSwitchState.SWITCH_TURNING_OFF && newState == WfcSwitchState.SWITCH_OFF) {
            enablePref = false;
            enableSwitch = true;
        }
        else if ((oldState == WfcSwitchState.SWITCH_OFF && newState == WfcSwitchState.SWITCH_TURNING_OFF)
                || (oldState == WfcSwitchState.SWITCH_ON && newState == WfcSwitchState.SWITCH_TURNING_ON)) {
            return;
        } else {
            // should not happen; do not know what to do, as screen is in transition phase
            if (DBG) Log.d(TAG, "Shouldn't have fallen in this case");
        }
        mSwitch.setEnabled(enableSwitch);
        changeStateOfPreference(enablePref);
    }

    private void changeStateOfPreference(boolean state) {
        if (DBG) Log.d(TAG, "in changeStateofPref, state:" + state);
        /* mSwitch.isChecked() is needed to prevent enabling preferences if switch is unchecked
         * state is needed to provide provision of controling enabling/disabling preferences due to various actions
         * both are needed to handle conditions like:
         * 1) WFC ON, call ringing: switch & preference to be disabled. So need 'state' to make
         *    methods disable pref, as with only switch it will remain enabled.
         * 2) WFC OFF, call ringing:  switch & preference to be disabled. Call hanged: Only Switch enabled.
         *    If only 'state' check present, it will enable preference too, while switch is off.
         */
        if (mSwitch.isChecked() && state) {
            if (DBG) Log.d(TAG, "enabling");
            mWfcPreferenceList.setEnabled(true);
        } else {
            if (DBG) Log.d(TAG, "disabling");
            mWfcPreferenceList.setEnabled(false);
        }
    }

    private void setWfcPreferenceSummary() {
        mWfcPreferenceList.setSummary(mContext.getResources()
                          .getStringArray(R.array.user_wfc_preference)[getWfcPreferenceFromSharedPreference() - 1]);
    }

    private void setWfcOnOffinDb(int state) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,    state);
        if (DBG) Log.d(TAG, "wfc state after setting in db:"
                + Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.ALWAYS_USE));
    }

    private void setWfcPreferenceInDb(int wfcPreference) {
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SELECTED_WFC_PREFERRENCE,
                wfcPreference);
        if (DBG) Log.d(TAG, "wfc pref after setting in db:"
                + Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED));
    }

    private int getWfcOnOffFromDb() {
        if (DBG) Log.d(TAG, "wfc state in db:"
                + Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.ALWAYS_USE));
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.ALWAYS_USE);
    }

    private int getWfcPreferenceFromDb() {
        if (DBG) Log.d(TAG, "wfc pref in db:"
                + Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED));
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED);
    }

    private int getWfcPreferenceFromSharedPreference() {
        if (DBG) Log.d(TAG, "wfc pref in sp:"
                + PreferenceManager.getDefaultSharedPreferences(mContext)
                                .getInt(WFC_PREF_LIST, getWfcPreferenceFromDb()));
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                                .getInt(WFC_PREF_LIST, getWfcPreferenceFromDb());
    }

    private void setWfcPreferenceInSharedPreference(int wfcPreferenceSelected) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        Editor ed = sp.edit();
        ed.putInt(WFC_PREF_LIST, wfcPreferenceSelected);
        ed.commit();
        if (DBG) Log.d(TAG, "wfc pref in sp after commit:" + getWfcPreferenceFromSharedPreference());
    }

    /**
     * Get the IMS_STATE_XXX, so can get whether the state is in changing.
     * @return true if the state is in changing, else return false.
     */
    private boolean isInSwitchProcess() {
        int imsState = PhoneConstants.IMS_STATE_DISABLED;
        try {
            // TODO: subid
            if (DBG) Log.d(TAG, "default voice sub id:" + SubscriptionManager.getDefaultVoiceSubId());
            imsState = ImsManager.getInstance(mContext, SubscriptionManager.getDefaultVoicePhoneId())
                            .getImsState();
        } catch (ImsException e) {
            if (DBG) Log.d(TAG, "[isInSwitchProcess]" + e);
            return false;
        }
        if (DBG) Log.d(TAG, "[can turn wfc on/off], imsState = " + imsState);
        return imsState == PhoneConstants.IMS_STATE_DISABLING
                || imsState == PhoneConstants.IMS_STATE_ENABLING;
    }

    private void handleWfcPreferenceChange(int wfcPreferenceSelected) {
        /* Turn OFF radio, if wfc preference is WIFI_ONLY */
        if (wfcPreferenceSelected == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
            //setRadioPower(false);
            if (DBG) Log.d(TAG, "Turn OFF radio, as wfc pref selected is wifi_only");
            sendWifiOnlyModeIntent(mContext, true);
        }
        /* Turn ON radio, if wfc preference was WIFI_ONLY */
        if (getWfcPreferenceFromSharedPreference() == TelephonyManager.WifiCallingPreferences.WIFI_ONLY) {
            //setRadioPower(true);
            if (DBG) Log.d(TAG, "Turn ON radio, as wfc pref selected was wifi_only & now is not");
            sendWifiOnlyModeIntent(mContext, false);
        }
        setWfcPreferenceInSharedPreference(wfcPreferenceSelected);
        setWfcPreferenceInDb(wfcPreferenceSelected);
        setWfcPreferenceSummary();
    }

    private void setRadioPower(boolean turnOn) {
        int sim_mode = turnOn ? 1:0; 
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, sim_mode);
        ITelephony iTel = ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
        try {
            iTel.setRadioForSubscriber(SubscriptionManager.getDefaultVoiceSubId(), turnOn);
        } catch (RemoteException e) {
            if (DBG) Log.e(TAG, "Exception in setRadioPower" + e);
            e.printStackTrace();
        }
    }
    
    public static void sendWifiOnlyModeIntent(Context context, boolean mode) {
        Intent intent = new Intent(ACTION_WIFI_ONLY_MODE_CHANGED);
        intent.putExtra(EXTRA_WIFI_ONLY_MODE_CHANGED, mode);
        if (DBG) Log.d(TAG, "Sending wifiOnlyMode intent, mode:" + mode);
        context.sendBroadcast(intent);
    }
    
    private class CallStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            // TODO Auto-generated method stub
            if (DBG) Log.d(TAG, "in onCallStateChanged state:" + state + ", prev state:" + mPrevCallState);
            switch(state) {
                case TelephonyManager.CALL_STATE_IDLE: {
                    if (mPrevCallState != TelephonyManager.CALL_STATE_IDLE) { //call ended/answered/missed
                        mPrevCallState = state;
                        mSwitch.setEnabled(true);
                        changeStateOfPreference(true);
                    }
                }
                break;
                case TelephonyManager.CALL_STATE_OFFHOOK://answering incoming call or making outgoing call
                case TelephonyManager.CALL_STATE_RINGING://incoming call
                    mPrevCallState = state;
                    if (DBG) Log.d(TAG, "mWifiPreferenceDialog :" + mWifiPreferenceDialog);
                    if (mWifiPreferenceDialog != null && mWifiPreferenceDialog.isShowing()) {
                        mWifiPreferenceDialog.dismiss();
                    }
                    mSwitch.setEnabled(false);
                    changeStateOfPreference(false);
                break;
            }
        }
    }


    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(
                   Context context, boolean enabled) {
                if (!FeatureOption.MTK_WFC_SUPPORT) return null;

                ArrayList<SearchIndexableResource> result = new ArrayList<SearchIndexableResource>();
                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.wfc_settings;
                result.add(sir);
                return result;
            }
        };

}
