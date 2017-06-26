/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.dataconnection.DctController.DcStateParam;
import com.android.internal.telephony.dataconnection.DcSwitchStateMachine;

import com.mediatek.internal.telephony.ims.ImsService;
import com.mediatek.internal.telephony.IRadioPower;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;

public class ImsSwitchController extends Handler  {
    static final String LOG_TAG = "ImsSwitchController";

    private Context mContext;
    private CommandsInterface[] mCi;
    private int mPhoneCount;
    private static ImsService mImsService = null;
    private DcStateParam mDcStateParam = null;
    private RadioPowerInterface mRadioPowerIf;
    private boolean mIsInVoLteCall = false;

    protected final Object mLock = new Object();

    /** events id definition */
    protected static final int EVENT_RADIO_NOT_AVAILABLE_PHONE1    = 1;
    protected static final int EVENT_RADIO_AVAILABLE_PHONE1        = 2;
    protected static final int EVENT_RADIO_NOT_AVAILABLE_PHONE2    = 3;
    protected static final int EVENT_RADIO_AVAILABLE_PHONE2        = 4;
    protected static final int EVENT_DC_SWITCH_STATE_CHANGE        = 5;
    
    static final int DEFAULT_MAJOR_CAPABILITY_PHONE_ID    = 0;
    static final int DEFAULT_IMS_STATE = 0;
    static final int DEFAULT_PHONE_ID = 0;
    
    ImsSwitchController(Context context , int phoneCount, CommandsInterface[] ci) {

        log("Initialize ImsSwitchController");

        mContext = context;
        mCi = ci;
        mPhoneCount = phoneCount;

        // For TC1, do not use MTK IMS stack solution
        if (SystemProperties.get("ro.mtk_ims_support").equals("1") &&
            !SystemProperties.get("ro.mtk_tc1_feature").equals("1")) {

            IntentFilter intentFilter = new IntentFilter(ImsManager.ACTION_IMS_SERVICE_DOWN);
            intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

            intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);

            if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
                intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            }
            mContext.registerReceiver(mIntentReceiver, intentFilter);
            mRadioPowerIf = new RadioPowerInterface();
            RadioManager.registerForRadioPowerChange(LOG_TAG, mRadioPowerIf);
            
            mImsService = new ImsService(context);
            ServiceManager.addService(ImsManager.IMS_SERVICE, mImsService.asBinder());

            mCi[PhoneConstants.SIM_ID_1].registerForNotAvailable(this, EVENT_RADIO_NOT_AVAILABLE_PHONE1, null);
            mCi[PhoneConstants.SIM_ID_1].registerForAvailable(this, EVENT_RADIO_AVAILABLE_PHONE1, null);

            if (mPhoneCount > PhoneConstants.SIM_ID_2) {
                mCi[PhoneConstants.SIM_ID_2].registerForNotAvailable(this, EVENT_RADIO_NOT_AVAILABLE_PHONE2, null);
                mCi[PhoneConstants.SIM_ID_2].registerForAvailable(this, EVENT_RADIO_AVAILABLE_PHONE2, null);
            }
            if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
                registerForWfcSwitchChange();
           }
        }
    }
    
    class RadioPowerInterface implements IRadioPower {
        public void notifyRadioPowerChange(boolean power, int phoneId) {
            if (SystemProperties.get("ro.mtk_ims_support").equals("1")) {
                int imsSetting = android.provider.Settings.Global.getInt(
                        mContext.getContentResolver(),
                        android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED,
                        ImsConfig.FeatureValueConstants.ON);
                
                log("notifyRadioPowerChange, imsSetting:" + imsSetting + " power:" + power + 
                        " phoneId:" + phoneId);

                if(RadioCapabilitySwitchUtil.getMainCapabilityPhoneId() == phoneId) {
                    if(imsSetting == 0) {
                        switchImsCapability(false, phoneId);
                    } else {
                        switchImsCapability(power, phoneId);
                    }
                }
            }
        }
    }
    
    private void switchImsCapability(boolean on, int phoneId) {
        log("switchImsCapability, on:" + on + " phoneId:" + phoneId);
        if(mImsService != null) {
            if(on) {
                mImsService.turnOnIms(phoneId);
            } else {

                int wfcSettings = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                        TelephonyManager.WifiCallingChoices.ALWAYS_USE);;

                if (!SystemProperties.get("ro.mtk_wfc_support").equals("1") ||
                        wfcSettings == TelephonyManager.WifiCallingChoices.NEVER_USE) {
                    mImsService.turnOffIms(phoneId);
                }
            }
        }
    }

    private void registerEvent() {
        log("registerEvent, major phoneid:" + RadioCapabilitySwitchUtil.getMainCapabilityPhoneId());

        DctController.getInstance().registerForDcSwitchStateChange(this, EVENT_DC_SWITCH_STATE_CHANGE, null,
                DctController.getInstance().new DcStateParam(LOG_TAG, true));
        //RadioManager.getInstance().registerForRadioPowerChange(LOG_TAG, mRadioPowerIf);
        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            onReceiveWifiStateChange(wifiManager.getWifiState());
        }
    }

    private void unregisterEvent() {
        log("unregisterEvent, major phoneid:" + RadioCapabilitySwitchUtil.getMainCapabilityPhoneId());
        DctController.getInstance().unregisterForDcSwitchStateChange(this);
        //RadioManager.getInstance().unregisterForRadioPowerChange(mRadioPowerIf);
    }

    private void handleDcStateAttaching(DcStateParam param) {
        synchronized (mLock) {
            log("handleDcStateAttaching param.getPhoneId():" + param.getPhoneId());
            // Enable / Disable IMS on the phone with PS capability
            int imsSetting = android.provider.Settings.Global.getInt(
                    mContext.getContentResolver(), android.provider.Settings
                    .Global.ENHANCED_4G_MODE_ENABLED, ImsConfig.FeatureValueConstants.ON);
            log("handleDcStateAttaching, param.getPhoneId():" + param.getPhoneId() +
                    " imsSetting:" + imsSetting);
            if( imsSetting == 1
                    && param.getPhoneId() == RadioCapabilitySwitchUtil.getMainCapabilityPhoneId()) {
                switchImsCapability(true, param.getPhoneId());
            }
        }
    }

    private void handleDcStatePreCheckDisconnect(DcStateParam param) {
        synchronized (mLock) {
            if(mIsInVoLteCall == true) {
                log("handleDcStatePreCheckDisconnect, in volte call, suspend DcState preCheck");
                mDcStateParam = param;
                return;
            }

            int imsSetting = android.provider.Settings.Global.getInt(
                    mContext.getContentResolver(),
                    android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED,
                    ImsConfig.FeatureValueConstants.ON);
            log("handleDcStatePreCheckDisconnect, param.getPhoneId():" + param.getPhoneId() +
                    " imsSetting:" + imsSetting);

            if( imsSetting == 1
                    && param.getPhoneId() == RadioCapabilitySwitchUtil.getMainCapabilityPhoneId()) {
                if (mImsService != null) {

                    int state = mImsService.getImsState();

                    if (state != PhoneConstants.IMS_STATE_DISABLED) {
                        mImsService.turnOffIms(param.getPhoneId());
                        mDcStateParam = param;
                    } else {
                        log("handleDcStatePreCheckDisconnect: ims is disable and confirm directly");
                        param.confirmPreCheckDetach();
                    }
                } else {
                    log("handleDcStatePreCheckDisconnect: ImsService not ready !!!");
                    mDcStateParam = param;
                }

            } else {
                param.confirmPreCheckDetach();
            }
        }
    }

    private void onReceiveDcSwitchStateChange(DcStateParam param) {
        log("handleMessage param.getState: " + param.getState() + " param.getReason(): " + param.getReason());
        switch (param.getState()) {
            case DcSwitchStateMachine.DCSTATE_PREDETACH_CHECK:
                handleDcStatePreCheckDisconnect(param);
                break;
            case DcSwitchStateMachine.DCSTATE_ATTACHING:
                if(!param.getReason().equals("Lost Connection")) {
                    handleDcStateAttaching(param);
                }
                break;
            default:
                break;
        }
    }

    private void onReceivePhoneStateChange(int phoneId, int phoneType, PhoneConstants.State phoneState) {
        synchronized (mLock) {
            log("onReceivePhoneStateChange phoneId:" + phoneId +
                    " phoneType: " + phoneType + " phoneState: " + phoneState);
            log("mIsInVoLteCall: " + mIsInVoLteCall);


            if(mIsInVoLteCall == true) {
                if(phoneType == RILConstants.IMS_PHONE &&
                        phoneState == PhoneConstants.State.IDLE) {
                    mIsInVoLteCall = false;
                    if(mDcStateParam != null) {
                        if (mImsService != null) {
                            mImsService.turnOffIms(mDcStateParam.getPhoneId());
                        }
                    }
                }
            }else {
                if(phoneType == RILConstants.IMS_PHONE && 
                        !(phoneState == PhoneConstants.State.IDLE)) {
                    mIsInVoLteCall = true;
                }
            }
        }
    }

    private void confirmPreCheckDetachIfNeed() {
        synchronized (mLock) {
            if(mDcStateParam != null){
                log("confirmPreCheckDetachIfNeed, phoneId:" + mDcStateParam.getPhoneId());
                mDcStateParam.confirmPreCheckDetach();
                mDcStateParam = null;
            }
        }
    }
    
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            log("mIntentReceiver Receive action " + action);

            if (action.equals(ImsManager.ACTION_IMS_SERVICE_DOWN)) {
                confirmPreCheckDetachIfNeed();
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(PhoneConstants.STATE_KEY);
                onReceivePhoneStateChange(
                        intent.getIntExtra(PhoneConstants.PHONE_KEY, DEFAULT_PHONE_ID),
                        intent.getIntExtra(PhoneConstants.PHONE_TYPE_KEY, RILConstants.NO_PHONE),
                        Enum.valueOf(PhoneConstants.State.class, state));
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                onReceiveWifiStateChange(wifiState);

            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE)) {
                String state = DctController.getInstance().getDcSwitchState(
                        RadioCapabilitySwitchUtil.getMainCapabilityPhoneId());
                int imsSetting = android.provider.Settings.Global.getInt(
                        mContext.getContentResolver(), android.provider.Settings
                        .Global.ENHANCED_4G_MODE_ENABLED, ImsConfig.FeatureValueConstants.ON);
                log("handle ACTION_SET_RADIO_CAPABILITY_DONE: data state:" + state +
                        " imsSetting:" + imsSetting);
                
                if (imsSetting == 1 && (state.equals(DcSwitchStateMachine.DCSTATE_ATTACHING) ||
                        state.equals(DcSwitchStateMachine.DCSTATE_ATTACHED))) {
                    switchImsCapability(true,
                            RadioCapabilitySwitchUtil.getMainCapabilityPhoneId());
                }

            }
        }
    };

    private String eventIdtoString(int what) {
        String str = null;
        switch (what) {
            case EVENT_RADIO_NOT_AVAILABLE_PHONE1:
                str = "RADIO_NOT_AVAILABLE_PHONE1";
                break;
            case EVENT_RADIO_NOT_AVAILABLE_PHONE2:
                str = "RADIO_NOT_AVAILABLE_PHONE2";
                break;
            case EVENT_RADIO_AVAILABLE_PHONE1:
                str = "RADIO_AVAILABLE_PHONE1";
                break;
            case EVENT_RADIO_AVAILABLE_PHONE2:
                str = "RADIO_AVAILABLE_PHONE2";
                break;
            case EVENT_DC_SWITCH_STATE_CHANGE:
                str = "DC_SWITCH_STATE_CHANGE";
                break;
            default:
                break;

        }
        return str;
    }
    
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        log("handleMessage msg.what: " + eventIdtoString(msg.what));
        int phoneId = 0;
        switch (msg.what) {
            case EVENT_RADIO_NOT_AVAILABLE_PHONE1:
                phoneId = PhoneConstants.SIM_ID_1;
                if(RadioCapabilitySwitchUtil.getMainCapabilityPhoneId() == phoneId) {
                    unregisterEvent();
                }
                break;
            case EVENT_RADIO_NOT_AVAILABLE_PHONE2:
                phoneId = PhoneConstants.SIM_ID_2;
                if(RadioCapabilitySwitchUtil.getMainCapabilityPhoneId() == phoneId) {
                    unregisterEvent();
                }
                break;
            case EVENT_RADIO_AVAILABLE_PHONE1:
                phoneId = PhoneConstants.SIM_ID_1;
                if(RadioCapabilitySwitchUtil.getMainCapabilityPhoneId() == phoneId) {
                    registerEvent();
                }
                break;
            case EVENT_RADIO_AVAILABLE_PHONE2:
                phoneId = PhoneConstants.SIM_ID_2;
                if(RadioCapabilitySwitchUtil.getMainCapabilityPhoneId() == phoneId) {
                    registerEvent();
                }
                break;
            case EVENT_DC_SWITCH_STATE_CHANGE:
                DcStateParam param = (DcStateParam)ar.result;
                onReceiveDcSwitchStateChange(param);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void registerForWfcSwitchChange() {
        ContentObserver contentObserver = new ContentObserver(this) {

            @Override
            public void onChange(boolean selfChange) {
                onChange(selfChange, Settings.System.getUriFor(Settings
                        .System.WHEN_TO_MAKE_WIFI_CALLS));
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {

                int wfcSetting = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                        TelephonyManager.WifiCallingChoices.ALWAYS_USE);

                int imsSetting = android.provider.Settings.Global.getInt(
                        mContext.getContentResolver(),
                        android.provider.Settings.Global.ENHANCED_4G_MODE_ENABLED,
                        ImsConfig.FeatureValueConstants.ON);
                int phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
                log("Wfc switch change wfcSetting=" + wfcSetting + " imsSetting=  " + imsSetting);

                if (wfcSetting == TelephonyManager.WifiCallingChoices.NEVER_USE
                    && imsSetting == 0) {
                    switchImsCapability(false, phoneId);
                } else  {
                    switchImsCapability(true, phoneId);
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(Settings.System
                .getUriFor(Settings.System.WHEN_TO_MAKE_WIFI_CALLS), false, contentObserver);
    }

    private void onReceiveWifiStateChange(int wifiState) {
        log("onReceiveWifiStateChange wifiState = " + wifiState);
        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            int wfcSetting = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                    TelephonyManager.WifiCallingChoices.ALWAYS_USE);
           if (wfcSetting != TelephonyManager.WifiCallingChoices.NEVER_USE) {
              switchImsCapability(true, RadioCapabilitySwitchUtil.getMainCapabilityPhoneId());
           }
        }
    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
