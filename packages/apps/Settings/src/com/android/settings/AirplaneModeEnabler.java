/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;


import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyProperties;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.settings.SubscriberPowerStateListener;
import com.mediatek.settings.SubscriberPowerStateListener.onRadioPowerStateChangeListener;
import com.mediatek.settings.cdma.CdmaAirplaneModeManager;


import com.mediatek.internal.telephony.ITelephonyEx;

public class AirplaneModeEnabler implements Preference.OnPreferenceChangeListener {

    private final Context mContext;
    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private final SwitchPreference mSwitchPref;

    private static final int EVENT_SERVICE_STATE_CHANGED = 3;

    
    /// M : add for Bug fix ALPS01772247
    private static final String TAG = "AirplaneModeEnabler";
    private SubscriberPowerStateListener mListener;
    private CdmaAirplaneModeManager mCdmaAirModeManager;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SERVICE_STATE_CHANGED:
                    onAirplaneModeChanged();
                    break;
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
            if (mSwitchPref.isChecked() != isAirplaneModeOn(mContext)) {
                Log.d(TAG, "airplanemode changed by others, update UI...");
                onAirplaneModeChanged();
            }
        }
    };

    public AirplaneModeEnabler(Context context, SwitchPreference airplaneModeSwitchPreference) {
        mContext = context;
        mSwitchPref = airplaneModeSwitchPreference;

        airplaneModeSwitchPreference.setPersistent(false);

        mPhoneStateReceiver = new PhoneStateIntentReceiver(mContext, mHandler);
        mPhoneStateReceiver.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);

    }
    // Only for phone, tablet no need to monitor SIM state
    private void initListener(Context context) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mCdmaAirModeManager = new CdmaAirplaneModeManager(mContext, mSwitchPref);
        } else {
            if (!Utils.isWifiOnly(mContext)) {
                mListener = new SubscriberPowerStateListener(context);
                mListener.setRadioPowerStateChangeListener(new onRadioPowerStateChangeListener() {
                    @Override
                    public void onAllPoweredOff() {
                        mSwitchPref.setEnabled(true);
                    }
                    @Override
                    public void onAllPoweredOn() {
                        mSwitchPref.setEnabled(true);
                    }
                });
            }
        }

    }

    public void resume() {
        mSwitchPref.setChecked(isAirplaneModeOn(mContext));
        mPhoneStateReceiver.registerIntent();
        mSwitchPref.setOnPreferenceChangeListener(this);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);

        // M: For CDMA LTE only
        if (mCdmaAirModeManager != null) {
            mCdmaAirModeManager.setEnable();
            mCdmaAirModeManager.registerBroadCastReceiver();
        }
        //


        /// M: for [Enhanced Airplane Mode] @{
        mSwitchPref.setEnabled(isAirplaneModeAvailable());
        IntentFilter intentFilter = new IntentFilter(ACTION_AIRPLANE_CHANGE_DONE);
        mContext.registerReceiver(mReceiver, intentFilter);
        /// @}

    }

    public void pause() {
        mPhoneStateReceiver.unregisterIntent();
        mSwitchPref.setOnPreferenceChangeListener(null);
        mContext.getContentResolver().unregisterContentObserver(mAirplaneModeObserver);

        if (mCdmaAirModeManager != null) {
            mCdmaAirModeManager.unRegisterBroadCastReceiver();
        }
    
        /// M: for [Enhanced Airplane Mode] @{
        mContext.unregisterReceiver(mReceiver);
        /// @}
    }


    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void setAirplaneModeOn(boolean enabling) {
        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                                enabling ? 1 : 0);
        // Update the UI to reflect system setting
        mSwitchPref.setChecked(enabling);

        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabling);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }   

    //Only for phone, tablet no need to register
    private void registerSubState() {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            /*HQ_yuankangbo 2015-08-04 modify for airplane mode on/off start*/
//            mSwitchPref.setEnabled(false); 
        } else {
            if (!Utils.isWifiOnly(mContext)) {
                /*HQ_yuankangbo 2015-08-04 modify for airplane mode on/off start*/
//                mSwitchPref.setEnabled(false);
                if (mListener != null) {
                    mListener.registerListener();
                }
            }
        }

        /// M: for [Enhanced Airplane Mode]
        // disable the switch to prevent quick click until switch is done
        mSwitchPref.setEnabled(false);

    }

    /**
     * Called when we've received confirmation that the airplane mode was set.
     * TODO: We update the checkbox summary when we get notified
     * that mobile radio is powered up/down. We should not have dependency
     * on one radio alone. We need to do the following:
     * - handle the case of wifi/bluetooth failures
     * - mobile does not send failure notification, fail on timeout.
     */
    private void onAirplaneModeChanged() {
        mSwitchPref.setChecked(isAirplaneModeOn(mContext));

        /// M: for [Enhanced Airplane Mode]
        mSwitchPref.setEnabled(isAirplaneModeAvailable());
    }

    /**
     * Called when someone clicks on the checkbox preference.
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode, do not update database at this point
        }else if ((Boolean) newValue) {
            /*HQ_yuankangbo 2015-08-04 modify for airplane mode on/off start*/
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(mContext.getString(com.android.settings.R.string.airplane_mode));
            builder.setMessage(mContext.getString(com.android.settings.R.string.openairplane));
            builder.setPositiveButton(
                mContext.getString(com.android.settings.R.string.airline_ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int which) {
                            setAirplaneModeOn(true);
                        }
                    });
            builder.setNegativeButton(
                mContext.getString(com.android.settings.R.string.airline_cancle),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int which) {
                            /* HQ_xuqian4 20151104 modified for HQ01484395 */
                            //mSwitchPref.setChecked(false);
                            /* HQ_xuqian4 20151105 modified for HQ01484395 begin*/
                            if(isAirplaneModeOn(mContext)) {
                                setAirplaneModeOn(false);
                            } else {
                                mSwitchPref.setChecked(false);
                            }
                            /* HQ_xuqian4 20151105 modified end*/
                        }
                    });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                     mSwitchPref.setChecked(false);
                }
            });
            builder.show();
            /*HQ_yuankangbo 2015-08-04 modify for airplane mode on/off end*/
        } else {
            setAirplaneModeOn((Boolean) newValue);
        }
        return true;
    }

    public void setAirplaneModeInECM(boolean isECMExit, boolean isAirplaneModeOn) {
        if (isECMExit) {
            // update database based on the current checkbox state
            setAirplaneModeOn(isAirplaneModeOn);
        } else {
            // update summary
            onAirplaneModeChanged();
        }
    }

    ///-------------------------------------------------MTK---------------------------------------
   // private static final String TAG = "AirplaneModeEnabler";

    /// M: for [Enhanced Airplane Mode] @{
    private static final String ACTION_AIRPLANE_CHANGE_DONE
                                    = "com.mediatek.intent.action.AIRPLANE_CHANGE_DONE";
    private static final String EXTRA_AIRPLANE_MODE = "airplaneMode";
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(ACTION_AIRPLANE_CHANGE_DONE.equals(action)) {
                boolean airplaneMode = intent.getBooleanExtra(EXTRA_AIRPLANE_MODE, false);
                Log.d(TAG, "onReceive, ACTION_AIRPLANE_CHANGE_DONE, " + airplaneMode);
                mSwitchPref.setEnabled(isAirplaneModeAvailable());
            }
        }
    };

    private boolean isAirplaneModeAvailable() {
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        boolean isAvailable = false;
        try {
            if (telephonyEx != null) {
                isAvailable = telephonyEx.isAirplanemodeAvailableNow();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isAirplaneModeAvailable = " + isAvailable);
        return isAvailable;
    }
    /// @}
}
