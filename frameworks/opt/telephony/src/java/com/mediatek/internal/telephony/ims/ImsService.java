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

package com.mediatek.internal.telephony.ims;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;

import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.ImsServiceClass;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsService;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsConfigStub;
import com.mediatek.ims.ImsNotificationController;
import com.mediatek.ims.WfcReasonInfo;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
import com.mediatek.rns.RnsManager;

public class ImsService extends IImsService.Stub {
    private static final String LOG_TAG = "ImsService";
    private static final boolean DBG = true;
    private static final boolean VDBG = false; // STOPSHIP if true

    private ImsAdapter mImsAdapter = null;
    private ImsRILAdapter  mImsRILAdapter = null;
    //private CommandsInterface[] mCommandsInterfaceArray = null;
    private IImsCallSession mPendingMT = null;
    private Context mContext;

    private static ImsConfigStub sImsConfig = null;
    private static ImsUtStub sImsUtStub = null;

    private final Handler mHandler;
    private IImsRegistrationListener mListener = null;
    private int mImsRegInfo = ServiceState.STATE_POWER_OFF;
    private int mImsExtInfo = 0;
    private int mServiceId = 0;
    private int mImsState = PhoneConstants.IMS_STATE_DISABLED;
    private int mActivePhoneId = 0;
    ///M : WFC @{
    int mWfcRegErrorCode = 0;
    /// @}


    //***** Event Constants
    private static final int EVENT_IMS_REGISTRATION_INFO = 1;
    protected static final int EVENT_RADIO_NOT_AVAILABLE    = 2;
    protected static final int EVENT_SET_IMS_ENABLED_DONE   = 3;
    protected static final int EVENT_SET_IMS_DISABLE_DONE   = 4;
    protected static final int EVENT_IMS_DISABLED_URC   = 5;
    private static final int EVENT_VIRTUAL_SIM_ON = 6;
    protected static final int EVENT_INCOMING_CALL_INDICATION = 7;
    protected static final int EVENT_CALL_INFO_INDICATION = 8;
    protected static final int EVENT_CALL_RING = 9;
    protected static final int EVENT_IMS_ENABLING_URC   = 10;
    protected static final int EVENT_IMS_ENABLED_URC   = 11;
    protected static final int EVENT_IMS_DISABLING_URC   = 12;
    ///M : WFC @{
    protected static final int EVENT_SIP_CODE_INDICATION = 13;
    protected static final int EVENT_HANDOVER_DONE_INDICATION = 14;
    /// @}

    private static final int IMS_ALLOW_INCOMING_CALL_INDICATION = 0;
    private static final int IMS_DISALLOW_INCOMING_CALL_INDICATION = 1;
    ///M : WFC @{
    private static final int WFC_REFRESH_REG_TIMER = 3000 ; //(5ms)
    /// @}

    //***** IMS Feature Support
    private static final int IMS_VOICE_OVER_LTE = 1;
    private static final int IMS_RCS_OVER_LTE = 2;
    private static final int IMS_SMS_OVER_LTE = 4;
    private static final int IMS_VIDEO_OVER_LTE = 8;
    private static final int IMS_VOICE_OVER_WIFI = 16;

    //Refer to ImsConfig FeatureConstants
    private static final int IMS_MAX_FEATURE_SUPPORT_SIZE = 4;

    ///M : WFC @{
    private ImsNotificationController mNotificationController = null;
    /// @}


    /** events id definition */
    /// M: Simulate IMS Registration @{
    private boolean mImsRegistry = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            ///M : WFC @{
            if (RnsManager.CONNECTIVITY_ACTION_HANDOVER_END.equals(intent.getAction())) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        EVENT_HANDOVER_DONE_INDICATION, intent), WFC_REFRESH_REG_TIMER);
            } else if (TelephonyIntents.ACTION_NOTIFY_CONNECTION_ERROR.equals(intent.getAction())) {
                mWfcRegErrorCode = intent.getIntExtra(TelephonyIntents.EXTRA_ERROR_CODE, 0);
                Rlog.i(LOG_TAG, "Connectivity error updated : " + mWfcRegErrorCode);
            } else {
            /// @}
                mImsRegistry = intent.getBooleanExtra("registry", false);
                Rlog.w(LOG_TAG, "Simulate IMS Registration: " + mImsRegistry);
                int[] result = new int[] {
                    (mImsRegistry ? 1 : 0),
                    15,
                    mActivePhoneId};
                AsyncResult ar = new AsyncResult(null, result, null);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_IMS_REGISTRATION_INFO, ar));
            }
        }
    };
    /// @}

    private int translateRegErrors(int methodType, int errorCode) {
        // method_type:0 -- REGISTER message
        // method_type:9 -- SUBSCRIBE message
        switch(methodType) {
            case 0:
                switch(errorCode) {
                    case 40301:
                        return WfcReasonInfo.CODE_WFC_403_UNKNOWN_USER;
                    case 40302:
                        return WfcReasonInfo.CODE_WFC_403_ROAMING_NOT_ALLOWED;
                    case 40303:
                        return WfcReasonInfo.CODE_WFC_403_MISMATCH_IDENTITIES;
                    case 40304:
                        return WfcReasonInfo.CODE_WFC_403_AUTH_SCHEME_UNSUPPORTED;
                    case 40305:
                        return WfcReasonInfo.CODE_WFC_RNS_ALLOWED_RADIO_DENY;
                    default:
                        return WfcReasonInfo.CODE_WFC_ANY_OTHER_CONN_ERROR;
                }
            case 9:
                switch(errorCode) {
                    case 40303:
                        return WfcReasonInfo.CODE_WFC_911_MISSING;
                    default:
                        return WfcReasonInfo.CODE_WFC_ANY_OTHER_CONN_ERROR;
                }
            default:
                return WfcReasonInfo.CODE_WFC_ANY_OTHER_CONN_ERROR;

        }

    }

   

    public ImsService(Context context) {
        mImsAdapter = new ImsAdapter(context);
        mImsRILAdapter = new ImsRILAdapter(context);

        Rlog.d(LOG_TAG, " mImsRILAdapter= " + mImsRILAdapter);

        mContext = context;
        mHandler = new MyHandler();

        if (sImsConfig == null) {
            sImsConfig = new ImsConfigStub(mContext);
        }

        ServiceManager.addService("ims", this, true);

        mImsRILAdapter.registerForImsRegistrationInfo(mHandler, EVENT_IMS_REGISTRATION_INFO, null);
        mImsRILAdapter.registerForImsEnableStart(mHandler, EVENT_IMS_ENABLING_URC, null);
        mImsRILAdapter.registerForImsEnableComplete(mHandler, EVENT_IMS_ENABLED_URC, null);
        mImsRILAdapter.registerForImsDisableStart(mHandler, EVENT_IMS_DISABLING_URC, null);
        mImsRILAdapter.registerForImsDisableComplete(mHandler, EVENT_IMS_DISABLED_URC, null);
        mImsRILAdapter.setOnIncomingCallIndication(mHandler, EVENT_INCOMING_CALL_INDICATION, null);
        mImsRILAdapter.setOnCallRing(mHandler, EVENT_CALL_RING, null);
        /// M: WFC Modem will send the registration error in ESIPCPI @ {
        mImsRILAdapter.registerForCallProgressIndicator(mHandler, EVENT_SIP_CODE_INDICATION, null);
        ///  @}
        /// register for radio state changed
        mImsRILAdapter.registerForNotAvailable(mHandler, EVENT_RADIO_NOT_AVAILABLE, null);
        /// M: Simulate IMS Registration @{
        final IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_IMS_SIMULATE");


        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            /* Register for Content Observer */
            // TODO: where to unregister it: in close()??
            registerForWfcPreferenceChange(mHandler);
            /* Send wfc preference info to Modem */
            mImsRILAdapter.sendWfcProfileInfo(Settings.System.getInt(mContext.getContentResolver(),
                                        Settings.System.SELECTED_WFC_PREFERRENCE,
                                        0));
            mNotificationController = new ImsNotificationController(context, 1);
            Rlog.d(LOG_TAG, "noticontroller created" + mNotificationController);
            filter.addAction(RnsManager.CONNECTIVITY_ACTION_HANDOVER_END);
            filter.addAction(TelephonyIntents.ACTION_NOTIFY_CONNECTION_ERROR);
        }
        context.registerReceiver(mBroadcastReceiver, filter);
        /// @}
    }

    public void enableImsAdapter() {
        mImsAdapter.enableImsAdapter();
    }

    public void disableImsAdapter(boolean isNormalDisable) {
        mImsAdapter.disableImsAdapter(isNormalDisable);
        mImsState = PhoneConstants.IMS_STATE_DISABLED;
    }

    @Override
    public boolean isConnected(int serviceId, int serviceType, int callType) {
        /* temp solution: always return ImsAdapter if enable */
        return mImsAdapter.getImsAdapterEnable();
    }

    @Override
    public int open(int phoneId, int serviceClass, PendingIntent incomingCallIntent,
            IImsRegistrationListener listener) {
        if (mListener != null) {
            Rlog.e(LOG_TAG, "IMS: it did not close IMS servide before open() !!");
        }
        setRegistrationListener(1, listener);
        return 1;
    }

    @Override
    public void close(int serviceId) {
        // remove registration listener
        mListener = null;
    }

    @Override
    public boolean isOpened(int serviceId) {
        /* temp solution: always return ImsAdapter if enable */
        return mImsAdapter.getImsAdapterEnable();
    }

    /**
     * Used for turning on IMS when its in OFF state.
     */

    @Override
    public void turnOnIms(int phoneId) {
        Rlog.d(LOG_TAG, "turnOnIms, mActivePhoneId = " + mActivePhoneId +
            " phoneId = " + phoneId);
        phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
        Rlog.d(LOG_TAG, "turnOnIms, MainCapabilityPhoneId = " + phoneId);

        boolean isPhoneIdChanged = false;
        if (mActivePhoneId != phoneId) {
            mActivePhoneId = phoneId;
            isPhoneIdChanged = true;
        }
        mImsRILAdapter.turnOnIms(mHandler.obtainMessage(EVENT_SET_IMS_ENABLED_DONE));
        mImsState = PhoneConstants.IMS_STATE_ENABLING;
    }

    /**
     * Used for turning off IMS when its in ON state.
     * When IMS is OFF, device will behave as CSFB'ed.
     */
    @Override
    public void turnOffIms(int phoneId) {
        Rlog.d(LOG_TAG, "turnOffIms, mActivePhoneId = " + mActivePhoneId +
            " phoneId = " + phoneId);
        phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
        Rlog.d(LOG_TAG, "turnOffIms, MainCapabilityPhoneId = " + phoneId);

        boolean isPhoneIdChanged = false;
        if (mActivePhoneId != phoneId) {
            mActivePhoneId = phoneId;
            isPhoneIdChanged = true;
        }
        mImsRILAdapter.turnOffIms(mHandler.obtainMessage(EVENT_SET_IMS_DISABLE_DONE));
        mImsState = PhoneConstants.IMS_STATE_DISABLING;
    }

    @Override
    public void setRegistrationListener(int serviceId, IImsRegistrationListener listener) {
        mListener = listener;
        if (mImsRegInfo != ServiceState.STATE_POWER_OFF) {
            notifyRegistrationStateChange(mImsRegInfo);
        }
        if ((mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
            notifyRegistrationCapabilityChange(mImsExtInfo);
        }
    }

    @Override
    public ImsCallProfile createCallProfile(int serviceId, int serviceType, int callType) {
        return new ImsCallProfile(serviceType, callType);
    }

    @Override
    public IImsCallSession createCallSession(int serviceId, ImsCallProfile profile, IImsCallSessionListener listener) {
        // This API is for outgoing call to create IImsCallSession
        return new ImsCallSessionProxy(mContext, profile, listener, this, mImsRILAdapter);
    }

    @Override
    public IImsCallSession getPendingCallSession(int serviceId, String callId) {
        // This API is for incoming call to create IImsCallSession
        if (mPendingMT == null) {
            return null;
        }

        IImsCallSession pendingMT = mPendingMT;

        try {
            if (pendingMT.getCallId().equals(callId)) {
                mPendingMT = null;
                return pendingMT;
            }
        } catch (RemoteException e) {
            // error handling. Currently no-op
        }

        return null;
    }

    /**
     * Ut interface for the supplementary service configuration.
     */
    @Override
    public IImsUt getUtInterface(int serviceId) {
        if (sImsUtStub == null) {
            sImsUtStub = new ImsUtStub(mContext);
        }
        return sImsUtStub;
    }

    /**
     * Config interface to get/set IMS service/capability parameters.
     */
    @Override
    public IImsConfig getConfigInterface(int phoneId) {
        if (sImsConfig == null) {
            sImsConfig = new ImsConfigStub(mContext);
        }
        return sImsConfig;
    }

    /**
     * ECBM interface for Emergency Callback mode mechanism.
     */
    @Override
    public IImsEcbm getEcbmInterface(int serviceId) {
        /* leave blank */
        return new ImsEcbmProxy();
    }

    /**
      * Used to set current TTY Mode.
      */
    @Override
    public void setUiTTYMode(int serviceId, int uiTtyMode, Message onComplete) {
        return;
    }

    /**
     *call interface for allowing/refusing the incoming call indication send to App.
     *@hide
     */
    public void setCallIndication(String callId, int seqNum, boolean isAllow) {
        /* leave blank */
        if (isAllow) {
            ImsCallProfile imsCallProfile = new ImsCallProfile();
            if (mPendingMT != null) {
                try {
                    mPendingMT.close();
                } catch (RemoteException e) {
                    // error handling. Currently no-op
                    Rlog.e(LOG_TAG, "setCallIndication: can't close pending MT");
                }
            }
            mPendingMT = new ImsCallSessionProxy(mContext, imsCallProfile,
                    null, ImsService.this, mImsRILAdapter, callId);
            mImsRILAdapter.setCallIndication(IMS_ALLOW_INCOMING_CALL_INDICATION, Integer.parseInt(callId), seqNum);
        } else {
            mImsRILAdapter.setCallIndication(IMS_DISALLOW_INCOMING_CALL_INDICATION, Integer.parseInt(callId), seqNum);
        }
    }

    /**
     * Use to query ims enable/disable status.
     *@return ims status
     *@hide
     */
    public int getImsState() {
        return mImsState;
    }

    /**
     * Use to query ims registration information.
     *@return true if the ims is registered or false if the ims is unregistered.
     *@hide
     */
    public boolean getImsRegInfo(int phoneId) {
        if (phoneId != mActivePhoneId) {
            Rlog.d(LOG_TAG, "IMS: getImsRegInfo() phoneId = " + phoneId +
                " mActivePhoneId = " + mActivePhoneId);
            return false;
        }

        if (mImsRegInfo == ServiceState.STATE_IN_SERVICE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Use to query ims registration extension information.
     *@return string ims extension information.
     *@hide
     */
    public String getImsExtInfo() {
        return Integer.toHexString(mImsExtInfo);
    }

    /**
     * Use to query ims service state .
     *@return mImsRegInfo for service state information.
     *@hide
     */
    public int getImsServiceState() {
        return mImsRegInfo;
    }

    /**
     * Use to hang up all calls .
     *@hide
     */
    public void hangupAllCall() {
        mImsRILAdapter.hangupAllCall(null);
    }

    /**
     *sned the incoming call intent  to ImsPhoneCallTracker.
     *@hide
     */
    private void sendIncomingCallIndication(AsyncResult ar) {
        // +EAIC:<call_id>,<number>,<type>,<call_mode>,<seq_no>
        String callId = ((String[]) ar.result)[0];
        String dialString = ((String[]) ar.result)[1];
        String seqNum = ((String[]) ar.result)[4];
        int serviceId = 1;

        Rlog.d(LOG_TAG, "IMS: sendIncomingCallIndication() call_id = " + callId +
                " dialString = " +  dialString + " seqNum = " + seqNum);

        Intent intent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL_INDICATION);
        intent.putExtra(ImsManager.EXTRA_CALL_ID, callId);
        intent.putExtra(ImsManager.EXTRA_DIAL_STRING, dialString);
        intent.putExtra(ImsManager.EXTRA_SEQ_NUM, Integer.parseInt(seqNum));
        intent.putExtra(ImsManager.EXTRA_SERVICE_ID, serviceId);
        mContext.sendBroadcast(intent);
    }

    /**
     *sned the incoming call intent  to ImsPhoneCallTracker.
     *@hide
     */
    private void sendIncomingCallIntent() {
        String callId = null;
        String dialString = null;

        if (mPendingMT != null) {
            try {
                callId = mPendingMT.getCallId();
                dialString = mPendingMT.getCallProfile().getCallExtra(ImsCallProfile.EXTRA_OI);
            } catch (RemoteException e) {
                // error handling. Currently no-op
                Rlog.e(LOG_TAG, "Can't get call information from PendingMT !!!");
                return;
            }
            int serviceId = 1;

            Rlog.d(LOG_TAG, "IMS: sendIncomingCallIntent() call_id = " + callId + " dialString = " +  dialString);

            Intent intent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL);
            intent.putExtra(ImsManager.EXTRA_CALL_ID, callId);
            intent.putExtra(ImsManager.EXTRA_DIAL_STRING, dialString);
            intent.putExtra(ImsManager.EXTRA_SERVICE_ID, serviceId);
            mContext.sendBroadcast(intent);
        } else {
            Rlog.e(LOG_TAG, "PendingMT is null!!! Something wrong!");
        }
    }

    public ImsRILAdapter getImsRILAdapter() {
        if (mImsRILAdapter == null) {
            Rlog.d(LOG_TAG, "IMS: getImsRILAdapter, mImsRILAdapter is null ");
        }

        return mImsRILAdapter;
    }

    /**
     *notify upper application ims registration information.
     *@param imsRegInfo   the registration inforamtion.
     *@hide
     */
    private void notifyRegistrationStateChange(int imsRegInfo) {
        if (mListener == null) {
            return;
        }

        if (DBG) {
            Rlog.d(LOG_TAG, "IMS: notifyRegistrationStateChange imsRegInfo= " + imsRegInfo);
        }

        if (imsRegInfo == ServiceState.STATE_IN_SERVICE) {
            try {
                mListener.registrationConnected();
                ///M : WFC @{
                mWfcRegErrorCode = 0;
                /// @}
            } catch (RemoteException e) {
                // error handling. Currently no-op
            }
        } else {
            try {
                mListener.registrationDisconnected();
                ///WFC @{
                if (mWfcRegErrorCode == 0) {
                    ConnectivityManager cm = (ConnectivityManager) mContext.
                                getSystemService(Context.CONNECTIVITY_SERVICE);

                    mWfcRegErrorCode = cm.getDisconnectCause(
                            ConnectivityManager.TYPE_MOBILE_IMS);
                }
                mListener.registrationDisconnectedWithCause(mWfcRegErrorCode);
                /// @}
            } catch (RemoteException e) {
                // error handling. Currently no-op
            }
        }
    }

    /**
     *notify upper application ims feature capability information.
     *@param imsExtInfo   the ims feature capability inforamtion.
     *@hide
     */
    private void notifyRegistrationCapabilityChange(int imsExtInfo) {
        if (mListener == null) {
            return;
        }

        int[] enabledFeatures = new int[IMS_MAX_FEATURE_SUPPORT_SIZE];
        int[] disabledFeatures = new int[IMS_MAX_FEATURE_SUPPORT_SIZE];

        for (int i = 0; i < IMS_MAX_FEATURE_SUPPORT_SIZE; i++) {
            enabledFeatures[i] = -1;
            disabledFeatures[i] = -1;
        }

        if (mImsAdapter.getRatType() !=  ConnectivityManager.TYPE_EPDG &&
                (imsExtInfo & IMS_VOICE_OVER_LTE) == IMS_VOICE_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        }

        if ((imsExtInfo & IMS_VIDEO_OVER_LTE) == IMS_VIDEO_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE;
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE;
        }
        /// WFC @{
        if (mImsAdapter.getRatType() ==  ConnectivityManager.TYPE_EPDG) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] =

                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
            Rlog.d(LOG_TAG, "[WFC]Parul IMS_VOICE_OVER_WIFI");
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] =
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;

        }
        ///@}
        // currently modem not support
        disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI] =
                ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI;

        try {
            mListener.registrationFeatureCapabilityChanged(ImsServiceClass.MMTEL,
                    enabledFeatures, disabledFeatures);
        } catch (RemoteException e) {
            // error handling. Currently no-op
        }
    }

    /*
    * Observes WFC preference settings changes .
    */
    private void registerForWfcPreferenceChange(Handler handler) {
        ContentObserver contentObserver = new ContentObserver(handler) {

            @Override
            public void onChange(boolean selfChange) {
                this.onChange(selfChange, Settings.System.getUriFor(Settings
                        .System.SELECTED_WFC_PREFERRENCE));
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                Uri i = android.provider.Settings.System.getUriFor(Settings
                        .System.SELECTED_WFC_PREFERRENCE);
                if (DBG) Rlog.d(LOG_TAG, "uri:" + uri + ", db_uri:" + i);
                if (DBG) Rlog.d(LOG_TAG, "wfc_preference:" + Settings.System
                        .getInt(mContext.getContentResolver(),
                        Settings.System.SELECTED_WFC_PREFERRENCE, 0));
                if (i != null && i.equals(uri)) {
                    /* send change to modem */
                    mImsRILAdapter.sendWfcProfileInfo(Settings.System.getInt(mContext
                            .getContentResolver(), Settings.System.SELECTED_WFC_PREFERRENCE,
                            0));
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SELECTED_WFC_PREFERRENCE),
                false, contentObserver);
    }

    /**
     *Ims service Message hanadler.
     *@hide
     */
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            Intent intent;
            switch (msg.what) {
                case EVENT_IMS_REGISTRATION_INFO:
                    if (DBG) Rlog.d(LOG_TAG, "receive EVENT_IMS_REGISTRATION_INFO");

                    /**
                     * According to 3GPP TS 27.007 +CIREGU format
                     *
                     * AsyncResult.result is an Object[]
                     * ((Object[])AsyncResult.result)[0] is integer type to indicate the IMS regiration status.
                     *                                    0: not registered
                     *                                    1: registered
                     * ((Object[])AsyncResult.result)[1] is numeric value in hexadecimal format to indicate the IMS capability.
                     *                                    1: RTP-based transfer of voice according to MMTEL (see 3GPP TS 24.173 [87])
                     *                                    2: RTP-based transfer of text according to MMTEL (see 3GPP TS 24.173 [87])
                     *                                    4: SMS using IMS functionality (see 3GPP TS 24.341[101])
                     *                                    8: RTP-based transfer of video according to MMTEL (see 3GPP TS 24.183 [87])
                     *
                     */
                    ar = (AsyncResult) msg.obj;
                    /// M: Fix DSDS bug
                    /// Do not notify AP when socket_id from RILD is not equal the active phone id. @{
                    int socketId = ((int[]) ar.result)[2];
                    if (socketId != mActivePhoneId) {
                        Rlog.d(LOG_TAG, "IMS: drop IMS reg info, socketId = " + socketId + 
                            " mActivePhoneId = " + mActivePhoneId);
                        break;
                    }
                    /// @}

                    int newImsRegInfo = ServiceState.STATE_POWER_OFF;
                    if (((int[]) ar.result)[0] == 1) {
                        newImsRegInfo = ServiceState.STATE_IN_SERVICE;
                    } else {
                        newImsRegInfo = ServiceState.STATE_OUT_OF_SERVICE;
                    }
                    /// M: Simulate IMS Registration @{
                    if (SystemProperties.getInt("persist.ims.simulate", 0) == 1) {
                        newImsRegInfo = (mImsRegistry ?
                                ServiceState.STATE_IN_SERVICE : ServiceState.STATE_OUT_OF_SERVICE);
                        Rlog.d(LOG_TAG, "Override EVENT_IMS_REGISTRATION_INFO: newImsRegInfo=" +
                                newImsRegInfo);
                    }
                    /// @}
                    int newImsExtInfo = ((int[]) ar.result)[1];

                    /* notify upper application the IMS registration status is chagned */
                    if (DBG) {
                        Rlog.d(LOG_TAG, "newReg:" + newImsRegInfo + " oldReg:" + mImsRegInfo);
                    }

                    mImsRegInfo = newImsRegInfo;
                    notifyRegistrationStateChange(mImsRegInfo);

                    /* notify upper application the IMS capability is chagned when IMS is registered */
                    if (DBG) {
                        Rlog.d(LOG_TAG, "newRegExt:" + newImsExtInfo + "oldRegExt:" + mImsExtInfo);
                    }

                    if ((mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
                        mImsExtInfo = newImsExtInfo;
                        notifyRegistrationCapabilityChange(mImsExtInfo);
                    }
                    break;
                case EVENT_IMS_ENABLING_URC:
                    //+EIMS: 1
                    //Since MD doens't send IMS_ENABLED_URC to AP we should handle initial here.
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_IMS_ENABLING_URC, mActivePhoneId = "
                                + mActivePhoneId);
                    }
                    // notify AP Ims Service is up
                    intent = new Intent(ImsManager.ACTION_IMS_SERVICE_UP);
                    intent.putExtra(ImsManager.EXTRA_PHONE_ID, mActivePhoneId);
                    mContext.sendBroadcast(intent);
                    // enable ImsAdapter
                    enableImsAdapter();
                    mImsState = PhoneConstants.IMS_STATE_ENABLE;
                    if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
                        mImsRILAdapter.sendWfcProfileInfo(Settings.System.getInt(mContext
                                .getContentResolver(), Settings.System.SELECTED_WFC_PREFERRENCE,
                                0));
                    }
                    break;
                case EVENT_IMS_ENABLED_URC:
                    //+EIMCFLAG: 1
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_IMS_ENABLED_URC");
                    }
                    break;
                case EVENT_IMS_DISABLING_URC:
                    //+EIMS: 0
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_IMS_DISABLING_URC");
                    }
                    break;
                case EVENT_IMS_DISABLED_URC:
                    //+EIMCFLAG: 0
                    if (DBG) Rlog.d(LOG_TAG, "receive EVENT_IMS_DISABLED_URC");
                    int phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
                    Rlog.d(LOG_TAG, "IMS: phoneId = " + phoneId);
                    intent = new Intent(ImsManager.ACTION_IMS_SERVICE_DOWN);
                    intent.putExtra(ImsManager.EXTRA_PHONE_ID, phoneId);
                    mContext.sendBroadcast(intent);

                    disableImsAdapter(true);
                    mImsState = PhoneConstants.IMS_STATE_DISABLED;
                    break;
                case EVENT_SET_IMS_ENABLED_DONE:
                    // Only log for tracking set ims enable command response
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_SET_IMS_ENABLED_DONE");
                    }
                    break;
                case EVENT_SET_IMS_DISABLE_DONE:
                    // Only log for tracking set ims disable command response
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_SET_IMS_DISABLE_DONE");
                    }
                    break;
                case EVENT_INCOMING_CALL_INDICATION:
                    Rlog.d(LOG_TAG, "receive EVENT_INCOMING_CALL_INDICATION");
                    ar = (AsyncResult) msg.obj;
                    sendIncomingCallIndication(ar);
                    break;
                case EVENT_CALL_RING:
                    Rlog.d(LOG_TAG, "receive EVENT_CALL_RING");
                    break;
                case EVENT_RADIO_NOT_AVAILABLE:
                    Rlog.d(LOG_TAG, "receive EVENT_RADIO_NOT_AVAILABLE");
                    mContext.sendBroadcast(new Intent(ImsManager.ACTION_IMS_SERVICE_DOWN));
                    disableImsAdapter(false);
                    break;
                case EVENT_SIP_CODE_INDICATION:
                    ar = (AsyncResult) msg.obj;
                    int[] sipMessage = (int[]) ar.result;
                    /* ESIPCPI: <call_id>,<dir>,<SIP_msg_type>,<method>,<response_code> */
                    if (sipMessage != null) {
                        Rlog.d(LOG_TAG, "Method =" + sipMessage[3] + "Reg cause =" + sipMessage[4]);
                        if (sipMessage[3] == 0 ||
                                sipMessage[3] == 9) {
                            /* Save the WFC registration error for later use */
                            mWfcRegErrorCode = translateRegErrors(sipMessage[3], sipMessage[4]);
                        }
                    }
                    break;

                case EVENT_HANDOVER_DONE_INDICATION:
                    intent = (Intent) msg.obj;
                    boolean result = intent.getBooleanExtra(
                            RnsManager.EXTRA_HANDOVER_RESULT, false);
                    Rlog.d(LOG_TAG, "EVENT_HANDOVER_DONE_INDICATION result = " + result);
                    if (result && (mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
                        notifyRegistrationCapabilityChange(mImsExtInfo);
                    }

                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public int getRegistrationStatus() {
        if (mNotificationController == null) return WfcReasonInfo.CODE_WFC_DEFAULT;
        return mNotificationController.getRegistrationStatus();
    }
}
