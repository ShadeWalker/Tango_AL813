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

package com.mediatek.ims;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.internal.telephony.ITelephonyEx;


public class ImsNotificationController {

    BroadcastReceiver mBr = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (DBG) Log.d(TAG, "Intent action:" + intent.getAction());
            
            // Restore screen lock state, even if intent received may not provide its effect
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mIsScreenLock = true;
                handleScreenOff();
            } else {
                mIsScreenLock = mKeyguardManager.isKeyguardLocked();
            }
            if (DBG) Log.d(TAG, "on receive:screen lock:" + mIsScreenLock);

            if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(TAG, "simState:" + simState);
                if (simState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)
                        || simState.equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                    setSimAppType();
                } else if (simState.equals(IccCardConstants.INTENT_VALUE_ICC_NOT_READY) ||
                        simState.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
                    // reset to "1" to avoid showing any error before Isim presence can be read from SIM
                    // SIM can be read only on SIM_LOADED(if sim present) or SIM_ABSENT(if sim not present)
                    SystemProperties.set(KEY_ISIM_PRESENT, "1");
                }
            }
            
            if (Settings.System.getInt(context.getContentResolver(),
                    Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                    TelephonyManager.WifiCallingChoices.NEVER_USE)
                    == TelephonyManager.WifiCallingChoices.NEVER_USE) {
                if (DBG) Log.d(TAG, "WFC off, return");
                removeWfcNotification();
                return;
            }

            if (isWifiEnabled() /*&& !isIsimAppPresent()*/) {
                // if Wifi enabled, check if sim have isim application
                if (!isIsimAppPresent()) {
                    if (DBG) Log.d(TAG, "ISIM not present");
                    mImsState = WfcReasonInfo.CODE_WFC_INCORRECT_SIM_CARD_ERROR;
                    mWfcCapabilityPresent = false;
                    displayWfcErrorNotification(false);
                    return;
                } else if (isIsimAppPresent()
                        && mImsState == WfcReasonInfo.CODE_WFC_INCORRECT_SIM_CARD_ERROR) {
                    // Remove the SIM_CARD_ERROR, when sim state change to ISIM present
                    if (DBG) Log.d(TAG, "ISIM now present, remove SIM_error notification");
                    mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
                    mWfcCapabilityPresent = false;
                    removeWfcNotification();
                }
            }

            if (!isWifiEnabled() || !isWifiConnected()) {
                if (DBG) Log.d(TAG, "Wifi off or not connected, return");
                removeWfcNotification();
                return;
            }

            if (intent.getAction().equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
                handleImsStateChange(intent);
            } else if (intent.getAction().equals(ImsManager.ACTION_IMS_CAPABILITIES_CHANGED)) {
                handleCapabilityChange(intent);
            } else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                handleCallIntent(intent);
            } else if (intent.getAction().equals(ImsManager.ACTION_IMS_SERVICE_DOWN)) {
                removeWfcNotification();
            } else if (intent.getAction().equals(TelephonyIntents.ACTION_NOTIFY_CONNECTION_ERROR)) {
                handleConnectionError(intent);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                handleScreenOn();
            } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                handleScreenUnlock();
            }
        }
    };

    private static final boolean DBG = true;
    private static final String TAG = "ImsNotificationController";
    private static final String KEY_ISIM_PRESENT = "persist.sys.wfc_isimAppPresent";
    private static final String ACTION_LAUNCH_WFC_SETTINGS = "mediatek.settings.WFC_SETTINGS";
    private static final String ACTION_LAUNCH_WFC_INVALID_SIM_ALERT
            = "mediatek.settings.WFC_INVALID_SIM_DIALOG_LAUNCH";    
    /**
    * Wfc registration notification ID. This is 
     * the ID of the Notification given to the NotificationManager.
     * Note: Id should be unique within APP.
     */
    private static final int WFC_NOTIFICATION = 0x10;

    private static final int WFC_REGISTERED_ICON =
            com.mediatek.internal.R.drawable.wfc_notify_registration_success;
    private static final int WFC_ERROR_ICON =
            com.mediatek.internal.R.drawable.wfc_notify_registration_error;
    private static final int WFC_CALL_ICON =
            com.mediatek.internal.R.drawable.wfc_notify_ongoing_call;

    private static final int WFC_REGISTERED_TITLE =
            com.mediatek.internal.R.string.success_notification_title;
    private static final int WFC_ERROR_TITLE =
            com.mediatek.internal.R.string.network_error_notification_title;
    private static final int WFC_CALL_TITLE =
            com.mediatek.internal.R.string.ongoing_call_notification_title;

    private static final int WFC_REGISTERED_SUMMARY =
            com.mediatek.internal.R.string.success_notification_summary;
    private static final int WFC_ERROR_SUMMARY =
            com.mediatek.internal.R.string.success_notification_summary;

    // Current WFC state.
    // Can be: 1) Success: WFC registered (2) DEFAULT: WFC on but not registered
    // (3) Various error codes: defined in WfcReasonInfo
    private int mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;

    private boolean mWfcCapabilityPresent = false;
    private boolean mWfcCallOngoing = false;
    private boolean mIsScreenLock = false;

    /*  Vars required for ImsNotificationController initialization */
    private Context mContext;
    private long mSubId;

    private NotificationManager mNotificationManager;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private KeyguardManager mKeyguardManager;
    private ContentObserver mWfcSwitchContentObserver;

    public ImsNotificationController(Context context, long subId) {
        if (DBG) Log.d(TAG, "in constructor: subId:" + subId);
        mContext = context;
        mSubId = subId;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        mIsScreenLock =  mKeyguardManager.isKeyguardLocked();
        if (DBG) Log.d(TAG, "in constructor, screen lock:" + mIsScreenLock);
        registerReceiver();
        registerForWfcSwitchChange();
    }

    public void stop() {
        if (DBG) Log.d(TAG, "in destroy Instance");
        unRegisterReceiver();
        unRegisterForWfcSwitchChange();
        /* Cancel visible notifications, if any */
        mNotificationManager.cancelAll();
    }

    private void registerReceiver() {
        if (DBG) Log.d(TAG, "in register receiver");
        IntentFilter filter = new IntentFilter(ImsManager.ACTION_IMS_STATE_CHANGED);
        filter.addAction(ImsManager.ACTION_IMS_CAPABILITIES_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_NOTIFY_CONNECTION_ERROR);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(ImsManager.ACTION_IMS_SERVICE_DOWN);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mBr, filter);
    }

    private void unRegisterReceiver() {
        if (DBG) Log.d(TAG, "in unregister receiver");
        mContext.unregisterReceiver(mBr);
    }

    private void handleCallIntent(Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        int phone_type = intent.getIntExtra(PhoneConstants.PHONE_TYPE_KEY, RILConstants.NO_PHONE);
        if (DBG) Log.d(TAG, "in handleCallIntent, phone state:" + state);
        if (DBG) Log.d(TAG, "in handleCallIntent, phone type:" + phone_type);
        if (phone_type == RILConstants.IMS_PHONE) {
            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                    || TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                mWfcCallOngoing = true;
                displayWfcCallNotification();
            } else {
                mWfcCallOngoing = false;
                displayWfcRegistrationNotification(false);
            }
        } else if (phone_type == RILConstants.GSM_PHONE && mWfcCallOngoing) {
            mWfcCallOngoing = false;
            displayWfcRegistrationNotification(false);
        }
    }

    private void handleImsStateChange(Intent intent) {
        if (intent.getAction().equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
            mImsState = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY,
                    ServiceState.STATE_OUT_OF_SERVICE);
            if (DBG) Log.d(TAG, "in handleImsStateChange:" + mImsState);
            /* 1. Turn off capability flag, as capability change intent will not be received after turning IMS OFF or error
             *  2. If State == STATE_IN_SERVICE, wait for Capabaility change intent
             */
            if (mImsState == ServiceState.STATE_OUT_OF_SERVICE) {
                int errorCode = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_ERROR_KEY,
                        WfcReasonInfo.CODE_WFC_DEFAULT);
                if (DBG) Log.d(TAG, "in out_of_service, error_extra:" + errorCode);
                // If error is RNS error, no action
                if (!isErrorValid(errorCode)) {
                    mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
                    if (DBG) Log.d(TAG, "invalid error code");
                    //return;
                } else mImsState = errorCode;
                
                mWfcCapabilityPresent = false;
                if (mImsState == WfcReasonInfo.CODE_WFC_DEFAULT)
                    removeWfcNotification();
                else 
                    displayWfcErrorNotification(true);
            } else if (mImsState == ServiceState.STATE_IN_SERVICE) {
                /* Set as SUCCESS only when capabilty received is wifi in capabilty_change  intent */
                mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
            }
        }
        if (DBG) Log.d(TAG, "exit handleImsStateChange, imsState:" + mImsState);
    }

    private void handleCapabilityChange(Intent intent) {
        if (DBG) Log.d(TAG, "in handleCapabilityChange");
        /*handle for registration icon*/
        int[] enabledFeatures = intent.getIntArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
        if (DBG) Log.d(TAG, "wifi capability:" + enabledFeatures[ImsConfig.FeatureConstants
                .FEATURE_TYPE_VOICE_OVER_WIFI]);
        if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
            mWfcCapabilityPresent = true;
            /* Capabilities have been change to WIFI, so set wfc status as Success. It is done to cover handover
             * cases in which IMS_STATE_CHANGE is not received before capability_change intent
            */
            mImsState = WfcReasonInfo.CODE_WFC_SUCCESS;
            // ALPS02187200: Query phone state to check whether UE is in Call when capability change to Wifi.
            // This case can happen during handover from LTE to Wifi when call is ongoing.
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            // TODO: for multiSim
            if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                displayWfcRegistrationNotification(true);
            }
            else {
                mWfcCallOngoing = true;
                displayWfcCallNotification();
            }
        } else {
            mWfcCapabilityPresent = false;
            /* Capabilities have been change to other than WIFI, so set wfc status as OFF */
            mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
            removeWfcNotification();
        }
    }

    private void handleConnectionError(Intent intent) {
        int errorCode = intent.getIntExtra(TelephonyIntents.EXTRA_ERROR_CODE,
                WfcReasonInfo.CODE_WFC_DEFAULT);
        if (DBG) Log.d(TAG, "in handleConnectionError, error:" + errorCode);
        if (!isErrorValid(errorCode)) {
            //mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
            if (DBG) Log.d(TAG, "invalid error code, return");
            return;
        }
        mImsState = errorCode;
        mWfcCapabilityPresent = false;
        displayWfcErrorNotification(true);
    }

    // Listening screen off intent because no intent for screen lock present in SDK now
    // So, treating screen Off as screen lock
    // Remove notification, if screen off
    private void handleScreenOff() {
        //mIsScreenLock = true;
        //if (DBG) Log.d(TAG, "screen off, removing notification");
        mNotificationManager.cancel(WFC_NOTIFICATION);
    }
    
    // Screen on but check if screen is locked or not. If unlocked, show notification.
    private void handleScreenOn() {
        //mIsScreenLock = mKeyguardManager.isKeyguardLocked();
        //if (DBG) Log.d(TAG, "screen lock:" + mIsScreenLock);
        if (!mIsScreenLock) {
            if (DBG) Log.d(TAG, "screen not locked & screen on, show notification");
            showNotification();
        }
    }

    // Intent received when user unlocks. Show notification.
    private void handleScreenUnlock() {
        //mIsScreenLock = false;
        //if (DBG) Log.d(TAG, "screen unlocked");
        showNotification();
    }

    private void showNotification() {
        if (mWfcCallOngoing) {
            displayWfcCallNotification();
        } else if (mWfcCapabilityPresent) {
            displayWfcRegistrationNotification(false);
        } else if (mImsState > WfcReasonInfo.CODE_WFC_DEFAULT) {
           displayWfcErrorNotification(false); 
        }
    }
    
    private void displayWfcCallNotification() {
        if (DBG) Log.d(TAG, "in call handling, screen lock:" + mIsScreenLock);
        if (!mIsScreenLock && mImsState == WfcReasonInfo.CODE_WFC_SUCCESS && mWfcCapabilityPresent) {
            // TODO: to handle fake SRVCC case(wfc registered but during call setup it goes on CS), need RAT type of call setup
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(WFC_CALL_TITLE))
                    .setSmallIcon(WFC_CALL_ICON)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build();
            mNotificationManager.notify(WFC_NOTIFICATION, noti);
            if (DBG) Log.d(TAG, "showing wfc call notification");
        }
    }

    private void displayWfcRegistrationNotification(boolean showTicker) {
        if (DBG) Log.d(TAG, "in registration handling, screen lock:" + mIsScreenLock);
        if (!mIsScreenLock && mImsState == WfcReasonInfo.CODE_WFC_SUCCESS && mWfcCapabilityPresent
            && mWfcCallOngoing == false) {
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(WFC_REGISTERED_TITLE))
                    .setContentText(mContext.getResources().getString(WFC_REGISTERED_SUMMARY))
                    .setSmallIcon(WFC_REGISTERED_ICON)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build();
            if (showTicker) {
                noti.tickerText = mContext.getResources().getString(WFC_REGISTERED_TITLE);
            }
            Intent intent = new Intent(ACTION_LAUNCH_WFC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            noti.contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
            noti.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(WFC_NOTIFICATION, noti);
            if (DBG) Log.d(TAG, "showing wfc registration notification");
        }
    }

    
    private void displayWfcErrorNotification(boolean showTicker) {
        if (DBG) Log.d(TAG, "in error handling, screen lock:" + mIsScreenLock);
        if (!mIsScreenLock && mImsState > WfcReasonInfo.CODE_WFC_DEFAULT) {
            if (DBG) Log.d(TAG, "WFC error:" + mImsState);
            // TMO requires only some erros to be shown on notification bar
            // TODO: removed this check as per Elieen's suggestion[05/04/2015]
            /*if (!doShowNotification(mImsState)) {
                if (DBG) Log.d(TAG, "Do not show error notification for error:" + mImsState);
                mNotificationManager.cancel(WFC_NOTIFICATION);
                return;
            }*/

            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(WFC_ERROR_TITLE))
                    .setContentText(mContext.getResources().getString(WfcReasonInfo
                            .getImsStatusCodeString(mImsState)))
                    .setSmallIcon(WFC_ERROR_ICON)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build();
            if (showTicker) {
                noti.tickerText = mContext.getResources().getString(WFC_ERROR_TITLE);
            }
            Intent intent;
            if (mImsState == WfcReasonInfo.CODE_WFC_INCORRECT_SIM_CARD_ERROR) {
                intent = new Intent(ACTION_LAUNCH_WFC_INVALID_SIM_ALERT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(ACTION_LAUNCH_WFC_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            noti.contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
            noti.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(WFC_NOTIFICATION, noti);
            if (DBG) Log.d(TAG, "showing wfc error notification");
            mWfcCallOngoing = false;
        }
    }

    private void removeWfcNotification() {
        if (DBG) Log.d(TAG, "removeWfcNotification: removing wfc notification, if any");
        mNotificationManager.cancel(WFC_NOTIFICATION);
        mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
        mWfcCapabilityPresent = false;
        mWfcCallOngoing = false;
    }

    /* Whether a error code is error condition for WFC notification or not
     * RNS errors are not considered as error conditions by WFC notification
     * so blocking them
     */
    private boolean isErrorValid(int errorCode) {
        switch(errorCode) {
            case WfcReasonInfo.CODE_WFC_WIFI_SIGNAL_LOST:
            case WfcReasonInfo.CODE_WFC_UNABLE_TO_COMPLETE_CALL:
            case WfcReasonInfo.CODE_WFC_NO_AVAILABLE_QUALIFIED_MOBILE_NETWORK:
            case WfcReasonInfo.CODE_WFC_UNABLE_TO_COMPLETE_CALL_CD:
            case WfcReasonInfo.CODE_WFC_RNS_ALLOWED_RADIO_DENY:
            case WfcReasonInfo.CODE_WFC_RNS_ALLOWED_RADIO_NONE:
                return false;
            default:
                return true;
        }
    }

     /* Whether a error is to be shown as notification or not.
     * TMO requires not all errors to be shown on notification panel.
     */
    private boolean doShowNotification(int errorCode) {
        switch(errorCode) {
            case WfcReasonInfo.CODE_WFC_DNS_RECV_NAPTR_QUERY_RSP_ERROR:
            case WfcReasonInfo.CODE_WFC_DNS_RECV_RSP_SRV_QUERY_ERROR:
            case WfcReasonInfo.CODE_WFC_DNS_RECV_RSP_QUERY_ERROR:
            case WfcReasonInfo.CODE_WFC_DNS_RESOLVE_FQDN_ERROR:
            case WfcReasonInfo.CODE_WFC_LOCAL_OR_NULL_PTR_ERROR:
            case WfcReasonInfo.CODE_WFC_EPDG_CON_OR_LOCAL_OR_NULL_PTR_ERROR:
            case WfcReasonInfo.CODE_WFC_EPDG_IPSEC_SETUP_ERROR:
            case WfcReasonInfo.CODE_WFC_TLS_CONN_ERROR:
            case WfcReasonInfo.CODE_WFC_SERVER_CERT_VALIDATION_ERROR:
            case WfcReasonInfo.CODE_WFC_SERVER_IPSEC_CERT_VALIDATION_ERROR:
            case WfcReasonInfo.CODE_WFC_SERVER_IPSEC_CERT_INVALID_ERROR:
            case WfcReasonInfo.CODE_WFC_SERVER_CERT_INVALID_ERROR:
            case WfcReasonInfo.CODE_WFC_ANY_OTHER_CONN_ERROR:
                return false;
            default:
                return true;
        }
    }

    public int getRegistrationStatus() {
        return mImsState;
    }

    public void setRegistrationStatus(int status) {
        if (DBG) Log.d(TAG, "new wfc state:" + status);
        mImsState = status;
    }

    private void setSimAppType() {
        ITelephonyEx telEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        boolean iSimPresent = true;
        try {
            iSimPresent = telEx.isAppTypeSupported(SubscriptionManager.getDefaultVoicePhoneId(),
                PhoneConstants.APPTYPE_ISIM);
        } catch(RemoteException e) {
            Log.d(TAG, "ItelephonyEx exceptio:" + e);
            iSimPresent = false;
        }
        String iSimAppPresent = iSimPresent ? "1":"0";
        SystemProperties.set(KEY_ISIM_PRESENT, iSimAppPresent);
        Log.d(TAG, "persist.sys.wfc_isimAppPresent:" + SystemProperties.get(KEY_ISIM_PRESENT));
    }

    private boolean isIsimAppPresent() {
        if (DBG) Log.d(TAG, "isimApp present:" + SystemProperties.get(KEY_ISIM_PRESENT));
        if (SystemProperties.get(KEY_ISIM_PRESENT).equals("1")) return true;
        else return false;
    }

    private boolean isWifiEnabled() {
        int wifiState = mWifiManager.getWifiState();
        if (DBG) Log.d(TAG, "wifi state:" + wifiState);
        return (wifiState != WifiManager.WIFI_STATE_DISABLED);
    }

    private boolean isWifiConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null) {
            if (DBG) Log.d(TAG, "networkInfo:" + networkInfo.isConnected());
            if (DBG) Log.d(TAG, "networkInfo:" + networkInfo.getDetailedState());
        }
        return (networkInfo != null && (networkInfo.isConnected()
                    || networkInfo.getDetailedState() == DetailedState.CAPTIVE_PORTAL_CHECK));
    }

    // Observes WFC settings changes. Needed for cases when WFC is switch OFF but
    //state_changes intent is received. Ex: WFC error & user switches WCF OFF.
    private void registerForWfcSwitchChange() {
        mWfcSwitchContentObserver = new ContentObserver(new Handler()) {

            @Override
            public void onChange(boolean selfChange) {
                this.onChange(selfChange, Settings.System.getUriFor(Settings
                        .System.WHEN_TO_MAKE_WIFI_CALLS));
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (DBG) Log.d(TAG, "uri:" + uri);
                if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                    TelephonyManager.WifiCallingChoices.NEVER_USE)
                    == TelephonyManager.WifiCallingChoices.NEVER_USE) {
                    if (DBG) Log.d(TAG, "ImsNotificationController: contentObserver:WFC OFF");
                    removeWfcNotification();
                } else {
                    if (isWifiEnabled() && !isIsimAppPresent()) {
                       // if Wifi enabled, check if sim have isim application
                        if (DBG) Log.d(TAG, "ISIM not present");
                        mImsState = WfcReasonInfo.CODE_WFC_INCORRECT_SIM_CARD_ERROR;
                        mWfcCapabilityPresent = false;
                        displayWfcErrorNotification(true);
                    }
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.WHEN_TO_MAKE_WIFI_CALLS),
                false, mWfcSwitchContentObserver);
    }
    
    private void unRegisterForWfcSwitchChange() {
        mContext.getContentResolver().unregisterContentObserver(mWfcSwitchContentObserver);
        mWfcSwitchContentObserver = null;
    }
}

