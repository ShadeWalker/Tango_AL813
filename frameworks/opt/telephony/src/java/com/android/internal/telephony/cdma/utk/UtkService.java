/*
* This Software is the property of VIA Telecom, Inc. and may only be used pursuant to a
license from VIA Telecom, Inc.
* Any unauthorized use inconsistent with the terms of such license is strictly prohibited.
* Copyright (c) 2013 -2015 VIA Telecom, Inc. All rights reserved.
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

package com.android.internal.telephony.cdma.utk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;

import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
//import android.telephony.SubscriptionManager;
//import android.util.Config;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
//import com.android.internal.telephony.cdma.SmsMessage;
import com.android.internal.telephony.cdma.utk.LocalInfo;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.cdma.IUtkService;
import com.mediatek.internal.telephony.ltedc.IRilDcArbitrator;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.RoamingMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;



import java.io.ByteArrayOutputStream;

/**
 * Class for message from ril.
 *
 * {@hide}
 */
class RilMessage {
    int mId;
    Object mData;
    ResultCode mResCode;

    RilMessage(int msgId, String rawData) {
        mId = msgId;
        mData = rawData;
    }

    RilMessage(RilMessage other) {
        this.mId = other.mId;
        this.mData = other.mData;
        this.mResCode = other.mResCode;
    }
}

class UtkTimerManagementData {
    private int mId = 0;
    private byte[] mBcdData = null;
    private long mRemaining = -1;
    private Handler mCaller = null;

    UtkTimerManagementData(Handler caller, int id, byte[] data) {
        UtkLog.d("UtkTimerManagementData", " id=" + id);

        mId = id;
        mCaller = caller;

        if (data != null && data.length == 3) {
            mBcdData = new byte[3];
            System.arraycopy(data, 0, mBcdData, 0, 3);

            byte[] digit = UtkService.bcdToDigit(data);

            UtkLog.d("UtkTimerManagementData", " bcd=" + IccUtils.bytesToHexString(data));
            if (digit != null) {
                UtkLog.d("UtkTimerManagementData", " convert to digit="
                        + digit[0] + digit[1] + digit[2]);

                mRemaining = (long) (digit[0] * 3600) + (long) (digit[1] * 600) + (long) (digit[2]);
            }
        }

        UtkLog.d("UtkTimerManagementData", " mRemaining=" + (int) mRemaining);
    }

    public void timerTick() {
        if (mRemaining >= 0) {
            mRemaining--; //-1 invalid!
        }
        if (mRemaining == 0) {
            handleTimerExpiration();
        }
    }

    public long getRemaining() { return mRemaining; }
    public int getTimerId() { return mId; }

    private void handleTimerExpiration() {

        UtkLog.d("UtkTimerManagementData", "handleTimerExpiration=" + mId);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        buf.write(BerTlv.BER_TIMER_EXPIRATION_TAG);

        // length
        buf.write(0x00); // place holder, assume length < 128.

        // device identities
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
        buf.write(0x02); // length
        buf.write(0x82); // source device id
        buf.write(0x81); // destination device id

        // timer identifier
        buf.write(ComprehensionTlvTag.TIMER_IDENTIFIER.value());
        buf.write(0x01); // length
        buf.write(mId); // timer identifier

        // timer value
        if (mBcdData != null) {
            buf.write(ComprehensionTlvTag.TIMER_VALUE.value());
            buf.write(0x03); // length
            // value
            buf.write(mBcdData, 0, 3);
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        //mCmdIf.sendEnvelope(hexString, null);
        Message m = mCaller.obtainMessage(
            UtkService.MSG_ID_TIMER_MANAGEMENT_TIMEOUT, 0, 0, hexString);
        mCaller.sendMessage(m);
    }
}

/**
 * Class that implements RUIM Toolkit Telephony Service. Interacts with the RIL
 * and application.
 *
 * {@hide}
 */
public class UtkService extends Handler implements AppInterface, IUtkService {
    private static final String LOG_TAG = "UtkService";

    // Class members
    //private static IccRecords mIccRecords;
    //private static UiccCardApplication mUiccApplication;
    private static IccRecords mIccRecordsSim1;
    private static UiccCardApplication mUiccApplicationSim1;
    private static IccRecords mIccRecordsSim2;
    private static UiccCardApplication mUiccApplicationSim2;
    private UiccController mUiccController = null;
    private int mSimId;

    private static final Object sInstanceLock = new Object();
    
    // Service members.
    private static UtkService sActiveInstance;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private UtkCmdMessage mCurrntCmd = null;
    private UtkCmdMessage mMenuCmd = null;
    //Fix Local issue
    private UtkCmdMessage mCatchLocalInfoCmd = null;

    //Fix Open channel issue
    private UtkCmdMessage mCatchChannelCmd = null;
    
    // WP solution 2 modification
    private static final String WP2_LOG_TAG = "Wp2UtkService";
    private int mPhoneId;
    private static UtkService sInstanceSim1;
    private static UtkService sInstanceSim2;
    private static int sActiveUtkId = -1;

    private RilMessageDecoder mMsgDecoder = null;

    private LocalInfo mLocalInfo = new LocalInfo();
    private static boolean LTE_UTK_DBG = true;
    private int mMutliSimType = -1;
    private static LteDcPhoneProxy sLteDcPhoneProxy = null;
    private static PhoneBase sLtePhone;
    private static IRilDcArbitrator sRilDcArbitrator = null;
    private CommandsInterface mLteCmdIf = null;
    public static final int UTK_CARD_TYPE_UNKNOW = 0;
    public static final int UTK_CARD_TYPE_UIM_ONLY = 1;
    public static final int UTK_CARD_TYPE_UIM_AND_USIM = 2;
    public static final int UTK_TR = 1;
    public static final int UTK_ENV = 2;
    public static final int UTK_PS_TR = 1;
    public static final int UTK_NPS_TR = 2;
    public static final int UTK_PS_ENV = 3;
    public static final int UTK_NPS_ENV = 4;
    public static int sLtePhoneProxyId = 0;
    public static int sCdmaPhoneId = 0;
    public static boolean mQueryMenuFlag = false;
    private static RoamingMode sRoamingMode = RoamingMode.ROAMING_MODE_HOME;
    // Service constants.
    static final int MSG_ID_SESSION_END              = 1;
    static final int MSG_ID_PROACTIVE_COMMAND        = 2;
    static final int MSG_ID_EVENT_NOTIFY             = 3;
    static final int MSG_ID_CALL_SETUP               = 4;
    static final int MSG_ID_REFRESH                  = 5;
    static final int MSG_ID_RESPONSE                 = 6;
    static final int MSG_ID_RUIM_READY               = 7;
    static final int MSG_ID_EVENT_DOWNLOAD           = 8;
    static final int MSG_ID_RIL_MSG_DECODED          = 10;
    static final int MSG_ID_ICC_CHANGED              = 11;

    static final int MSG_ID_CMD_LOCAL_INFO           = 12;
    static final int MSG_ID_RIL_REFRESH_RESULT       = 13;
    static final int MSG_ID_EVENT_LOCAL_INFO         = 14;

    static final int MSG_ID_RADIO_OFF_OR_UNAVAILABLE = 15;

    private boolean mRilMsgDecoding = false;
    private ArrayList<RilMessage> mPendingRilMsgList = new ArrayList<RilMessage>();

    //bip start
    private byte[] mEventList;
    private BipService mBipService = null;
    final int UTK_TIMER_MAX = 8;

    private HashMap<Integer, UtkTimerManagementData> mTimerManagementHash =
                                              new HashMap<Integer, UtkTimerManagementData>();
    private Object mTimerLock = new Object();
    private Timer mUtkTicker = null;

    private ServiceStateReceiver mServiceReceiver = null;
    private int mServiceState = 2; //out of service
    private String mLocationStatusString = null;

    public static final int MSG_ID_OPENED_CHANNEL = 20;
    public static final int MSG_ID_SENT_DATA = 21;
    public static final int MSG_ID_RECEIVED_DATA = 22;
    public static final int MSG_ID_CLOSED_CHANNEL = 23;
    public static final int MSG_ID_GET_CHANNEL_STATUS = 24;
    public static final int MSG_ID_TIMER_TICK = 25;
    public static final int MSG_ID_TIMER_MANAGEMENT_TIMEOUT = 26;

    private static final int UTK_TIMEUPDATE_PERIOD = 1000;  // ms

    static final int EVENT_LIST_MT_CALL = 0x00;
    static final int EVENT_LIST_CALL_CONNECTED = 0x01;
    static final int EVENT_LIST_CALL_DISCONNECTED = 0x02;
    static final int EVENT_LIST_LOCATION_STATUS = 0x03;
    static final int EVENT_LIST_USER_ACTIVITY = 0x04;
    static final int EVENT_LIST_IDLE_SCREEN_AVAILABLE = 0x05;
    static final int EVENT_LIST_CARD_READER_STATUS = 0x06;
    static final int EVENT_LIST_LANGUAGE_SELECTION = 0x07;
    static final int EVENT_LIST_BROWSER_TERMINATION = 0x08;
    static final int EVENT_LIST_DATA_AVAILABLE = 0x09;
    static final int EVENT_LIST_CHANNEL_STATUS = 0x0A;
    //bip end
    public static final int MSG_ID_MENU_INFO = 30;

    private static final int DEV_ID_KEYPAD      = 0x01;
    private static final int DEV_ID_DISPLAY     = 0x02;
    private static final int DEV_ID_EARPIECE    = 0x03;
    private static final int DEV_ID_UICC        = 0x81;
    private static final int DEV_ID_TERMINAL    = 0x82;
    private static final int DEV_ID_NETWORK     = 0x83;
    public static final int ENVELOPE_MENU_SELECTION = 0xFF;

    static final String[] UICCCARD_PROPERTY_RIL_UICC_TYPE = {
        "gsm.ril.uicctype",
        "gsm.ril.uicctype.2",
        "gsm.ril.uicctype.3",
        "gsm.ril.uicctype.4",
    };

    static final String UTK_DEFAULT = "Defualt Message";
    //Added for Operator 
    private static final String mEsnTrackUtkMenuSelect =
        "com.android.internal.telephony.cdma.utk.ESN_MENU_SELECTION";
    //Add for world phone feature
    private final IntentFilter mSIMStateChangeFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
    private final BroadcastReceiver mSIMStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                int mSlotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1);
                UtkLog.d(this, "mSIMStateChangeReceiver() - slotId[" + mSlotId + "]");
                if (mSlotId == mPhoneId) {
                    String mSimState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    UtkLog.d(this, "mSIMStateChangeReceiver() - mSimState[" + mSimState + "]");
                    if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(mSimState)) {
                        unRegisterLteRilEvents();
                    }
                }
            }
        }
    };

    //Add for UTK IR case
    private final IntentFilter mIRStateChangeFilter = new IntentFilter(
            SvlteRatController.INTENT_ACTION_FINISH_SWITCH_ROAMING_MODE);

    private final BroadcastReceiver mIRStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SvlteRatController.INTENT_ACTION_FINISH_SWITCH_ROAMING_MODE.equals(intent.getAction())) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
                UtkLog.d(this, "received IR state changed broadcast at phone " + phoneId);
                if (phoneId == mPhoneId) {
                    if (sLteDcPhoneProxy == null) {
                        UtkLog.d(this, "sLteDcPhoneProxy is null and ignore this event");
                        return;
                    }
                    SvlteRatController sLteRatController = sLteDcPhoneProxy.getSvlteRatController();
                    RoamingMode mRoamingMode = null;
                    if (sLteRatController != null) {
                        mRoamingMode = sLteRatController.getRoamingMode();
                        UtkLog.d(this, "mRoamingMode = " + mRoamingMode +
                                                            "  sRoamingMode = " + sRoamingMode);
                        if ((RoamingMode.ROAMING_MODE_NORMAL_ROAMING != mRoamingMode) &&
                            (RoamingMode.ROAMING_MODE_NORMAL_ROAMING == sRoamingMode)) {
                            queryUtkSetupMenuFromMD();
                        }
                        sRoamingMode = mRoamingMode;
                    }
                }
            }
        }
    };

    /* Intentionally private for singleton */
    private UtkService(CommandsInterface ci, UiccCardApplication ca, IccRecords ir,
            Context context, IccFileHandler fh, UiccCard ic) {

        UtkLog.d(LOG_TAG, " ci" + ci + " ca " + ca + " ir " + ir + " fh " + fh + " ic " + ic);
        
        if (ci == null || ca == null || ir == null || context == null || fh == null
                || ic == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }

        mCmdIf = ci;
        mContext = context;
        //mIccRecords = ir;
        mPhoneId = ic.getPhoneId();
        //mSimId = ca.getMySimId();
        UtkLog.d(LOG_TAG, " UtkService constructor " + mPhoneId);
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (LTE_UTK_DBG) {
                mMutliSimType = getMutliSimType();
                UtkLog.d(LOG_TAG, " mMutliSimType " + mMutliSimType);
            }
        }

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = RilMessageDecoder.getInstance(this, fh, mPhoneId);

        mBipService = BipService.getInstance(context, this, mPhoneId);
        mServiceReceiver = new ServiceStateReceiver();
        IntentFilter intent = new IntentFilter();
        intent.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        mContext.registerReceiver(mServiceReceiver, intent);

        // Register ril events handling.
        //mUiccController = UiccController.getInstance(mSimId);
        mUiccController = UiccController.getInstance();
        if (mUiccController != null) {
            mUiccController.registerForIccChanged(this, MSG_ID_ICC_CHANGED, null);
            UtkLog.d(this, "mUiccController != null, register for icc change successly");
        } else {
            UtkLog.d(this, "mUiccController = null, cant register for icc change");
        }

        // WPS2 modification
        if (PhoneConstants.SIM_ID_1 == mPhoneId) {
            mIccRecordsSim1 = ir;
            mUiccApplicationSim1 = ca;
            mUiccApplicationSim1.registerForReady(this, MSG_ID_RUIM_READY, null);
        } else {
            mIccRecordsSim2 = ir;
            mUiccApplicationSim2 = ca;
            mUiccApplicationSim2.registerForReady(this, MSG_ID_RUIM_READY, null);
        }
        //mUiccApplication = ca;
        //mUiccApplication.registerForReady(this, MSG_ID_RUIM_READY, null);

        mCmdIf.setOnUtkSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnUtkProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnUtkEvent(this, MSG_ID_EVENT_NOTIFY, null);
        //mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);
        //Add for radio off case
        mCmdIf.registerForOffOrNotAvailable(this, MSG_ID_RADIO_OFF_OR_UNAVAILABLE, null);

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            //registerLteRilEvents();
            //mContext.registerReceiver(mIRStateChangeReceiver, mIRStateChangeFilter);
            //Add for world phone
            mContext.registerReceiver(mSIMStateChangeReceiver, mSIMStateChangeFilter);

            mContext.registerReceiver(mIRStateChangeReceiver, mIRStateChangeFilter);
        }

        mCmdIf.reportUtkServiceIsRunning(null);
        UtkLog.d(this, "UtkService v1.9.0 is running");
    }

    /**
     * Handles UtkService dispose.
     */
    public void dispose() {
        UtkLog.d(this, "dispose+");

        Intent intent = new Intent("android.intent.action.utk.service_dispose");
        mContext.sendBroadcast(intent);

        stopUtkTimer();

        if (mBipService != null) {
          mBipService.dispose();
        }

        mCmdIf.unSetOnUtkSessionEnd(this);
        mCmdIf.unSetOnUtkProactiveCmd(this);
        mCmdIf.unSetOnUtkEvent(this);

        mContext.unregisterReceiver(mServiceReceiver);

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            //mLteCmdIf.unSetOnUtkSessionEnd(this);
            //mLteCmdIf.unSetOnUtkProactiveCmd(this);
            //mLteCmdIf.unSetOnUtkEvent(this);
            unRegisterLteRilEvents();
            //Add for world phone
            mContext.unregisterReceiver(mSIMStateChangeReceiver);
            mContext.unregisterReceiver(mIRStateChangeReceiver);
        }
        if (mMsgDecoder != null) {
            UtkLog.d(WP2_LOG_TAG, "mMsgDecoder dispose");
            mMsgDecoder.dispose(mPhoneId);
            mMsgDecoder = null;
        }
        if (mUiccController != null) {
            mUiccController.unregisterForIccChanged(this);
            mUiccController = null;
        }
        UtkLog.d(LOG_TAG, " dispose UtkService instance" + mPhoneId);
        if ((PhoneConstants.SIM_ID_1 == mPhoneId) && (mUiccApplicationSim1 != null)) {
            mUiccApplicationSim1.unregisterForReady(this);
            mUiccApplicationSim1 = null;
        } else if ((PhoneConstants.SIM_ID_2 == mPhoneId) && (mUiccApplicationSim2 != null)) {
            mUiccApplicationSim2.unregisterForReady(this);
            mUiccApplicationSim2 = null;
        } else {
            UtkLog.d(WP2_LOG_TAG, "invalid ca dispose");
        }
        this.removeCallbacksAndMessages(null);

        //sInstance = null;
        // WPS2 modification
        if (PhoneConstants.SIM_ID_1 == mPhoneId) {
            sInstanceSim1 = null;
        } else if (PhoneConstants.SIM_ID_2 == mPhoneId) {
            sInstanceSim2 = null;
        } else {
            UtkLog.d(WP2_LOG_TAG, "invalid utk dispose");
        }
        sActiveInstance = null;
        UtkLog.d(WP2_LOG_TAG, "dispose-");
    }

    protected void finalize() {
        UtkLog.d(this, "Service finalized");
    }

    public Context getContext() {
        return mContext;
    }

    private void updateIccStatus(int index) {
        UtkLog.d(this, "updateIccStatus");
        if (mUiccController == null) {
            UtkLog.d(this, "mUiccController == null, cant do nothing");
            return;
        }

        UiccCard uiccCard = mUiccController.getUiccCard(index);
        if (null == uiccCard) {
            UtkLog.d(this, "uiccCard == null, cant do nothing");
            return;
        }
        UiccCardApplication newUiccApplication =
                uiccCard.getApplication(UiccController.APP_FAM_3GPP2);

        UtkLog.d(this, "newUiccApplication " + index + " " + newUiccApplication);

        if (PhoneConstants.SIM_ID_1 == index) {
            if (mUiccApplicationSim1 != newUiccApplication) {
                UtkLog.d(this, "mUiccApplicationSim1 have changed!");
                if (mUiccApplicationSim1 != null) {
                    UtkLog.d(this, "mUiccApplicationSim1 unregisterForReady!");
                    mUiccApplicationSim1.unregisterForReady(this);
                    mUiccApplicationSim1 = null;
                }
                if (newUiccApplication != null) {
                    UtkLog.d(this, "mUiccApplicationSim1 registerForReady successly");
                    sActiveUtkId = index;
                    mUiccApplicationSim1 = newUiccApplication;
                    mUiccApplicationSim1.registerForReady(this, MSG_ID_RUIM_READY, null);
                } else {
                    sActiveUtkId = -1;
                }
            }
        } else {
            if (mUiccApplicationSim2 != newUiccApplication) {
                UtkLog.d(this, "mUiccApplicationSim2 have changed!");
                if (mUiccApplicationSim2 != null) {
                    UtkLog.d(this, "mUiccApplicationSim2 unregisterForReady!");
                    mUiccApplicationSim2.unregisterForReady(this);
                    mUiccApplicationSim2 = null;
                }
                if (newUiccApplication != null) {
                    UtkLog.d(this, "mUiccApplicationSim2 registerForReady successly");
                    sActiveUtkId = index;
                    mUiccApplicationSim2 = newUiccApplication;
                    mUiccApplicationSim2.registerForReady(this, MSG_ID_RUIM_READY, null);
                } else {
                    sActiveUtkId = -1;
                }
            }
        }
        UtkLog.d(this, "current active utk phone Id is " + sActiveUtkId);
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;

        UtkLog.d(this, "handleRilMsg " + rilMsg.mId);

        switch (rilMsg.mId) {
        case MSG_ID_EVENT_NOTIFY:
            if (rilMsg.mResCode == ResultCode.OK) {
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleProactiveCommand(cmdParams);
                }
            }
            break;
        case MSG_ID_PROACTIVE_COMMAND:
        //Add for UTK IR case
        case MSG_ID_MENU_INFO:
            try {
                cmdParams = (CommandParams) rilMsg.mData;
            } catch (ClassCastException e) {
                // for error handling : cast exception
                UtkLog.d(this, "Fail to parse proactive command");
                // Don't send Terminal Resp if command detail is not available
                if (mCurrntCmd != null) {
                    sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                                     false, 0x00, null);
                }
                break;
            }
            UtkLog.d(this, "handleRilMsg cmdParams!=null =" + (cmdParams != null) +
                                                        " rilMsg.mResCode = " + rilMsg.mResCode);
            if (cmdParams != null) {
                if (rilMsg.mResCode == ResultCode.OK) {
                    handleProactiveCommand(cmdParams);
                } else {
                    // for proactive commands that couldn't be decoded
                    // successfully respond with the code generated by the
                    // message decoder.
                    sendTerminalResponse(cmdParams.cmdDet, rilMsg.mResCode,
                            false, 0, null);
                }
            }
            break;
        case MSG_ID_REFRESH:
            cmdParams = (CommandParams) rilMsg.mData;
            if (cmdParams != null) {
                handleProactiveCommand(cmdParams);
            }
            break;
        case MSG_ID_SESSION_END:
            handleSessionEnd();
            break;
        case MSG_ID_CALL_SETUP:
            // prior event notify command supplied all the information
            // needed for set up call processing.
            break;
        }
    }

    /**
     * Handles RIL_UNSOL_UTK_PROACTIVE_COMMAND unsolicited command from RIL.
     * Sends valid proactive command data to the application using intents.
     *
     */
    private void handleProactiveCommand(CommandParams cmdParams) {
        UtkLog.d(this, cmdParams.getCommandType().name());

        UtkCmdMessage cmdMsg = new UtkCmdMessage(cmdParams);

        UtkLog.d(this, "handleProactiveCommand " + cmdParams.getCommandType());

        switch (cmdParams.getCommandType()) {
        case SET_UP_MENU:
            if (removeMenu(cmdMsg.getMenu())) {
                mMenuCmd = null;
            } else {
                mMenuCmd = cmdMsg;
            }
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                    null);
            break;
        case DISPLAY_TEXT:
            // when application is not required to respond, send an immediate
            // response.
            if (!cmdMsg.geTextMessage().responseNeeded) {
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                        0, null);
            }
            break;
        case REFRESH: {
            // ME side only handles refresh commands which meant to remove IDLE
            // MODE TEXT.
            UtkLog.d(this, "UtkService handleProactiveCommand Do refresh");
            int type = 1;
            if (cmdParams.cmdDet.commandQualifier == 1) {
                type = 1;
            } else if (cmdParams.cmdDet.commandQualifier == 4) {
                type = 2;
            } else if (cmdParams.cmdDet.commandQualifier == 3) {
                type = 3;
            } else if (cmdParams.cmdDet.commandQualifier == 0 ||
                         cmdParams.cmdDet.commandQualifier == 2) {
                type = 4;
            }
            mCmdIf.requestUtkRefresh(type, obtainMessage(MSG_ID_RIL_REFRESH_RESULT));

            //mRuimRecords.handleRuimRefresh(cmdParams.cmdDet.commandQualifier);
            IccRefreshResponse rsp = new IccRefreshResponse();
            rsp.refreshResult = cmdParams.cmdDet.commandQualifier;
            IccRecords mIccRecords = (mPhoneId == 0) ? mIccRecordsSim1:mIccRecordsSim2;
            Message m = Message.obtain(mIccRecords, 31, null);
            AsyncResult.forMessage(m, rsp, null);
            m.sendToTarget();

            cmdParams.cmdDet.typeOfCommand = CommandType.SET_UP_IDLE_MODE_TEXT
                    .value();
            //sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
            //        0, null);
            break;
            }
        case SET_UP_IDLE_MODE_TEXT:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            break;
        case LAUNCH_BROWSER:
        case SELECT_ITEM:
        case GET_INPUT:
        case GET_INKEY:
        case SEND_DTMF:
        case SEND_SS:
        case SEND_USSD:
        case PLAY_TONE:
        case SET_UP_CALL:
        case SEND_SMS:
            // nothing to do on telephony!
            break;
        case MORE_TIME:
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false,
                    0, null);
            //There is no need to notify utkapp there is more time command
            //just send a respond is enougth
            return;
         case LOCAL_INFO:
            if (cmdParams.cmdDet.commandQualifier == 0 ||
                cmdParams.cmdDet.commandQualifier == 6) {
                UtkLog.d(this, "Local information get AT data");
                mCmdIf.getUtkLocalInfo(obtainMessage(MSG_ID_CMD_LOCAL_INFO));
                mCurrntCmd = cmdMsg;
                mCatchLocalInfoCmd = cmdMsg;
            } else {
                UtkLog.d(this, "handleCmdResponse Local info");
                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                  new LocalInformationResponseData(cmdParams.cmdDet.commandQualifier, mLocalInfo));
                mCurrntCmd = null;
            }
            return;

         //bip start
         case POLL_INTERVAL:
            int timeUnit = ((pollIntervalParams) cmdParams).timeUnit;
            int timeInterval = ((pollIntervalParams) cmdParams).timeInterval;
            UtkLog.d(this, " timeUnit=" + timeUnit + " timeInterval=" + timeInterval);

            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                                 new PollIntervalResponseData(timeUnit, timeInterval));
            mCurrntCmd = null;
            return ;
         case TIMER_MANAGEMENT:
            int timerId = ((TimerManagementParams) cmdParams).timerId;
            int timerAction = ((TimerManagementParams) cmdParams).timerAction;
            byte[] bcdData = ((TimerManagementParams) cmdParams).dataRaw;

            UtkLog.d(this, " timerId=" + timerId + " timerAction=" + timerAction +
                           " bcdData=" + IccUtils.bytesToHexString(bcdData));

            if (timerAction == BipConstants.BIP_TIMER_MANAGEMENT_START) {
                startUtkTimer();
                addTimerManagement(timerId, bcdData);

                sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                                     new TimerManagementResponseData(timerId));
            } else if (timerAction == BipConstants.BIP_TIMER_MANAGEMENT_DEACTIVATE) {
                UtkTimerManagementData td = getTimerManagement(timerId);
                if (td != null) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                                     new TimerManagementResponseData(timerId, td.getRemaining()));
                    removeTimerManagement(timerId);
                } else {
                    UtkLog.d(this, " timermanagement " + timerId + " is null");
                    //action in contradiction with the current timer state???
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.CONTRADICTION_WITH_TIMER,
                                             false, 0, null);
                }
            } else if (timerAction == BipConstants.BIP_TIMER_MANAGEMENT_GET_VALUE) {
                UtkTimerManagementData td = getTimerManagement(timerId);

                if (td != null) {
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0,
                                     new TimerManagementResponseData(timerId, td.getRemaining()));
                } else {
                    UtkLog.d(this, " timermanagement " + timerId + " is null");
                    //action in contradiction with the current timer state???
                    sendTerminalResponse(cmdParams.cmdDet, ResultCode.CONTRADICTION_WITH_TIMER,
                                             false, 0, null);
                }
            }
            mCurrntCmd = null;
            return;
         case SET_UP_EVENT_LIST:
            mEventList = ((SetupEventListParams) cmdParams).eventList;
            UtkLog.d(this, " set mEventList=" + IccUtils.bytesToHexString(mEventList));
            sendTerminalResponse(cmdParams.cmdDet, ResultCode.OK, false, 0, null);

            checkLocationEvent();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                //roll back for 4G BIP case fail
                checkBipEvent();
                //broadcastEventlist();
            }
            //mCurrntCmd = null;
            if (mCurrntCmd != null) {
                UtkLog.d(this, " SET_UP_EVENT_LIST: mCurrntCmd != null");
            }
            return;
         case OPEN_CHANNEL:
            UtkLog.d(this, " OPEN_CHANNEL: cache this cmd");
            mCatchChannelCmd = cmdMsg;
            //
            break;
         case CLOSE_CHANNEL:
          {
            int chId = ((CloseChannelParams) cmdParams).chId;
            //UtkLog.d(this, "CLOSE_CHANNEL:"+chId);

            boolean listen = ((CloseChannelParams) cmdParams).isListen;
            mBipService.closeChannel(chId, listen);
          }
            break;
         case RECEIVE_DATA:
          {
            int chId = ((ReceiveDataParams) cmdParams).chId;
            int reqDataLength = ((ReceiveDataParams) cmdParams).reqDataLength;
            //UtkLog.d(this, "RECEIVE_DATA:"+chId+" reqDataLength:"+reqDataLength);

            mBipService.receiveData(chId, reqDataLength);
          }
            break;
         case SEND_DATA:
          {
            int chId = ((SendDataParams) cmdParams).chId;
            byte[] data = ((SendDataParams) cmdParams).channelData;
            boolean sendImmediately = ((SendDataParams) cmdParams).sendImmediately;
            //UtkLog.d(this, "SEND_DATA chId:"+chId+" sendImmediately:"+sendImmediately);
            //UtkLog.d(this, "SEND_DATA length:"+data.length);

            mBipService.sendData(chId, data, sendImmediately);
          }
            break;
         case GET_CHANNEL_STATUS:
          {
            int chId = ((GetChannelStatusParams) cmdParams).chId;
            //UtkLog.d(this, "GET_CHANNEL_STATUS:"+chId);
            mBipService.getChannelStatus(chId);
          }
            break;
         //bip end

            default:
            UtkLog.d(this, "Unsupported command");
            return;
        }
        mCurrntCmd = cmdMsg;
        Intent intent = new Intent(AppInterface.UTK_CMD_ACTION);
        intent.putExtra("UTK CMD", cmdMsg);
        intent.putExtra("PHONE_ID", mPhoneId);
        mContext.sendBroadcast(intent);
    }

    private boolean resetCurrentCmd() {
        if (mCurrntCmd == null) {
            return true;
        }

        AppInterface.CommandType type =
              AppInterface.CommandType.fromInt(mCurrntCmd.mCmdDet.typeOfCommand);
        switch(type) {
            case OPEN_CHANNEL:
            case CLOSE_CHANNEL:
            case RECEIVE_DATA:
            case SEND_DATA:
              return false;
            default:
              return true;
        }
    }

    /**
     * Handles RIL_UNSOL_UTK_SESSION_END unsolicited command from RIL.
     *
     */
    private void handleSessionEnd() {
        UtkLog.d(this, "SESSION END on mPhoneId " + mPhoneId);

        if (resetCurrentCmd()) {
            mCurrntCmd = mMenuCmd;
        }
        Intent intent = new Intent(AppInterface.UTK_SESSION_END_ACTION);
        intent.putExtra("PHONE_ID", mPhoneId);
        mContext.sendBroadcast(intent);
    }

    private void sendTerminalResponse(CommandDetails cmdDet,
            ResultCode resultCode, boolean includeAdditionalInfo,
            int additionalInfo, ResponseData resp) {

        if (cmdDet == null) {
            return;
        }
        //Add for UTK IR case
        if ((CommandType.SET_UP_MENU.value() == cmdDet.typeOfCommand) && mQueryMenuFlag) {
            UtkLog.d(this, "Ignore response from query menu case");
            mQueryMenuFlag = false;
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // command details
        int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
        if (cmdDet.compRequired) {
            tag = 0x80 | ComprehensionTlvTag.COMMAND_DETAILS.value();
        }
        buf.write(tag);
        buf.write(0x03); // length
        buf.write(cmdDet.commandNumber);
        buf.write(cmdDet.typeOfCommand);
        buf.write(cmdDet.commandQualifier);

        // device identities
        tag = /*0x80 |*/ ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_TERMINAL); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // result
        tag = 0x80 | ComprehensionTlvTag.RESULT.value();
        buf.write(tag);
        int length = includeAdditionalInfo ? 2 : 1;
        buf.write(length);
        buf.write(resultCode.value());

        // additional info
        if (includeAdditionalInfo) {
            buf.write(additionalInfo);
        }

        // Fill optional data for each corresponding command
        if (resp != null) {
            //Filter the invalid Mcc,IMSI value
            if (CommandType.LOCAL_INFO.value() == cmdDet.typeOfCommand) {
                UtkLog.d(this, "sendTerminalResponse : mServiceState = " + mServiceState);
                if (mServiceState != BipConstants.SERVICE_STATE_NORMAL) {
                    //buf.write(ComprehensionTlvTag.LOCATION_INFORMATION.value());
                    //buf.write(0x00); //length
                } else {
                    LocalInfo lc = getRecodeLocalinfo();
                    if ((lc.MCC == 0) && (lc.IMSI_11_12 == 0)) {
                        UtkLog.d(this, "sendTerminalResponse ignore invalid local info value");
                        //buf.write(ComprehensionTlvTag.LOCATION_INFORMATION.value());
                        //buf.write(0x00); //length
                    } else {
                        resp.format(buf);
                    }
                }                        
            } else {
                resp.format(buf);
            }
        }

        byte[] rawData = buf.toByteArray();
        String hexString = IccUtils.bytesToHexString(rawData);
        //if (Config.LOGD) {
            UtkLog.d(this, "TERMINAL RESPONSE: " + hexString);
        //}

        if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport()) &&
                (UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType)) {
            int cmdType = getSvlteUtkCommandType(UTK_TR, cmdDet.typeOfCommand);
            UtkLog.d(this, "UTK LTE: send terminal response through " +
                    "lte ril arbitrator, cmdType = " + cmdType);
            sRilDcArbitrator.sendTerminalResponse(hexString, null, cmdType, mMutliSimType);
        } else {
            mCmdIf.sendTerminalResponse(hexString, null);
        }
    }


    private void sendMenuSelection(int menuId, boolean helpRequired) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_MENU_SELECTION_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder

        // device identities
        tag = /*0x80 |*/ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_KEYPAD); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // item identifier
        tag = /*80 |*/ ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(menuId); // menu identifier chosen

        // help request
        if (helpRequired) {
            tag = ComprehensionTlvTag.HELP_REQUEST.value();
            buf.write(tag);
            buf.write(0x00); // length
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        UtkLog.d(this, "sending menu selection envelope: " + hexString);
        if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport()) &&
                (UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType)) {
            int cmdType = getSvlteUtkCommandType(UTK_ENV, ENVELOPE_MENU_SELECTION);
            UtkLog.d(this, "UTK LTE: sendMenuSelection through " +
                    "lte ril arbitrator, cmdType = " + cmdType);
            sRilDcArbitrator.sendEnvelope(hexString, null, cmdType, mMutliSimType);
        } else {
            mCmdIf.sendEnvelope(hexString, null);
        }
         if(SystemProperties.get("persist.sys.esn_track_switch").equals("1")) {
            mContext.sendBroadcast(new Intent(mEsnTrackUtkMenuSelect));
        }
    }

    private void eventDownload(int event, int sourceId, int destinationId,
            byte[] additionalInfo, boolean oneShot) {
        int index;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        if (EVENT_LIST_DATA_AVAILABLE != event) {
            if (null == mEventList || mEventList.length == 0) {
                UtkLog.d(this, "eventDownload mEventList null");
                return;
            }

            UtkLog.d(this, "eventDownload events=" + IccUtils.bytesToHexString(mEventList) +
                            " current event=" + event);

            for (index = 0; index < mEventList.length; index++) {
                if (mEventList[index] == event) {
                    if (oneShot) {
                        mEventList[index] = 0;
                    }
                    break;
                }
            }

            if (index == mEventList.length) {
                UtkLog.d(this, "eventDownload not wanted event " + event);
                return;
            }
        }

        // tag
        buf.write(BerTlv.BER_EVENT_DOWNLOAD_TAG);

        // length
        buf.write(0x00); // place holder, assume length < 128.

        // event list
        buf.write(ComprehensionTlvTag.EVENT_LIST.value());
        buf.write(0x01); // length
        buf.write(event); // event value

        // device identities
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
        buf.write(0x02); // length
        buf.write(sourceId); // source device id
        buf.write(destinationId); // destination device id

        // additional information
        if (additionalInfo != null) {
            buf.write(additionalInfo, 0, additionalInfo.length);
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        UtkLog.d(this, "sending event envelope hexString:" + hexString);

        if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport()) &&
                (UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType)) {
            int cmdType = getSvlteUtkCommandType(UTK_ENV, event);
            UtkLog.d(this, "UTK LTE: event download through " +
                    "lte ril arbitrator, event = " + event + " cmdType = " + cmdType);
            sRilDcArbitrator.sendEnvelope(hexString, null, cmdType, mMutliSimType);
        } else {
            mCmdIf.sendEnvelope(hexString, null);
        }
    }

    /**
     * Used for instantiating/updating the Service from the GsmPhone constructor.
     *
     * @param ci CommandsInterface object
     * @param context phone app context
     * @param ic UiccCard obj
     * @return The only Service object in the system
     */
    public static UtkService getInstance(CommandsInterface ci,
            Context context, UiccCard ic) {

        UtkLog.d(LOG_TAG, " getInstance ic " + ic);
        //fh Ruim file handler
        UiccCardApplication ca = null;
        IccFileHandler fh = null;
        IccRecords ir = null;
        // WP solution 2 modification
        int tempPhoneId = -1;

        if (ic != null) {
            /* Since Cat is not tied to any application, but rather is Uicc application
             * in itself - just get first FileHandler and IccRecords object
             */
            //ca = ic.getApplicationIndex(0);
            ca = ic.getApplication(UiccController.APP_FAM_3GPP2);
            if (ca != null) {
                fh = ca.getIccFileHandler();
                ir = ca.getIccRecords();
                //To do: May need another FO
                tempPhoneId = ic.getPhoneId();
                UtkLog.d(WP2_LOG_TAG, " tempPhoneId = " + tempPhoneId);
            }
        }
        synchronized (sInstanceLock) {
            if ((PhoneConstants.SIM_ID_1 == tempPhoneId && sInstanceSim1 == null)
                || (PhoneConstants.SIM_ID_2 == tempPhoneId && sInstanceSim2 == null)) {
                if (ci == null || context == null || ic == null ||
                    ca == null || fh == null || ir == null || tempPhoneId == -1) {
                    UtkLog.d(LOG_TAG, " getInstance ca " + ca + " ir " + ir + " fh " + fh);
                    return null;
                }
                // WPS2 modification
                //HandlerThread thread = new HandlerThread("Utk Telephony service");
                //thread.start();
                UtkService tempInstance = null;
                tempInstance = new UtkService(ci, ca, ir, context, fh, ic);
                sActiveInstance = tempInstance;
                if (PhoneConstants.SIM_ID_1 == tempPhoneId) {
                    sInstanceSim1 = tempInstance;
                    sActiveUtkId = tempPhoneId;
                    UtkLog.d(sInstanceSim1, "new sInstance" + tempPhoneId);
                    return sInstanceSim1;
                } else if (PhoneConstants.SIM_ID_2 == tempPhoneId) {
                    sInstanceSim2 = tempInstance;
                    sActiveUtkId = tempPhoneId;
                    UtkLog.d(sInstanceSim2, "new sInstance" + tempPhoneId);
                    return sInstanceSim2;
                } else {
                    UtkLog.d(WP2_LOG_TAG, "Invalid phone ID and return null instance");
                    return null;
                }
            } else if (PhoneConstants.SIM_ID_1 == tempPhoneId && sInstanceSim1 != null) {
                if ((ca != null) && (mUiccApplicationSim1 != ca)) {
                    if (mUiccApplicationSim1 != null) {
                        mUiccApplicationSim1.unregisterForReady(sInstanceSim1);
                    }
                    mUiccApplicationSim1 = ca;
                    mUiccApplicationSim1.registerForReady(sInstanceSim1, MSG_ID_RUIM_READY, null);
                    UtkLog.d(sInstanceSim1, "sInstanceSim1 reinitialize with new ca");
                }
                if ((ir != null) && (mIccRecordsSim1 != ir)) {
                    mIccRecordsSim1 = ir;
                    UtkLog.d(sInstanceSim1, "sInstanceSim1 reinitialize with new ir");
                }
                UtkLog.d(sInstanceSim1, "Return current sInstanceSim1");
                sActiveInstance = sInstanceSim1;
                sActiveUtkId = 0;
                return sInstanceSim1;
            } else if (PhoneConstants.SIM_ID_2 == tempPhoneId && sInstanceSim2 != null) {
                if ((ca != null) && (mUiccApplicationSim2 != ca)) {
                    if (mUiccApplicationSim2 != null) {
                        mUiccApplicationSim2.unregisterForReady(sInstanceSim2);
                    }
                    mUiccApplicationSim2 = ca;
                    mUiccApplicationSim2.registerForReady(sInstanceSim2, MSG_ID_RUIM_READY, null);
                    UtkLog.d(sInstanceSim2, "sInstanceSim2 reinitialize with new ca");
                }
                if ((ir != null) && (mIccRecordsSim2 != ir)) {
                    mIccRecordsSim2 = ir;
                    UtkLog.d(sInstanceSim2, "sInstanceSim2 reinitialize with new ir");
                }
                UtkLog.d(sInstanceSim2, "Return current sInstanceSim2");
                sActiveInstance = sInstanceSim2;
                sActiveUtkId = 1;
                return sInstanceSim2;
            } else {
                UtkLog.d(WP2_LOG_TAG, " Return active sInstance");
                UtkLog.d(WP2_LOG_TAG, " sActiveUtkId is " + sActiveUtkId);
                return sActiveInstance;
            }
        }
    }

    /**
     * Used by application to get an AppInterface object.
     *
     * @return The only Service object in the system
     */
    public static AppInterface getInstance() {
        return getInstance(null, null, null);
    }

    /**
     * Used by application to get an AppInterface object.
     *
     * @return The only Service object in the system
     */
    public static AppInterface getInstance(int phoneId) {
        if (PhoneConstants.SIM_ID_1 == phoneId) {
            return sInstanceSim1;
        } else if (PhoneConstants.SIM_ID_2 == phoneId) {
            return sInstanceSim2;
        } else {
            return null;
        }
    }
    
    @Override
    public void handleMessage(Message msg) {
        UtkLog.d(this, "ril message arrived : [" + msg.what + "] from Phone " + mPhoneId);
        switch (msg.what) {
        case MSG_ID_SESSION_END:
        case MSG_ID_PROACTIVE_COMMAND:
        case MSG_ID_EVENT_NOTIFY:
        case MSG_ID_REFRESH:
        //Add for UTK IR case
        case MSG_ID_MENU_INFO:
            UtkLog.d(this, "ril message arrived ");
            String data = null;
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar != null && ar.result != null) {
                    try {
                        data = (String) ar.result;
                    } catch (ClassCastException e) {
                        break;
                    }
                }
            }
            if (mRilMsgDecoding) {
                UtkLog.d(this, "ril message delay...");
                mPendingRilMsgList.add(new RilMessage(msg.what, data));
                break;
            }
            mRilMsgDecoding = true;

            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
            if (MSG_ID_MENU_INFO == msg.what) {
                UtkLog.d(this, "Ignore response from query menu case");
                mQueryMenuFlag = true;
            }
            break;
        case MSG_ID_CALL_SETUP:
            if (mRilMsgDecoding) {
                UtkLog.d(this, "ril message delay...");
                mPendingRilMsgList.add(new RilMessage(msg.what, null));
                break;
            }
            mRilMsgDecoding = true;

            mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
            break;
        case MSG_ID_ICC_CHANGED:
            if (msg.obj != null) {
                AsyncResult ar = (AsyncResult) msg.obj;
                int index = -1;
                if (ar != null && ar.result instanceof Integer) {
                    index = ((Integer) ar.result).intValue();
                    UtkLog.d(this, "MSG_ID_ICC_CHANGED, index = " + index + 
                                                                    " and mPhoneId = " + mPhoneId);
                } else {
                    UtkLog.d(this, "MSG_ID_ICC_CHANGED, no index and mPhoneId = " + mPhoneId);
                }
                if (index == mPhoneId) {
                    updateIccStatus(index);
                }
            }
            break;
        case MSG_ID_RUIM_READY:
            if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport()) &&
                    (UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType)) {
                UtkLog.d(this, "utk MSG_ID_RUIM_READY registerLteRilEvents");
                registerLteRilEvents();
            }

            // Before sending terminal profile, update EF_Model for ESN tracking
            String mUiccType = SystemProperties.
                get(UICCCARD_PROPERTY_RIL_UICC_TYPE[mPhoneId]);
            UtkLog.d(this, "mPhoneId: " + mPhoneId + " mUiccType: " + mUiccType);

            if (null != mUiccType && mUiccType.equals("RUIM")) {
                int mUpdateBinary = 0xD6;
                int mfileid = 0x6F90;
                String mEfModelPath = "3F007F25";
                String mModelMessage = getModelMessage();
                if (null != mModelMessage) {
                    UtkLog.d(this, "utk write EF_Model");
                    mCmdIf.iccIOForApp(mUpdateBinary, mfileid, mEfModelPath,
                        0, 0, 126, mModelMessage, null, null, null);
                }
            }

            UtkLog.d(this, "utk profileDownload");
            mCmdIf.profileDownload("", null);
            break;
        case MSG_ID_RIL_MSG_DECODED:
            handleRilMsg((RilMessage) msg.obj);

            if (mPendingRilMsgList.size() > 0) {
                UtkLog.d(this, " decoding pending ril msg");

                RilMessage r = mPendingRilMsgList.get(0);
                mPendingRilMsgList.remove(0);

                mMsgDecoder.sendStartDecodingMessageParams(r);
            } else {
                mRilMsgDecoding = false;
            }
            UtkLog.d(this, " decoding pending ril msg done");
            break;
        case MSG_ID_RESPONSE:
            handleCmdResponse((UtkResponseMessage) msg.obj);
            break;
        case MSG_ID_EVENT_LOCAL_INFO:
        case MSG_ID_CMD_LOCAL_INFO:
            AsyncResult aresult = (AsyncResult) msg.obj;
            if (aresult.result != null) {
                int info[] = (int[]) aresult.result;
                if (info.length == 8) {
                    mLocalInfo.Technology = info[0];
                    mLocalInfo.MCC = info[1];
                    mLocalInfo.IMSI_11_12 = info[2];
                    mLocalInfo.SID = info[3];
                    mLocalInfo.NID = info[4];
                    mLocalInfo.BASE_ID = info[5];
                    mLocalInfo.BASE_LAT = info[6];
                    mLocalInfo.BASE_LONG = info[7];
                } else {
                   UtkLog.d(this, "MSG_GET_LOCAL_INFO error");
                }
            }

            if (msg.what == MSG_ID_CMD_LOCAL_INFO) {
                UtkLog.d(this, "response cmd local info");

                LocalInfo lc = getRecodeLocalinfo();

                ResultCode resCode = ResultCode.OK;
                boolean includeAdditionalInfo = false;
                int additionalInfo = 0;

                UtkLog.d(this, "response cmd local info : mServiceState = " + mServiceState);
                if (mServiceState != BipConstants.SERVICE_STATE_NORMAL) {
                    resCode = ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
                    includeAdditionalInfo = true;
                    additionalInfo = 4;
                } else {
                    if ((lc.MCC == 0) && (lc.IMSI_11_12 == 0)) {
                        resCode = ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
                        includeAdditionalInfo = true;
                        additionalInfo = 4;
                    }
                }
                if ((mCurrntCmd != null) && ((CommandType.LOCAL_INFO.value() == mCurrntCmd.mCmdDet.typeOfCommand))) {
                    UtkLog.d(this, "response cmd local info: mCurrntCmd != null! ");
                    sendTerminalResponse(mCurrntCmd.mCmdDet, resCode, includeAdditionalInfo,
                                     additionalInfo,
                                     new LocalInformationResponseData(
                                         mCurrntCmd.mCmdDet.commandQualifier, lc));
                } else if ((mCurrntCmd == null) && (mCatchLocalInfoCmd != null)) { 
                    UtkLog.d(this, "response cmd local info: mCurrntCmd == null! ");
                    sendTerminalResponse(mCatchLocalInfoCmd.mCmdDet, resCode,
                                     includeAdditionalInfo, additionalInfo,
                                     new LocalInformationResponseData(
                                         mCatchLocalInfoCmd.mCmdDet.commandQualifier, lc));
                } else {
                    UtkLog.d(this, "Both mCurrntCmd and mCatchLocalInfoCmd are null");
                }        
                mCurrntCmd = null;
            } else {
                //event download
                locationStatusEventDownload();
            }
           break;
        case MSG_ID_RIL_REFRESH_RESULT: {
            UtkLog.d(this, "MSG_ID_RIL_REFRESH_RESULT  Complete! ");
            Intent intent = new Intent();
            intent.setAction("com.android.contacts.action.CONTACTS_INIT_RETRY_ACTION");
            mContext.sendBroadcast(intent);
            //For Local info JE issue
            if ((mCurrntCmd != null) &&
                            (CommandType.REFRESH.value() == mCurrntCmd.mCmdDet.typeOfCommand)) {
                mCurrntCmd = null;
                UtkLog.d(this, "Clear REFRESH mCurrentCmd");
            }
            break;
        }

        //bip start
        case MSG_ID_TIMER_MANAGEMENT_TIMEOUT:
            if (msg.obj != null) {
                String hexString = (String) msg.obj;
                UtkLog.d(this, "sending timeout envelope: " + hexString);
                mCmdIf.sendEnvelope(hexString, null);
            }
            break;
        case MSG_ID_TIMER_TICK:
            handleUtkTimerTick();
            break;
        case MSG_ID_EVENT_DOWNLOAD:
            UtkLog.d(this, "handleMessage MSG_ID_EVENT_DOWNLOAD");
            handleEventDownload((UtkResponseMessage) msg.obj);
            break;
        case MSG_ID_OPENED_CHANNEL:
        {
            if (mCurrntCmd == null) {
                if (mCatchChannelCmd == null) {
                    UtkLog.d(this, "handleMessage MSG_ID_OPENED_CHANNEL:" +
                                                "mCurrntCmd is null, mCatchChannelCmd is null");
                    return;
                } else {
                    UtkLog.d(this, "handleMessage MSG_ID_OPENED_CHANNEL:" +
                                                "mCurrntCmd is null, mCatchChannelCmd isn't null");
                    mCurrntCmd = mCatchChannelCmd;
                }
            }

            UtkLog.d(this, "handleMessage MSG_ID_OPENED_CHANNEL:" + msg.arg1 + " arg2:" + msg.arg2);
            OpenChannelResponseData rsp = null;
            if (msg.arg1 == BipConstants.RESULT_SUCCESS) {
                OpenChannelResult openResult = (OpenChannelResult) msg.obj;
                rsp = new OpenChannelResponseData(openResult);
                if (msg.arg2 ==
                            BipConstants.RESULT_CODE_RESULT_SUCCESS_PERFORMED_WITH_MODIFICATION) {
                    sendTerminalResponse(mCurrntCmd.mCmdDet,
                                         ResultCode.PRFRMD_WITH_MODIFICATION, false, 0, rsp);
                } else {
                    sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, rsp);
                }
            } else {
                 if (msg.arg2 == BipConstants.RESULT_CODE_NETWORK_CRNTLY_UNABLE_TO_PROCESS) {
                     sendTerminalResponse(mCurrntCmd.mCmdDet,
                                  ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                 } else if (msg.arg2 == BipConstants.RESULT_CODE_BEYOND_TERMINAL_CAPABILITY) {
                     sendTerminalResponse(mCurrntCmd.mCmdDet,
                                  ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                 } else {
                     sendTerminalResponse(mCurrntCmd.mCmdDet,
                                  ResultCode.BIP_ERROR, false, 0, null);
                 }
            }
            mCurrntCmd = null;
            mCatchChannelCmd = null;
        }
            break;
        case MSG_ID_SENT_DATA:
        {
            if (mCurrntCmd == null) {
                return;
            }

            UtkLog.d(this, "handleMessage MSG_ID_SENT_DATA:" + msg.arg1 + " arg2:" + msg.arg2);
            if (msg.arg1 == BipConstants.RESULT_SUCCESS) {
                int[] availableTxSize = (int[]) msg.obj;
                SendDataResponseData rsp = new SendDataResponseData(availableTxSize[0]);
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, rsp);
            } else {
                int additionInfo = 0;
                boolean withAdd = false;

                if (msg.arg2 == BipConstants.ADDITIONAL_INFO_CHANNEL_CLOSED) {
                    additionInfo = BipConstants.CHANNEL_STATUS_INFO_LINK_DROPED;
                    withAdd = true;
                } else if (msg.arg2 == BipConstants.ADDITIONAL_INFO_CHANNEL_ID_NOT_AVAILABLE) {
                    additionInfo = BipConstants.CHANNEL_STATUS_INFO_LINK_DROPED;
                    withAdd = true;
                }
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.BIP_ERROR,
                                      withAdd, additionInfo, null);
            }
            mCurrntCmd = null;
        }
            break;
        case MSG_ID_RECEIVED_DATA:
        {
            if (mCurrntCmd == null) {
                return;
            }

            UtkLog.d(this, "handleMessage MSG_ID_RECEIVED_DATA:" + msg.arg1 + " arg2:" + msg.arg2);

            if (msg.arg1 == BipConstants.RESULT_SUCCESS) {
                byte[] receivedData = (byte[]) msg.obj;
                int remaining = msg.arg2;
                ReceiveDataResponseData rsp = new ReceiveDataResponseData(receivedData, remaining);

                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, rsp);
            } else if (msg.arg2 == BipConstants.RESULT_CODE_PRFRMD_WITH_MISSING_INFO) {
                byte[] receivedData = (byte[]) msg.obj;
                int remaining = 0;
                ReceiveDataResponseData rsp = new ReceiveDataResponseData(receivedData, remaining);

                sendTerminalResponse(mCurrntCmd.mCmdDet,
                                             ResultCode.PRFRMD_WITH_MISSING_INFO, false, 0, rsp);
            } else {
                sendTerminalResponse(mCurrntCmd.mCmdDet,
                                     ResultCode.BIP_ERROR, false, 0, null);
            }
            mCurrntCmd = null;
        }
            break;
        case MSG_ID_CLOSED_CHANNEL:
            if (mCurrntCmd == null) {
                return;
            }

            UtkLog.d(this, "handleMessage MSG_ID_CLOSED_CHANNEL:" + msg.arg1 + " arg2:" + msg.arg2);

            if (msg.arg1 == BipConstants.RESULT_SUCCESS) {
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
            } else {
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, null);
            }
            mCurrntCmd = null;
            break;
        case MSG_ID_GET_CHANNEL_STATUS:
        {
            if (mCurrntCmd == null) {
                return;
            }

            UtkLog.d(this, "handleMessage MSG_ID_GET_CHANNEL_STATUS:"
                                    + msg.arg1 + " arg2:" + msg.arg2);

            if (msg.arg1 == BipConstants.RESULT_SUCCESS) {
                ChannelStatus s = (ChannelStatus) msg.obj;
                GetChannelStatusResponseData rsp = new GetChannelStatusResponseData(s);
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, rsp);
            } else {
                sendTerminalResponse(mCurrntCmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, null);
            }
            mCurrntCmd = null;
        }
            break;
        case MSG_ID_RADIO_OFF_OR_UNAVAILABLE:
        {
            UtkLog.d(this, "handleMessage MSG_ID_RADIO_OFF_OR_UNAVAILABLE");
            Intent intent = new Intent("android.intent.action.utk.radio_off");
            mContext.sendBroadcast(intent);
        }
        break;
        //bip end

        default:
            throw new AssertionError("Unrecognized UTK command: " + msg.what);
        }
    }

    /**
     * Used by application to send MSG_ID_RESPONSE.
     * @param UtkResponseMessage
     * @return null
     */
    public synchronized void onCmdResponse(UtkResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = this.obtainMessage(MSG_ID_RESPONSE, resMsg);
        msg.sendToTarget();
    }

    private boolean validateResponse(UtkResponseMessage resMsg) {
        if (resMsg.cmdDet.typeOfCommand == CommandType.OPEN_CHANNEL.value()) {
            UtkLog.d(this, "Uncheck open channel cmd");
            return true;
        }
        if (resMsg.cmdDet.typeOfCommand == CommandType.DISPLAY_TEXT.value()) {
            UtkLog.d(this, "Uncheck display text cmd");
            return true;
        }
        if (mCurrntCmd != null) {
            UtkLog.d(this, "validateResponse resMsg.cmdDet: " + resMsg.cmdDet);
            UtkLog.d(this, "validateResponse mCurrntCmd.mCmdDet: " + mCurrntCmd.mCmdDet);
            return (resMsg.cmdDet.compareTo(mCurrntCmd.mCmdDet));
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            UtkLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    private void handleCmdResponse(UtkResponseMessage resMsg) {
        // Make sure the response details match the last valid command. An invalid
        // response is a one that doesn't have a corresponding proactive command
        // and sending it can "confuse" the baseband/ril.
        // One reason for out of order responses can be UI glitches. For example,
        // if the application launch an activity, and that activity is stored
        // by the framework inside the history stack. That activity will be
        // available for relaunch using the latest application dialog
        // (long press on the home button). Relaunching that activity can send
        // the same command's result again to the UtkService and can cause it to
        // get out of sync with the SIM.
        if (!validateResponse(resMsg)) {
                UtkLog.d(this, "handleCmdResponse:validateResponse");
            return;
        }
        ResponseData resp = null;
        boolean helpRequired = false;
        CommandDetails cmdDet = resMsg.getCmdDetails();
        UtkLog.d(this, "handleCmdResponse:resMsg.resCode = " + resMsg.resCode);
        switch (resMsg.resCode) {
        case HELP_INFO_REQUIRED:
            helpRequired = true;
            // fall through
        case OK:
        case PRFRMD_WITH_PARTIAL_COMPREHENSION:
        case PRFRMD_WITH_MISSING_INFO:
        case PRFRMD_WITH_ADDITIONAL_EFS_READ:
        case PRFRMD_ICON_NOT_DISPLAYED:
        case PRFRMD_MODIFIED_BY_NAA:
        case PRFRMD_LIMITED_SERVICE:
        case PRFRMD_WITH_MODIFICATION:
        case PRFRMD_NAA_NOT_ACTIVE:
        case PRFRMD_TONE_NOT_PLAYED:
            UtkLog.d(this, "handleCmdResponse cmd = " +
                                        AppInterface.CommandType.fromInt(cmdDet.typeOfCommand));
            switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
            case SET_UP_MENU:
                helpRequired = resMsg.resCode == ResultCode.HELP_INFO_REQUIRED;
                sendMenuSelection(resMsg.usersMenuSelection, helpRequired);
                return;
            case SELECT_ITEM:
                resp = new SelectItemResponseData(resMsg.usersMenuSelection);
                break;
            case GET_INPUT:
            case GET_INKEY:
                Input input = mCurrntCmd.geInput();
                //modified by maolikui at 2015-09-28 START 
                UtkLog.d(this, "[ALPS02332111] input.yesNo:"+ input.yesNo+"input.ucs2:"+input.ucs2+"input.packed:"+input.packed); 
                //modified by maolikui at 2015-09-28 end
                if (!input.yesNo) {
                    // when help is requested there is no need to send the text
                    // string object.
                    if (!helpRequired) {
                        resp = new GetInkeyInputResponseData(resMsg.usersInput,
                                input.ucs2, input.packed);
                    }
                } else {
                    resp = new GetInkeyInputResponseData(
                            resMsg.usersYesNoSelection);
                }
                break;
            case DISPLAY_TEXT:
            case LAUNCH_BROWSER:
                break;
            case SET_UP_CALL:
                mCmdIf.handleCallSetupRequestFromUim(resMsg.usersConfirm, null);
                // No need to send terminal response for SET UP CALL. The user's
                // confirmation result is send back using a dedicated ril message
                // invoked by the CommandInterface call above.
                mCurrntCmd = null;
                return;
            //bip start
            case OPEN_CHANNEL:
                UtkLog.d(this, "resCode:" + resMsg.resCode +
                               " usersConfirm:" + resMsg.usersConfirm);

                if (ResultCode.OK == resMsg.resCode) {
                    if (resMsg.usersConfirm) {
                        OpenChannelSettings p = null;
                        if ((mCurrntCmd == null) ||
                            (mCurrntCmd.mCmdDet.typeOfCommand != CommandType.OPEN_CHANNEL.value())) {
                                UtkLog.d(this, "mCurrntCmd is null , and use mCatchChannelCmd");
                                if (mCatchChannelCmd == null) {
                                    UtkLog.d(this, "mCurrntCmd is null , and mCatchChannelCmd is null");
                                    resMsg.resCode = ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
                                    break;
                                }
                                p = mCatchChannelCmd.getOpenChannelSettings();
                            } else {
                                p = mCurrntCmd.getOpenChannelSettings();
                            }
                        //
                        mBipService.openChannel(p);
                        return;
                    } else {
                        resMsg.resCode = ResultCode.USER_NOT_ACCEPT;
                        mCatchChannelCmd = null;
                    }
                }
                //in call
                //ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS
                break;
            //bip end
            }
            break;
        case NO_RESPONSE_FROM_USER:
        case UICC_SESSION_TERM_BY_USER:
        case BACKWARD_MOVE_BY_USER:
            resp = null;
            break;
        default:
            return;
        }
        sendTerminalResponse(cmdDet, resMsg.resCode, false, 0, resp);
        mCurrntCmd = null;
    }

    private LocalInfo getRecodeLocalinfo() {
      LocalInfo lc = new LocalInfo();

      lc.copyFrom(mLocalInfo);

      int myMap[] = { 9, 0, 1, 2, 3, 4, 5, 6, 7, 8 };
      int mcc = lc.MCC;
      int mnc = lc.IMSI_11_12;

      UtkLog.d("LocalInfo", "mLocalInfo MCC:" + lc.MCC + " IMSI:" + lc.IMSI_11_12);

      if (mcc < 1000)
      {
        lc.MCC = myMap[mcc / 100] * 100;
        mcc%=100;
        lc.MCC += myMap[mcc / 10] * 10;
        lc.MCC += myMap[mcc % 10];
      }
      else
      {
        lc.MCC = myMap[mcc / 1000] * 1000;
        mcc%=1000;
        lc.MCC += myMap[mcc / 100] * 100;
        mcc%=100;
        lc.MCC += myMap[mcc / 10] * 10;
        lc.MCC += myMap[mcc % 10];
      }

      if (mnc < 100)
      {
        lc.IMSI_11_12 = myMap[mnc / 10] * 10;
        lc.IMSI_11_12 += myMap[mnc % 10];
      } else {
        lc.IMSI_11_12 = myMap[mnc / 100] * 100;
        mnc %= 100;
        lc.IMSI_11_12 += myMap[mnc / 10] * 10;
        lc.IMSI_11_12 += myMap[mnc % 10];
      }

      return lc;
    }

     //bip start
    private void handleEventDownload(UtkResponseMessage resMsg) {
        eventDownload(resMsg.event, resMsg.sourceId, resMsg.destinationId,
                resMsg.additionalInfo, resMsg.oneShot);
    }

    public synchronized void onEventDownload(UtkResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = this.obtainMessage(MSG_ID_EVENT_DOWNLOAD, resMsg);
        msg.sendToTarget();
    }

    private void handleUtkTimerTick() {
        UtkLog.d(this, "handleUtkTimerTick");

        //id is 1---8;
        for (int i = 1; i <= UTK_TIMER_MAX; i++) {
            UtkTimerManagementData td = getTimerManagement(i);
            if (td != null) {
                td.timerTick();
                if (td.getRemaining() < 0) {
                    removeTimerManagement(td.getTimerId());
                }
            }
        }

        if (mTimerManagementHash.size() == 0) {
            stopUtkTimer();
        }
    }

    private void addTimerManagement(int timerId, byte[] bcdData) {
        UtkLog.d(this, "addTimerManagement id=" + timerId);

        UtkTimerManagementData td = new UtkTimerManagementData(sActiveInstance, timerId, bcdData);
        synchronized (mTimerManagementHash) {
            //remove the old, if exist
            mTimerManagementHash.remove(timerId);
            mTimerManagementHash.put(timerId, td);
        }
    }

    private void removeTimerManagement(int timerId) {
        UtkLog.d(this, "removeTimerManagement id=" + timerId);

         synchronized (mTimerManagementHash) {
             mTimerManagementHash.remove(timerId);
         }
    }

    private UtkTimerManagementData getTimerManagement(int timerId) {
         synchronized (mTimerManagementHash) {
             return mTimerManagementHash.get(timerId);
         }
    }

    public static byte[] bcdToDigit(byte[] bcd) {
        if (bcd == null) {
            return null;
        }
        byte[] digit = new byte[bcd.length];

        for (int i = 0; i < bcd.length; i++) {
            byte l = (byte) (bcd[i] & 0x0f);
            if (l > 9) {
                return null;
            }
            byte h = (byte) ((bcd[i] & 0xf0) >> 4);

            digit[i] = (byte) ((l * 10) + h);
        }

        return digit;
    }

    public static byte[] digitTobcd(byte[] digit) {
        if (digit == null) {
            return null;
        }
        byte[] bcd = new byte[digit.length];

        for (int i = 0; i < digit.length; i++) {
            byte l = (byte) (digit[i] % 10);
            byte h = (byte) (digit[i] / 10);

            bcd[i] = (byte) ((l << 4) | h);
        }

        return bcd;
    }

    public class ServiceStateReceiver extends BroadcastReceiver {
        int mOldState = -1;
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null) {
                return;
            }
            if (intent.getAction().equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,-1);
                UtkLog.d(LOG_TAG, "service state changed phoneId = " + phoneId);
                if (phoneId != sActiveUtkId) {
                    UtkLog.d(LOG_TAG, "ignore unuseful service state");
                    return;
                }
                ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
                if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                    mServiceState = BipConstants.SERVICE_STATE_NORMAL;
                } else if (serviceState.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
                    mServiceState = BipConstants.SERVICE_STATE_NOSERVICE;
                } else if (serviceState.getState() == ServiceState.STATE_EMERGENCY_ONLY) {
                    mServiceState = BipConstants.SERVICE_STATE_LIMITED;
                }

                UtkLog.d(LOG_TAG, "service state changed=" + mServiceState);
                if (mOldState != mServiceState) {
                    mOldState = mServiceState;
                    //location status event download
                    checkLocationEvent();
                }
            }
        }
    }

    private void locationStatusEventDownload() {
        UtkLog.d(this, "locationStatusEventDownload");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean isRightFillLocInfo = false;

        //location status
        buf.write(ComprehensionTlvTag.LOCATION_STATUS.value());
        buf.write(1); //length

        UtkLog.d(this, "mServiceState=" + mServiceState);

        //location information
        if (mServiceState == BipConstants.SERVICE_STATE_NORMAL) {
            //mLocalInfo.localInfoFormat(buf);
            UtkLog.d(this, "locationStatusEventDownload v2.0 ");
            LocalInfo lc = getRecodeLocalinfo();
            UtkLog.d(this, "lc.MCC = " + lc.MCC + ", lc.IMSI_11_12 = " + lc.IMSI_11_12);
            if ((lc.MCC != 0) && (lc.IMSI_11_12 != 0)) {
                buf.write(BipConstants.SERVICE_STATE_NORMAL);
                UtkLog.d(this, "locationStatusEventDownload include localInfo");
                lc.localInfoFormat(buf);
                isRightFillLocInfo = true;
            }
        }

        //align the local infomation, if location information is invalid,
        //will be fill no service instead
        UtkLog.d(this, "isRightFillLocInfo = " + isRightFillLocInfo);
        if (!isRightFillLocInfo) {
            buf.write(BipConstants.SERVICE_STATE_NOSERVICE);
        }

        byte[] additionalInfo = buf.toByteArray();

        if ((mLocationStatusString == null) ||
           (mLocationStatusString.compareTo(additionalInfo.toString()) != 0)) {
            if (isBusyOnCall()) {
                UtkLog.d(this, "phone is busy, event download abort");
            } else {
              eventDownload(EVENT_LIST_LOCATION_STATUS, 0x82, 0x81, additionalInfo, false);
              mLocationStatusString = new String(additionalInfo);
            }
        } else {
            UtkLog.d(this, "the location status already download");
        }
    }

    private void checkLocationEvent() {
        UtkLog.d(this, "checkLocationEvent");

        String st = IccUtils.bytesToHexString(mEventList);
        if (st != null && st.contains("03")) {
            UtkLog.d(this, "getUtkLocalInfo");
            mCmdIf.getUtkLocalInfo(obtainMessage(MSG_ID_EVENT_LOCAL_INFO));
        }
    }
    
    private void checkBipEvent() {
        UtkLog.d(this, "checkBipEvent");
        StringBuilder mSb = new StringBuilder("");
        
        String st = IccUtils.bytesToHexString(mEventList);
        if (st != null) {
            if (st.contains("09")) {
                UtkLog.d(this, "Bip event data available");
                mSb.append("09");
            }
            if (st.contains("0a")) {
                UtkLog.d(this, "Bip event channel status");
                mSb.append("0a");
            }
        }
        String mEvents = mSb.toString();
        UtkLog.d(this, "checkBipEvent: mEvents = " + mEvents);
        if (!(mEvents.equals(""))) {
            byte[] mUtkEvents = null;
            mUtkEvents = IccUtils.hexStringToBytes(mEvents);
            if (mUtkEvents != null) {
                UtkLog.d(this, "checkBipEvent: mUtkEvents = " + mUtkEvents.toString());
                Intent intent = new Intent(AppInterface.UTK_SETUP_EVENT_LIST_ACTION);
                intent.putExtra("PHONE_ID", mPhoneId);
                intent.putExtra("UTK_EVENTS", mUtkEvents);
                mContext.sendBroadcast(intent);
            }
        }
    }

    private void broadcastEventlist() {
        String st = null;
        st = IccUtils.bytesToHexString(mEventList);
        UtkLog.d(this, "broadcastEventlist mEventList: " + st);
        if (st != null) {
            Intent intent = new Intent(AppInterface.UTK_SETUP_EVENT_LIST_ACTION);
            intent.putExtra("PHONE_ID", mPhoneId);
            intent.putExtra("UTK_EVENTS", mEventList);
            mContext.sendBroadcast(intent);
        }       
    }

     private boolean isBusyOnCall() {
        UtkLog.d(this, "isBusyOnCall");

        PhoneConstants.State s;
        Phone phone = PhoneFactory.getDefaultPhone();
        //Temp solution
        s = phone.getState();
        /*
        if(FeatureOption.MTK_GEMINI_SUPPORT == true) {
            s = ((GeminiPhone)phone).getState();
        } else {
            s = phone.getState();
        }

        UtkLog.d(this, "phone state: " + s); */

        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                                                    Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        boolean inDataCall = false;
        if (netInfo != null) {
            inDataCall = netInfo.isConnected();
        }

        UtkLog.d(this, "inDataCall: " + inDataCall);

        return (inDataCall || (s != PhoneConstants.State.IDLE));
    }

    private void startUtkTimer() {
        synchronized (mTimerLock) {
            if (mUtkTicker == null) {
                UtkLog.d(this, " get new mUtkTicker");

                mUtkTicker = new Timer();
                mUtkTicker.schedule(new TimerTask() {
                    public void run() {
                        Message.obtain(sActiveInstance, MSG_ID_TIMER_TICK, null).sendToTarget();
                    }
                }, UTK_TIMEUPDATE_PERIOD, UTK_TIMEUPDATE_PERIOD);
            } else {
                UtkLog.d(this, "mUtkTicker already running");
            }
        }
    }

    private void stopUtkTimer() {
        UtkLog.d(this, "stopUtkTimer");

        synchronized (mTimerLock) {
            if (mUtkTicker != null) {
                mUtkTicker.cancel();
                mUtkTicker.purge();
                mUtkTicker = null;
            }
        }
    }
//bip end

    private int getMutliSimType() {
        int phoneNum = TelephonyManager.getDefault().getPhoneCount();
        int[] cardType = new int[phoneNum];
        cardType = UiccController.getInstance().getC2KWPCardType();
        int targetCardType = cardType[mPhoneId];
        if (((targetCardType & UiccController.CARD_TYPE_RUIM) > 0
                || (targetCardType & UiccController.CARD_TYPE_CSIM) > 0)
                && ((targetCardType & UiccController.CARD_TYPE_USIM) > 0)) {
            return UTK_CARD_TYPE_UIM_AND_USIM;
        } else if ((targetCardType & UiccController.CARD_TYPE_RUIM) > 0
                || (targetCardType & UiccController.CARD_TYPE_CSIM) > 0) {
            return UTK_CARD_TYPE_UIM_ONLY;
        } else {
            return UTK_CARD_TYPE_UNKNOW;
        }
    }

    private int getUtkTrType(AppInterface.CommandType cmdType) {
        boolean isPSCmd = false;

        AppInterface.CommandType[] mPsCmdTable = {
            AppInterface.CommandType.OPEN_CHANNEL,
            AppInterface.CommandType.CLOSE_CHANNEL,
            AppInterface.CommandType.RECEIVE_DATA,
            AppInterface.CommandType.SEND_DATA,
            AppInterface.CommandType.GET_CHANNEL_STATUS
        };

        UtkLog.d(this, "UTK LTE: cmdType = " + cmdType);

        for (int i = 0; i < mPsCmdTable.length; i++) {
            if (cmdType == mPsCmdTable[i]) {
                isPSCmd = true;
                break;
            }
        }

        if (true == isPSCmd) {
            return UTK_PS_TR;
        } else {
            return UTK_NPS_TR;
        }
    }

    private int getUtkEnvType(int env) {
        boolean isPSCmd = false;

        int[] mPSEnvCmdTable = {
            EVENT_LIST_DATA_AVAILABLE,
            EVENT_LIST_CHANNEL_STATUS
        };

        UtkLog.d(this, "UTK LTE: env = " + env);

        for (int i = 0; i < mPSEnvCmdTable.length; i++) {
            if (env == mPSEnvCmdTable[i]) {
                isPSCmd = true;
                break;
            }
        }

        if (true == isPSCmd) {
            return UTK_PS_ENV;
        } else {
            return UTK_NPS_ENV;
        }
    }

    private int getSvlteUtkCommandType(int res, int typeOfCmd) {
        int utkCmdType = 0;
        UtkLog.d(this, "UTK LTE: res = " + res + " typeOfCmd = " + typeOfCmd);

        if (UTK_TR == res) {
            AppInterface.CommandType cmdType = AppInterface.CommandType.fromInt(typeOfCmd);
            utkCmdType = getUtkTrType(cmdType);
        } else if (UTK_ENV == res) {
            utkCmdType = getUtkEnvType(typeOfCmd);
        } else {
            UtkLog.d(this, "UTK LTE: inValid commands type");
        }

        UtkLog.d(this, "UTK LTE: utkCmdType = " + utkCmdType);
        return utkCmdType;
    }


    //Add for UTK IR case
    /**
     * Query the utk menu from MD3.
     *
     * @param null
     *
     */
    public void queryUtkSetupMenuFromMD() {
        UtkLog.d(this, "query utk menu from modem");
        Message msg = obtainMessage(MSG_ID_MENU_INFO);

        //mCmdIf.queryStkSetUpMenuFromMD(null, msg);
        mCmdIf.queryUtkSetupMenuFromMD(null, msg);
    }

    /**
     * Query the radio state.
     *
     * @return true if radio on
     *
     */
     public boolean isRadioOn() {
        RadioState mRadioState = mCmdIf.getRadioState();
        UtkLog.d(this, "getRadioState: " + mRadioState);
        if (mRadioState == RadioState.RADIO_ON) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Register ril events to Lte Ril.
     *
     */
    public void registerLteRilEvents() {
        sLteDcPhoneProxy = (LteDcPhoneProxy) PhoneFactory.getPhone(mPhoneId);
        if (sLteDcPhoneProxy != null) {
            UtkLog.d(this, "UTK LTE: sLteDcPhoneProxy != null ");
            sRilDcArbitrator = sLteDcPhoneProxy.getRilDcArbitrator();
            if (sRilDcArbitrator != null) {
                UtkLog.d(this, "UTK LTE: sRilDcArbitrator != null ");
            }
            sLtePhone = (PhoneBase) sLteDcPhoneProxy.getLtePhone();
            if (sLtePhone != null) {
                UtkLog.d(this, "UTK LTE: sLtePhone != null ");
                mLteCmdIf = sLtePhone.mCi;
            }
            if (mLteCmdIf != null) {
                mLteCmdIf.setOnUtkSessionEnd(this, MSG_ID_SESSION_END, null);
                mLteCmdIf.setOnUtkProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
                //mLteCmdIf.setOnUtkEvent(this, MSG_ID_EVENT_NOTIFY, null);
            }
        }      
    }

    /**
     * Unregister ril events from Lte Ril.
     *
     */
    public void unRegisterLteRilEvents() {
        UtkLog.d(this, "unRegister Lte Ril events");
        if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport()) &&
                (UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType) &&
                    (mLteCmdIf != null)) {
            mLteCmdIf.unSetOnUtkSessionEnd(this);
            mLteCmdIf.unSetOnUtkProactiveCmd(this);
            //mLteCmdIf.unSetOnUtkEvent(this);
        }
    }

    public static int getActiveUtkId() {
        UtkLog.d(LOG_TAG, "return active utk id: " + sActiveUtkId);
        return sActiveUtkId;
    }

    private String getformattedCode(String str, int max) {
        if (str != null) {
            if (str.length() > max) {
                return str.substring(0, max);
            } else {
                return str;
            }
        } else {
            return null;
        }
    }

    private String getModelMessage() {
        StringBuffer data = new StringBuffer();
        byte[] mCharEncoding = new byte[1];
        byte[] mLangIndicator = new byte[1];
        String mModel = Build.MODEL;
        String mManufacturer = Build.MANUFACTURER;
        String mSwVersion = Build.VERSION.RELEASE;
        final int mModelMaxLength = 32;
        final int mManufacturerMaxLength = 32;
        final int mSwVersionMaxLength = 60;

        UtkLog.d(this, "getModelMessage mModel = " + mModel
                        + " mManufacturer = " + mManufacturer
                        + " mSwVersion = " + mSwVersion);

        mCharEncoding[0] = 2;
        data.append(IccUtils.bytesToHexString(mCharEncoding));
        mLangIndicator[0] = 1;
        data.append(IccUtils.bytesToHexString(mLangIndicator));
        data.append(IccUtils.bytesToHexString(getformattedCode(mModel,
            mModelMaxLength).getBytes()));
        if (mModel.length() < mModelMaxLength) {
            for (int i = 0; i < (mModelMaxLength - mModel.length()); i++) {
                data.append("FF");
            }
        }
        data.append(IccUtils.bytesToHexString(getformattedCode(mManufacturer,
            mManufacturerMaxLength).getBytes()));
        if (mManufacturer.length() < mManufacturerMaxLength) {
            for (int i = 0; i < (mManufacturerMaxLength - mManufacturer.length()); i++) {
                data.append("FF");
            }
        }
        data.append(IccUtils.bytesToHexString(getformattedCode(mSwVersion,
            mSwVersionMaxLength).getBytes()));
        if (mSwVersion.length() < mSwVersionMaxLength) {
            for (int i = 0; i < (mSwVersionMaxLength - mSwVersion.length()); i++) {
                data.append("FF");
            }
        }
        UtkLog.d(this, "getModelMessage data = " + data.toString().toUpperCase());
        return data.toString().toUpperCase();
    }
}
