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

import android.os.Handler;
import android.os.Message;
import android.os.AsyncResult;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.Rlog;

import com.android.ims.ImsManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.IRadioPower;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

public class RadioManager extends Handler  {

    static final String LOG_TAG = "RadioManager";
    private static final String PREF_CATEGORY_RADIO_STATUS = "RADIO_STATUS";
    private static RadioManager sRadioManager;

    private static final int MODE_PHONE1_ONLY = 1;
    private static final int MODE_PHONE2_ONLY = 2;
    private static final int MODE_PHONE3_ONLY = 4;
    private static final int MODE_PHONE4_ONLY = 8;

    private static final int INVALID_PHONE_ID = -1;

    private static final int SIM_NOT_INITIALIZED = -1;
    private static final int NO_SIM_INSERTED = 0;
    private static final int SIM_INSERTED = 1;

    private static final boolean ICC_READ_NOT_READY = false;
    private static final boolean ICC_READ_READY = true;

    private static final int INITIAL_RETRY_INTERVAL_MSEC = 200;

    private static final boolean RADIO_POWER_OFF = false;
    private static final boolean RADIO_POWER_ON = true;

    private static final boolean MODEM_POWER_OFF = false;
    private static final boolean MODEM_POWER_ON = true;

    private static final boolean AIRPLANE_MODE_OFF = false;
    private static final boolean AIRPLANE_MODE_ON = true;
    private boolean mAirplaneMode = AIRPLANE_MODE_OFF;

    private static final boolean WIFI_ONLY_MODE_OFF = false;
    private static final boolean WIFI_ONLY_MODE_ON = true;
    private boolean mWifiOnlyMode = WIFI_ONLY_MODE_OFF;
    private static final String ACTION_WIFI_ONLY_MODE_CHANGED = "android.intent.action.ACTION_WIFI_ONLY_MODE";

    private static final String STRING_NO_SIM_INSERTED = "N/A";

    private static final String PROPERTY_SILENT_REBOOT_MD1 = "gsm.ril.eboot";
    private static final String PROPERTY_SILENT_REBOOT_MD2 = "gsm.ril.eboot.2";
    private static final String CDMA_PROPERTY_SILENT_REBOOT_MD = "cdma.ril.eboot";

    private static final String IS_NOT_SILENT_REBOOT = "0";
    private static final String IS_SILENT_REBOOT = "1";
    private static final String NO_NAME = "NO_NAME";

    public static final String ACTION_FORCE_SET_RADIO_POWER =
        "com.mediatek.internal.telephony.RadioManager.intent.action.FORCE_SET_RADIO_POWER";

    private int[] mSimInsertedStatus;
    private Context mContext;
    private int[] mInitializeWaitCounter;
    private CommandsInterface[] mCi;
    private static SharedPreferences mIccidPreference;
    private int mPhoneCount;
    private int mBitmapForPhoneCount;
    //For checking if ECC call is on-going to bypass turning off radio
    private boolean mIsEccCall;

    private boolean[] mModemPower;

    // Record ipo shutdown request for those phone which in radio not available state,
    // should send ipo shutdown request again when radio state is available
    private boolean bIsQueueIpoShutdown;

    // Is in IPO shutdown, needs to block all radio power on request
    // ex. ECC do not recognize IPO shutdown, so it will force set radio power on after its timeout
    // in this scenario, modem will power-on wrongly: airplane on -> ecc call -> ipo shutdown
    // -> ecc trigger airplane off -> ecc timeout -> force set radio power on
    private boolean bIsInIpoShutdown;

    private ImsSwitchController mImsSwitchController = null;

    static protected ConcurrentHashMap<IRadioPower, String> mNotifyRadioPowerChange
            = new ConcurrentHashMap<IRadioPower, String>();

    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };

    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };

    private static final String  PROPERTY_ICCID_SIM_C2K = "ril.iccid.sim1_c2k";
    private static final String  PROPERTY_ICCID_SIM_LTE = "ril.iccid.sim1_lte";
    private static final String  PROPERTY_RIL_CARD_TYPE_SET = "gsm.ril.cardtypeset";
    private static final String  PROPERTY_RIL_CARD_TYPE_SET_2 = "gsm.ril.cardtypeset.2";
    private static final String  PROPERTY_CONFIG_EMDSTATUS_SEND = "ril.cdma.emdstatus.send";

    private static final int CARD_TYPE_SIM  = 1;
    private static final int CARD_TYPE_USIM = 2;
    private static final int CARD_TYPE_CSIM = 4;
    private static final int CARD_TYPE_RUIM = 8;

    public enum SwitchImsScenario {
        SWITCH_IMS_RADIO_ON_OFF,
        SWITCH_IMS_RUNTIME,
        SWITCH_IMS_RADIO_NOT_AVAILABLE,
    };

    private int mImsState   = PhoneConstants.IMS_STATE_DISABLED;

    /** events id definition */
    private static final int EVENT_RADIO_AVAILABLE = 1;
    private static final int EVENT_VIRTUAL_SIM_ON = 2;
    
    // C2K 5M (CLLWG)
    private final static String C2K_5M = "CLLWG";
    private final static String IS_INDIAN = "1";

    public static RadioManager init(Context context, int phoneCount, CommandsInterface[] ci) {
        synchronized (RadioManager.class) {
            if (sRadioManager == null) {
                sRadioManager = new RadioManager(context, phoneCount, ci);
            }
            return sRadioManager;
        }
    }

    ///M: [SVLTE] Add for the airplane mode frequently switch issue.@{
    private AirplaneRequestHandler mAirplaneRequestHandler;
    /// @}
    
    private List<RadioPowerRunnable> mRunnables = new ArrayList<RadioPowerRunnable>();
    /**
     * @internal
     */
    public static RadioManager getInstance() {
        return sRadioManager;
    }

    private RadioManager(Context context , int phoneCount, CommandsInterface[] ci) {

        int airplaneMode = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        int wifionlyMode = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.WIFI_PREFERRED);
                
        log("Initialize RadioManager under airplane mode:" + airplaneMode + " wifi only mode:" + wifionlyMode);

        mSimInsertedStatus = new int[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            mSimInsertedStatus[i] = SIM_NOT_INITIALIZED;
        }
        mInitializeWaitCounter = new int[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            mInitializeWaitCounter[i] = 0;
        }

        mContext = context;
        mAirplaneMode = ((airplaneMode == 0) ? AIRPLANE_MODE_OFF : AIRPLANE_MODE_ON);       
        mWifiOnlyMode = (TelephonyManager.WifiCallingPreferences.WIFI_ONLY == wifionlyMode);        
        mCi = ci;
        mPhoneCount = phoneCount;
        mBitmapForPhoneCount = convertPhoneCountIntoBitmap(phoneCount);
        mIccidPreference = mContext.getSharedPreferences(PREF_CATEGORY_RADIO_STATUS, 0);
        mModemPower = new boolean[mPhoneCount];

        mImsSwitchController = new ImsSwitchController(mContext, mPhoneCount, mCi);

        for (int i = 0; i < phoneCount; i++) {
            mModemPower[i] = MODEM_POWER_ON;
        }

        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            log("Not BSP Package, register intent!!!");
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            filter.addAction(ACTION_FORCE_SET_RADIO_POWER);
            filter.addAction(ACTION_WIFI_ONLY_MODE_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter);

            // For virtual SIM
            for (int i = 0; i < phoneCount; i++) {
                Integer index = new Integer(i);
                mCi[i].registerForVirtualSimOn(this, EVENT_VIRTUAL_SIM_ON, index);
                mCi[i].registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
            }

        }
        ///M: [SVLTE]Add for the airplane mode frequently switch issue.@{
        //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
        mAirplaneRequestHandler = new AirplaneRequestHandler(mContext, mPhoneCount);
        //}
        ///@}
    }

    private int convertPhoneCountIntoBitmap(int phoneCount) {
        int ret = 0;
        for (int i = 0; i < phoneCount; i++) {
            ret += MODE_PHONE1_ONLY << i;
        }
        log("Convert phoneCount " + phoneCount + " into bitmap " + ret);
        return ret;
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

           log("BroadcastReceiver: " + intent.getAction());

            if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                onReceiveSimStateChangedIntent(intent);
            } else if (intent.getAction().equals(ACTION_FORCE_SET_RADIO_POWER)) {
                onReceiveForceSetRadioPowerIntent(intent);
            } else if (intent.getAction().equals(ACTION_WIFI_ONLY_MODE_CHANGED)) {
                onReceiveWifiOnlyModeStateChangedIntent(intent);
            }
        }
    };

    // For SVLTE
    // IPO power on, c2k iccid may not ready when SIM state changed, this may cause not set radio power for LTE phone
    private class SimStateChangedRunnable implements Runnable {
            Intent retryIntent;
            public  SimStateChangedRunnable(Intent intent) {
                retryIntent = intent;
            }
            @Override
            public void run() {
                onReceiveSimStateChangedIntent(retryIntent);
            }
        }

    private void onReceiveSimStateChangedIntent(Intent intent) {
        String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

        // TODO: phone_key now is equals to slot_key, change in the future
        int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, INVALID_PHONE_ID);

        if (!isValidPhoneId(phoneId)) {
            log("INTENT:Invalid phone id:" + phoneId + ", do nothing!");
            return;
        }

        log("INTENT:SIM_STATE_CHANGED: " + intent.getAction() + ", sim status: " + simStatus + ", phoneId: " + phoneId );

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
            && !(SystemProperties.get(PROPERTY_RIL_CARD_TYPE_SET).equals("1")
            && SystemProperties.get(PROPERTY_RIL_CARD_TYPE_SET_2).equals("1"))) {
            return;
        }
        boolean desiredRadioPower = RADIO_POWER_ON;

        if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(simStatus)
            || IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(simStatus)
            || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)) {
            mSimInsertedStatus[phoneId] = SIM_INSERTED;
            log("Phone[" + phoneId + "]: " + simStatusToString(SIM_INSERTED));

            // if we receive ready, but can't get iccid, we do nothing
            String iccid = readIccIdUsingPhoneId(phoneId);
            if (STRING_NO_SIM_INSERTED.equals(iccid)) {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                        && IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)
                        && phoneId == SvlteModeController.getCdmaSocketSlotId()
                        && SvlteUiccUtils.getInstance().isUsimWithCsim(phoneId)) {
                    log("CT 4G card SIM state loaded, c2k iccid not ready, wait for it...");
                    SimStateChangedRunnable simStateChangedRunnable = new SimStateChangedRunnable(intent);
                    postDelayed(simStateChangedRunnable, INITIAL_RETRY_INTERVAL_MSEC);
                    return;
                } else {
                    log("Phone " + phoneId + ":SIM ready but ICCID not ready, do nothing");
                    return;
                }
            } else if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && (phoneId == SvlteModeController.getCdmaSocketSlotId())
                && (!SvlteUiccUtils.getInstance().isHaveCard(phoneId))) {
                log("Phone " + phoneId + ": No card, do nothing");
                return;
            }

            desiredRadioPower = RADIO_POWER_ON;
            if (mAirplaneMode == AIRPLANE_MODE_OFF) {
                log("Set Radio Power due to SIM_STATE_CHANGED, power: " + desiredRadioPower + ", phoneId: " + phoneId);
                setRadioPower(desiredRadioPower, phoneId);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        SvlteUiccUtils.getInstance().isUsimWithCsim(phoneId) &&
                        (phoneId == SvlteModeController.getCdmaSocketSlotId())) {
                    log("SVLTE phone id:" + phoneId + " SIM_STATE_CHANGED, need to check card type for LTE phone radio");
                    //for CT 4G card, need to set radio power for LTE phone
                    if (phoneId == 0 &&
                            isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_1) &&
                            SvlteRatController.getEngineerMode() != SvlteRatController.ENGINEER_MODE_CDMA) {
                        setRadioPower(desiredRadioPower, SubscriptionManager.LTE_DC_PHONE_ID_1);
                    } else if (phoneId == 1 &&
                            isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_2) &&
                            SvlteRatController.getEngineerMode() != SvlteRatController.ENGINEER_MODE_CDMA) {
                        setRadioPower(desiredRadioPower, SubscriptionManager.LTE_DC_PHONE_ID_2);
                    }
                }
            }
        }

        else if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && (phoneId == SvlteModeController.getCdmaSocketSlotId())
                && (SvlteUiccUtils.getInstance().isHaveCard(phoneId))) {
                return;
            }
            mSimInsertedStatus[phoneId] = NO_SIM_INSERTED;
            log("Phone[" + phoneId + "]: " + simStatusToString(NO_SIM_INSERTED));
            desiredRadioPower = RADIO_POWER_OFF;
            if (mAirplaneMode == AIRPLANE_MODE_OFF) {
                log("Set Radio Power due to SIM_STATE_CHANGED, power: " + desiredRadioPower + ", phoneId: " + phoneId);
                setRadioPower(desiredRadioPower, phoneId);
            }
        }
    }

    private void onReceiveForceSetRadioPowerIntent(Intent intent) {
        int phoneId = 0;
        int mode = intent.getIntExtra(Intent.EXTRA_MSIM_MODE, -1);
        log("force set radio power, mode: " + mode);
        if (mode == -1) {
            log("Invalid mode, MSIM_MODE intent has no extra value");
            return;
        }
        for (phoneId = 0; phoneId < mPhoneCount; phoneId++) {
            boolean singlePhonePower =
                ((mode & (MODE_PHONE1_ONLY << phoneId)) == 0) ? RADIO_POWER_OFF : RADIO_POWER_ON;
            if (RADIO_POWER_ON == singlePhonePower) {
                forceSetRadioPower(true, phoneId);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        (phoneId == SvlteModeController.getCdmaSocketSlotId())) {
                    if (phoneId == 0 &&
                            isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_1)) {
                        forceSetRadioPower(true, SubscriptionManager.LTE_DC_PHONE_ID_1);
                    } else if (phoneId == 1 &&
                            isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_2)) {
                        forceSetRadioPower(true, SubscriptionManager.LTE_DC_PHONE_ID_2);
                    }
                }
            }
        }
    }

    private boolean isValidPhoneId(int phoneId) {
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            return false;
        } else {
            return true;
        }
    }

    private String simStatusToString(int simStatus) {
        String result = null;
        switch(simStatus) {
            case SIM_NOT_INITIALIZED:
                result = "SIM HAVE NOT INITIALIZED";
                break;
            case SIM_INSERTED:
                result = "SIM DETECTED";
                break;
            case NO_SIM_INSERTED:
                result = "NO SIM DETECTED";
                break;
            }
        return result;
    }

    /**
     * enter or leave wifi only mode
     *
     */ 
    public void onReceiveWifiOnlyModeStateChangedIntent(Intent intent) {
        
        boolean enabled = intent.getBooleanExtra("state", false);
        log("mReceiver: ACTION_WIFI_ONLY_MODE_CHANGED, enabled = " + enabled);

        // we expect wifi only mode is on-> off or off->on
        if (enabled == mWifiOnlyMode) {
            log("enabled = " + enabled + ", mWifiOnlyMode = "+ mWifiOnlyMode + "is not expected (the same)");
            return;
        }

        mWifiOnlyMode = enabled;
        if (mAirplaneMode == AIRPLANE_MODE_OFF) {
            boolean radioPower = enabled ? RADIO_POWER_OFF : RADIO_POWER_ON;
            for (int i = 0; i < mPhoneCount; i++) {
                setRadioPower(radioPower, i);
            }
        }
    }

    /**
     * Modify mAirplaneMode and set modem power
     * @param enabled 0: normal mode
     *                1: airplane mode
     * @internal
     */
    public void notifyAirplaneModeChange(boolean enabled) {
        ///M: Add for the airplane mode frequently switch issue.@{
        if (!mAirplaneRequestHandler.allowSwitching()) {
            log("airplane mode switching, not allow switch now ");
            mAirplaneRequestHandler.pendingAirplaneModeRequest(enabled);
            return;
        }
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (mAirplaneRequestHandler.waitRadioAvaliable(enabled)) {
                log("airplane mode switching, not allow switch now, waitRadioAvaliable...");
                return;
            }
        }
        /// @}

        // we expect airplane mode is on-> off or off->on
        if (enabled == mAirplaneMode) {
            log("enabled = " + enabled + ", mAirplaneMode = "+ mAirplaneMode + "is not expected (the same)");
            return;
        }

        mAirplaneMode = enabled;
        log("Airplane mode changed:" + enabled);

        if (isFlightModePowerOffModemEnabled() && !isUnderCryptKeeper()) {
            log("Airplane mode changed: turn on/off all modem");
            boolean modemPower = enabled ? MODEM_POWER_OFF : MODEM_POWER_ON;
            ///M: Add for the airplane mode frequently switch issue.@{
            //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mAirplaneRequestHandler.onAirplaneChangeStarted(modemPower, true);
            //}
            /// @}
            setSilentRebootPropertyForAllModem(IS_SILENT_REBOOT);
            setModemPower(modemPower, mBitmapForPhoneCount);
            SystemProperties.set(PROPERTY_CONFIG_EMDSTATUS_SEND, "0");
            ///M: Add for the airplane mode frequently switch issue.@{
            //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mAirplaneRequestHandler.monitorAirplaneChangeDone();
            //}
            /// @}
        } else if (isMSimModeSupport()) {
            log("Airplane mode changed: turn on/off all radio");
            boolean radioPower = enabled ? RADIO_POWER_OFF : RADIO_POWER_ON;
            ///M: Add for the airplane mode frequently switch issue.@{
            //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mAirplaneRequestHandler.onAirplaneChangeStarted(radioPower, false);
            //}
            /// @}
            for (int i = 0; i < mPhoneCount; i++) {
                setRadioPower(radioPower, i);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    if (radioPower == false) {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(i)).getLtePhone().setRadioPower(radioPower);
                    } else {
                        if (i == 0 &&
                                isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_1)) {
                            SvlteUtils.getSvltePhoneProxy(i).getLtePhone().setRadioPower(radioPower);
                        } else if (i == 1 &&
                                isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_2)) {
                            SvlteUtils.getSvltePhoneProxy(i).getLtePhone().setRadioPower(radioPower);
                        }
                    }
                }
            }
            ///M: Add for the airplane mode frequently switch issue.@{
            //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mAirplaneRequestHandler.monitorAirplaneChangeDone();
            //}
            /// @}
        }
    }

    /*
     *A special paragraph, not to trun off modem power under cryptkeeper
     */
    private boolean isUnderCryptKeeper() {
        if (SystemProperties.get("ro.crypto.state").equals("encrypted")
            && SystemProperties.get("vold.decrypt").equals("trigger_restart_min_framework")) {
            log("[Special Case] Under CryptKeeper, Not to turn on/off modem");
            return true;
        }
        log("[Special Case] Not Under CryptKeeper");
        return false;
    }

    private void setSilentRebootPropertyForAllModem(String isSilentReboot) {
        TelephonyManager.MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();
        switch(config) {
            case DSDS:
                log("set eboot under DSDS");
                SystemProperties.set(PROPERTY_SILENT_REBOOT_MD1, isSilentReboot);
                break;
            case DSDA:
                log("set eboot under DSDA");
                SystemProperties.set(PROPERTY_SILENT_REBOOT_MD1, isSilentReboot);
                SystemProperties.set(PROPERTY_SILENT_REBOOT_MD2, isSilentReboot);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    SystemProperties.set(CDMA_PROPERTY_SILENT_REBOOT_MD, isSilentReboot);
                }
                break;
            case TSTS:
                log("set eboot under TSTS");
                SystemProperties.set(PROPERTY_SILENT_REBOOT_MD1, isSilentReboot);
                break;
            default:
                log("set eboot under SS");
                SystemProperties.set(PROPERTY_SILENT_REBOOT_MD1, isSilentReboot);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    SystemProperties.set(CDMA_PROPERTY_SILENT_REBOOT_MD, isSilentReboot);
                }
                break;
        }
    }

    /*
     * Called From GSMSST, if boot up under airplane mode, power-off modem
     */
    public void notifyRadioAvailable(int phoneId) {
        log("Phone " + phoneId + " notifies radio available");
        if (mAirplaneMode == AIRPLANE_MODE_ON && isFlightModePowerOffModemEnabled() && !isUnderCryptKeeper()) {
            log("Power off modem because boot up under airplane mode");
            final int slotId = SvlteUtils.getSlotId(phoneId);
            setModemPower(MODEM_POWER_OFF, (1 << slotId));
        }
    }

    public void notifyIpoShutDown() {
        log("notify IPO shutdown!");
        bIsInIpoShutdown = true;

        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            // record IpoShutdown if there is any phone of radio state is not avaible,
            // then do ipo shutdown after radio available
            log("mCi[" + i + "].getRadioState().isAvailable(): " + mCi[i].getRadioState().isAvailable());
            if(!mCi[i].getRadioState().isAvailable()) {
                bIsQueueIpoShutdown = true;
            }
        }

        // it may fail on the phone which radio state is not available
        doIpoShutDown();
    }

    private void doIpoShutDown() {
        log("do IPO shutdown");
        SystemProperties.set(PROPERTY_RIL_CARD_TYPE_SET, "0");
        SystemProperties.set(PROPERTY_RIL_CARD_TYPE_SET_2, "0");
        SystemProperties.set(PROPERTY_CONFIG_EMDSTATUS_SEND, "0");
        setModemPower(MODEM_POWER_OFF, mBitmapForPhoneCount);
    }

    public void notifyIpoPreBoot() {
        log("IPO preboot!");
        bIsInIpoShutdown = false;
        bIsQueueIpoShutdown = false;
        setSilentRebootPropertyForAllModem(IS_NOT_SILENT_REBOOT);
        setModemPower(MODEM_POWER_ON, mBitmapForPhoneCount);
    }

    /**
     * Set modem power on/off according to DSDS or DSDA.
     *
     * @param power desired power of modem
     * @param phoneId a bit map of phones you want to set
     *              1: phone 1 only
     *              2: phone 2 only
     *              3: phone 1 and phone 2
     */
    public void setModemPower(boolean power, int phoneBitMap) {
        log("Set Modem Power according to bitmap, Power:" + power + ", PhoneBitMap:" + phoneBitMap);
        TelephonyManager.MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();

        int phoneId = 0;
        switch(config) {
            case DSDS:
                phoneId = findMainCapabilityPhoneId();
                log("Set Modem Power under DSDS mode, Power:" + power + ", phoneId:" + phoneId);
                /// M: c2k modify. @{
                if (PhoneFactory.getPhone(phoneId).getPhoneType() ==
                        PhoneConstants.PHONE_TYPE_CDMA) {
                    mCi[phoneId].setModemPower(power, null);
                } else { /// @}
                    for (int i = 0; i < mPhoneCount; i++) {
                        mModemPower[i] = power;
                    }
                    mCi[phoneId].setModemPower(power, null);
                }
                if (power == MODEM_POWER_OFF) {
                    for (int i = 0; i < mPhoneCount; i++) {
                        resetSimInsertedStatus(i);
                    }
                }
                break;

            case DSDA:
                for (int i = 0; i < mPhoneCount; i++) {
                    phoneId = i;
                    if ((phoneBitMap & (MODE_PHONE1_ONLY << i)) != 0) {
                        log("Set Modem Power under DSDA mode, Power:" + power + ", phoneId:" + phoneId);
                        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                            if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                                ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().mCi.setModemPower(power, null);
                                ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getNLtePhone().mCi.setModemPower(power, null);
                            } else {
                                ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().mCi.setModemPower(power, null);
                            }
                        } else {
                            /// M: c2k modify. @{
                            if (PhoneFactory.getPhone(phoneId).getPhoneType() ==
                                    PhoneConstants.PHONE_TYPE_CDMA) {
                                mCi[phoneId].setModemPower(power, null);
                            } else { /// @}
                                mModemPower[phoneId] = power;
                                mCi[phoneId].setModemPower(power, null);
                            }
                        }
                        if (power == MODEM_POWER_OFF) {
                            resetSimInsertedStatus(phoneId);
                        }
                    }
                }
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        SvlteModeController.getActiveSvlteModeSlotId() == SvlteModeController.CSFB_ON_SLOT) {
                    int cdmaSlotId = SvlteModeController.getCdmaSocketSlotId();
                    log("Set Modem Power for C2K, cdmaSlotId=" + cdmaSlotId + " ,power=" + power);
                    ((SvltePhoneProxy) PhoneFactory.getPhone(cdmaSlotId)).getNLtePhone().mCi.setModemPower(power, null);
                }
                break;

            case TSTS:
                phoneId = findMainCapabilityPhoneId();
                log("Set Modem Power under TSTS mode, Power:" + power + ", phoneId:" + phoneId);
                for (int i = 0; i < mPhoneCount; i++) {
                    mModemPower[i] = power;
                }
                mCi[phoneId].setModemPower(power, null);
                if (power == MODEM_POWER_OFF) {
                    for (int i = 0; i < mPhoneCount; i++) {
                        resetSimInsertedStatus(i);
                    }
                }
                break;

            default:
                //FIXME: To sort out SVLTE MD power configuration
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    log("Single SIM mode, for SVLTE need turn on/off LTE MD.");
                    PhoneBase phone =
                            ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone();
                    phone.mCi.setModemPower(power, null);
                    log("Single SIM mode, for SVLTE need turn on/off Non-LTE MD.");
                    phone = ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getNLtePhone();
                    phone.mCi.setModemPower(power, null);
                } else {
                    phoneId = PhoneFactory.getDefaultPhone().getPhoneId();
                    log("Set Modem Power under SS mode:" + power + ", phoneId:" + phoneId);
                    mModemPower[phoneId] = power;
                    mCi[phoneId].setModemPower(power, null);
                }

                break;
        }
    }

    private int findMainCapabilityPhoneId() {
        int result = 0;
        int switchStatus = Integer.valueOf(
                SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1"));
        result = switchStatus - 1;
        if (result < 0 || result >= mPhoneCount) {
            return 0;
        } else {
            return result;
        }
    }

    private class RadioPowerRunnable implements Runnable {
        boolean retryPower;
        int retryPhoneId;
        public  RadioPowerRunnable(boolean power, int phoneId) {
            retryPower = power;
            retryPhoneId = phoneId;
        }
        @Override
        public void run() {
            setRadioPower(retryPower, retryPhoneId);
        }
    }
    
    //add by jinlibo
    private void queryAndPostRunnable(boolean power, int phoneId){
        RadioPowerRunnable runnable = null;
        if(mRunnables.size() > 0){
            for(RadioPowerRunnable obj : mRunnables){
                if(power == obj.retryPower && phoneId == obj.retryPhoneId){
                    runnable = obj;
                    break;
                }
            }
        }
        if(runnable == null){
            runnable = new RadioPowerRunnable(power, phoneId);
            mRunnables.add(runnable);
        }
        if(!hasCallbacks(runnable)){
            postDelayed(runnable, INITIAL_RETRY_INTERVAL_MSEC);
        }
    }

    /*
     * MTK flow to control radio power
     */
    public void setRadioPower(boolean power, int phoneId) {
        log("setRadioPower, power=" + power + "  phoneId=" + phoneId);
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
            !SystemProperties.get(PROPERTY_CONFIG_EMDSTATUS_SEND).equals("1")) {
            log("emdstatus is not sent, wait for " + INITIAL_RETRY_INTERVAL_MSEC + "ms");
            //modifed by jinlibo for phone msg queue block
//            RadioPowerRunnable setRadioPowerRunnable = new RadioPowerRunnable(power, phoneId);
//            postDelayed(setRadioPowerRunnable, INITIAL_RETRY_INTERVAL_MSEC);
            queryAndPostRunnable(power, phoneId);
            //jinlibo modified end 
            return;
        }

        if (isFlightModePowerOffModemEnabled() && mAirplaneMode == AIRPLANE_MODE_ON) {
            log("Set Radio Power under airplane mode, ignore");
            return;
        }

        if (isModemPowerOff(phoneId)) {
            log("modem for phone " + phoneId + " off, do not set radio again");
            return;
        }

        /**
        * We want iccid ready berfore we check if SIM is once manually turned-offedd
        * So we check ICCID repeatedly every 300 ms
        */
        if (!isIccIdReady(phoneId)) {
            log("RILD initialize not completed, wait for " + INITIAL_RETRY_INTERVAL_MSEC + "ms");
            //jinlibo modified for phone msg block
//            RadioPowerRunnable setRadioPowerRunnable = new RadioPowerRunnable(power, phoneId);
//            postDelayed(setRadioPowerRunnable, INITIAL_RETRY_INTERVAL_MSEC);
            queryAndPostRunnable(power, phoneId);
            //jinlibo modified end
            return;
        }

        setSimInsertedStatus(phoneId);

        boolean radioPower = power;
        String iccId = readIccIdUsingPhoneId(phoneId);
        //adjust radio power according to ICCID
        if (mIccidPreference.contains(iccId)) {
            log("Adjust radio to off because once manually turned off, iccid: " + iccId + " , phone: " + phoneId);
            radioPower = RADIO_POWER_OFF;
        }

        if (mWifiOnlyMode == WIFI_ONLY_MODE_ON && mIsEccCall == false) {
            log("setradiopower but wifi only, turn off");
            radioPower = RADIO_POWER_OFF;
        }

        boolean isCTACase = checkForCTACase();

        /// M: [SVLTE] handle the RAT mode when radio power. Assume SVLTE in slot 0.@{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (power && (!isAllowRadioPowerOn(phoneId))) {
                log("not allow power on : +phoneId: " + phoneId);
                return;
            }

            if (power && (!isSvlteTestSimAllowPowerOn(phoneId))) {
                log("SvlteTest SIM: not allow power on : +phoneId: " + phoneId);
                return;
            }
        }
        /// @}

        if (getSimInsertedStatus(phoneId) == NO_SIM_INSERTED) {
            if (isCTACase == true) {
                int capabilityPhoneId = findMainCapabilityPhoneId();
                log("No SIM inserted, force to turn on 3G/4G phone " + capabilityPhoneId + " radio if no any sim radio is enabled!");
                PhoneFactory.getPhone(capabilityPhoneId).setRadioPower(RADIO_POWER_ON);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        phoneId == SvlteModeController.getCdmaSocketSlotId()) {
                    log("No SIM inserted, force to turn on LTE radio");
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().setRadioPower(RADIO_POWER_ON);
                }
            } else if (true == mIsEccCall) {
                log("ECC call Radio Power, power: " + radioPower + ", phoneId: " + phoneId);
                PhoneFactory.getPhone(phoneId).setRadioPower(radioPower);
            } else {
                log("No SIM inserted, turn Radio off!");
                radioPower = RADIO_POWER_OFF;
                PhoneFactory.getPhone(phoneId).setRadioPower(radioPower);
            }
        } else {
            log("Trigger set Radio Power, power: " + radioPower + ", phoneId: " + phoneId);
            // We must refresh sim setting during boot up or if we adjust power according to ICCID
            refreshSimSetting(radioPower, phoneId);
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                updatePhoneRadioPower(radioPower, phoneId);
            } else {
                PhoneFactory.getPhone(phoneId).setRadioPower(radioPower);
            }
        }
    }

    // For C2K SVLTE @{
    private int getSimInsertedStatus(int phoneId) {
        if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
            phoneId = 0;
        } else if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
            phoneId = 1;
        }
        return mSimInsertedStatus[phoneId];
    }

    private void updatePhoneRadioPower(boolean power, int phoneId) {
        Phone phone = SvlteUtils.getSvltePhoneProxy(phoneId).getPhoneById(phoneId);
        if (phone == null) {
            log("updatePhoneRadioPower: phone" + phoneId + " is null, skip");
            return;
        }
        phone.setRadioPower(power);
        /*
        // FIXME: To find a better way to get SVLTE card/phone slot
        final int svlteSlot = 0;
        SvlteRatController.SvlteRatMode ratMode = getSvlteRatMode(mContext);

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                (phoneId == svlteSlot || phoneId == SubscriptionManager.LTE_DC_PHONE_ID)) {
            if (SvlteModeController.getRadioTechnologyMode()
                == SvlteModeController.RADIO_TECH_MODE_CSFB) {
                if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID) {
                    if (power) {
                        if (SvlteUiccUtils.getInstance().isUsimWithCsim(svlteSlot) &&
                                ratMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G &&
                                SvlteRatController.getEngineerMode()
                                        != SvlteRatController.ENGINEER_MODE_CDMA &&
                                SvlteRatController.getEngineerMode()
                                        != SvlteRatController.ENGINEER_MODE_LTE) {
                            phone.setRadioPower(power);
                            log("updatePhoneRadioPower: CSFB mode, 4G Mode, turn on radio!");
                        }
                    } else {
                        if (SvlteUiccUtils.getInstance().isUsimWithCsim(svlteSlot) &&
                                ratMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G) {
                            log("updatePhoneRadioPower: CSFB mode, 4G Mode, Not turn off radio!");
                            return;
                        }
                        phone.setRadioPower(power);
                    }
                } else {
                    phone.setRadioPower(power);
                }
            } else {
                if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID && power) {
                    // LTE: need enable radio when UICC is 4G CT
                    if (SvlteUiccUtils.getInstance().isUsimSim(svlteSlot)
                            && SvlteRatController.getEngineerMode()
                                != SvlteRatController.ENGINEER_MODE_CDMA) {
                        phone.setRadioPower(power);
                    } else {
                        log("updatePhoneRadioPower: slot0 not CT LTE card, no need turn on radio!");
                    }
                } else if (phoneId == svlteSlot && power) {
                    // C2K: need enable radio when UICC is CDMA card
                    if (SvlteUiccUtils.getInstance().isRuimCsim(svlteSlot)
                            && SvlteRatController.getEngineerMode()
                                != SvlteRatController.ENGINEER_MODE_LTE) {
                        phone.setRadioPower(power);
                    } else {
                        log("updatePhoneRadioPower: slot0 not CDMA card, no need turn on radio!");
                    }
                } else {
                    phone.setRadioPower(power);
                }
            }
        } else {
            phone.setRadioPower(power);
        }*/
    }

    //FIXME: To refine Test SIM flow, 2015/06/25
    public boolean isSvlteTestSimAllowPowerOn(int phoneId) {
        if (UiccController.isSvlteTestSimMode()) {
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            int[] cardType = new int[phoneCount];
            int type = 0;
            for (int i = 0; i < phoneCount; i++) {
                cardType[i] = getFullCardType(i);
                log("SvlteTestSimMode,  cardType[" + i + "]=" + cardType[i]);
            }
            if (phoneId == 0 || phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
                type = cardType[0];
            } else if (phoneId == 1 || phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
                type = cardType[1];
            }
            PhoneBase phoneBase =
                    (PhoneBase) ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getPhoneById(phoneId);
            if (phoneBase != null) {
                if ((phoneBase.getPhoneName()).equals("CDMA") && isNonCdma(type)) {
                    log("SvlteTestSimMode: skip CDMA power on phone: " + phoneId);
                    return false;
                }
            }
        }
        log("SvlteTestSimMode: power on phone: " + phoneId);
        return true;
    }

    private void updateMsimModeRadioPower(boolean power, int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        int svlteSlot = SvlteModeController.getCdmaSocketSlotId();
        log("updateMsimModeRadioPower: power=" + power + " phoneId=" + phoneId +  " svlteSlot=" + svlteSlot);
        if (svlteSlot == phoneId) {
            if (power == false) {
                ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().setRadioPower(power);
            } else {
                if (phoneId == 0 &&
                        isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_1)) {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().setRadioPower(power);
                } else if (phoneId == 1 &&
                        isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID_2)) {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().setRadioPower(power);
                }
            }
        }

        // power default phone
        phone.setRadioPower(power);

        /*
        // FIXME: To figure out a better way to get SVLTE phone slot
        if (0 == phoneId) {
            // Power PS channel if needed
            if (SvlteUiccUtils.getInstance().isUsimWithCsim(phoneId)) {
                // FIXME TO uncomment after SvlteRatController is merged.
                //if ((power && isAllowRadioPowerOn(SubscriptionManager.LTE_DC_PHONE_ID))
                           || power == false) {
                    log("updateMsimModeRadioPower: PS channel power: " + power);
                    PhoneFactory.getPhone(SubscriptionManager.LTE_DC_PHONE_ID).setRadioPower(power);
                //} else {
                //    log("updateMsimModeRadioPower not allow power on : +phoneId: " + phoneId);
                //}
            }
            // Power CS channel
            phone.setRadioPower(power);
        } else {
            phone.setRadioPower(power);
        }*/
    }
    // @}

    private void setSimInsertedStatus(int phoneId) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1
                    || phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
                return;
            }
        }

        String iccId = readIccIdUsingPhoneId(phoneId);
        if (STRING_NO_SIM_INSERTED.equals(iccId)) {
            mSimInsertedStatus[phoneId] = NO_SIM_INSERTED;
        } else {
            mSimInsertedStatus[phoneId] = SIM_INSERTED;
        }
    }

    private boolean isIccIdReady(int phoneId) {
        String iccId = readIccIdUsingPhoneId(phoneId);
        boolean ret = ICC_READ_NOT_READY;
        if (iccId == null || "".equals(iccId)) {
            log("ICC read not ready for phone:" + phoneId);
            ret = ICC_READ_NOT_READY;
        } else {
            log("ICC read ready, iccid[" + phoneId + "]: " + iccId);
            ret = ICC_READ_READY;
        }
        return ret;
    }

    private String readIccIdUsingPhoneId(int phoneId) {
        String ret = null;
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            ret = SystemProperties.get(PROPERTY_ICCID_SIM[phoneId]);
            log("Common ICCID for phone " + phoneId + " is " + ret);
            return ret;
        }

        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        int[] cardType = new int[phoneCount];
        if (UiccController.isSvlteTestSimMode()) {
            for (int i = 0; i < phoneCount; i++) {
                cardType[i] = getFullCardType(i);
                log("SvlteTestSimMode,  cardType[" + i + "]=" + cardType[i]);
            }
        } else {
        cardType = UiccController.getInstance().getC2KWPCardType();
        }
        log("solution2 readIccIdUsingPhoneId: phoneId=" + phoneId + " cardType=" + cardType);
        int radioTechMode;
        // solution2
        if (phoneId == 0 || phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
            // slot1
            radioTechMode = SvlteModeController.getRadioTechnologyMode(0);
            log("readIccIdUsingPhoneId: slot1 radioTechMode=" + radioTechMode);
            if (radioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE) {
                // update iccid property if CT 3G sim
                String iccidCommon = SystemProperties.get(PROPERTY_ICCID_SIM[0]);
                if ((iccidCommon == null || iccidCommon.equals("") || iccidCommon.equals("N/A")) &&
                        isCdmaOnly(cardType[0])) {
                    String iccidC2K = SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                    if ((SvlteModeController.getActiveSvlteModeSlotId() == 0) && iccidC2K != null && !iccidC2K.equals("")) {
                        SystemProperties.set(PROPERTY_ICCID_SIM[0], iccidC2K);
                        log("readIccIdUsingPhoneId: update iccid[0] use iccidC2K:" + iccidC2K);
                    } else {
                        log("readIccIdUsingPhoneId: CDMA only iccid not ready");
                    }
                }

                if (phoneId == 0) {
                    //FIXME: To refine Test SIM flow, 2015/06/25
                    ret = UiccController.isSvlteTestSimMode()
                            ? SystemProperties.get(PROPERTY_ICCID_SIM[0])
                            : SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                } else {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM[0]);
                }
            } else if (radioTechMode == SvlteModeController.RADIO_TECH_MODE_CSFB) {
                if (phoneId == 0 || isGsmCard(getFullCardType(phoneId))) {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM[0]);
                } else {
                    //FIXME: To refine Test SIM flow, 2015/06/25
                    ret = UiccController.isSvlteTestSimMode()
                            ? SystemProperties.get(PROPERTY_ICCID_SIM[0])
                            : SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                }
            } else {
                log("readIccIdUsingPhoneId: invalid radioTechMode=" + radioTechMode);
            }
        } else if (phoneId == 1 || phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
            // slot2
            radioTechMode = SvlteModeController.getRadioTechnologyMode(1);
            log("readIccIdUsingPhoneId: slot2 radioTechMode=" + radioTechMode);
            if (radioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE) {
                // update iccid property if CT 3G sim
                String iccidCommon = SystemProperties.get(PROPERTY_ICCID_SIM[1]);
                if ((iccidCommon == null || iccidCommon.equals("") || iccidCommon.equals("N/A")) &&
                        isCdmaOnly(cardType[1])) {
                    String iccidC2K = SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                    if ((SvlteModeController.getActiveSvlteModeSlotId() == 1) && iccidC2K != null && !iccidC2K.equals("")) {
                        SystemProperties.set(PROPERTY_ICCID_SIM[1], iccidC2K);
                        log("readIccIdUsingPhoneId: update iccid[1] use iccidC2K:" + iccidC2K);
                    } else {
                        log("readIccIdUsingPhoneId: CDMA only iccid not ready");
                    }
                }

                if (phoneId == 1) {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                } else {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM[1]);
                }
            } else if (radioTechMode == SvlteModeController.RADIO_TECH_MODE_CSFB) {
                 if (phoneId == 1 || isGsmCard(getFullCardType(phoneId ))) {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM[1]);
                } else {
                    ret = SystemProperties.get(PROPERTY_ICCID_SIM_C2K);
                }
            } else {
                log("readIccIdUsingPhoneId: invalid radioTechMode=" + radioTechMode);
            }
        } else {
            log("readIccIdUsingPhoneId: invalid phoneId=" + phoneId);
        }
        log("ICCID for phone " + phoneId + " is " + ret);
        return ret;
    }
    
    private boolean containsGsm(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_SIM) > 0 ||
            (cardType & UiccController.CARD_TYPE_USIM) > 0) {
            return true;
        }
        return false;
    }
    
    private boolean isGsmCard(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_RUIM) == 0 &&
            (cardType & UiccController.CARD_TYPE_CSIM) == 0 &&
            containsGsm(cardType)) {
            return true;
        }
        return false;
    }

    private boolean checkForCTACase() {
        boolean isCTACase = true;
        log("Check For CTA case!");
        if (mAirplaneMode == AIRPLANE_MODE_OFF && mWifiOnlyMode != WIFI_ONLY_MODE_ON) {
            for (int i = 0; i < mPhoneCount; i++) {
                log("Check For CTA case: mSimInsertedStatus[" + i + "]:"  + mSimInsertedStatus[i]);
                if (mSimInsertedStatus[i] == SIM_INSERTED || mSimInsertedStatus[i] == SIM_NOT_INITIALIZED) {
                    isCTACase = false;
                }
            }
        } else {
            isCTACase = false;
        }

        if ((false == isCTACase) && (false == mIsEccCall)) {
            turnOffCTARadioIfNecessary();
        }
        log("CTA case: " + isCTACase);
        return isCTACase;
    }

    /*
     * We need to turn off Phone's radio if no SIM inserted (radio on because CTA) after we leave CTA case
     */
    private void turnOffCTARadioIfNecessary() {
        for (int i = 0; i < mPhoneCount; i++) {
            if (mSimInsertedStatus[i] == NO_SIM_INSERTED) {
                if (isModemPowerOff(i)) {
                    log("modem off, not to handle CTA");
                    return;
                } else {
                    log("turn off phone " + i + " radio because we are no longer in CTA mode");
                    PhoneFactory.getPhone(i).setRadioPower(RADIO_POWER_OFF);
                }
            }
        }
    }

    /*
     * Refresh MSIM Settings only when:
     * We auto turn off a SIM card once manually turned off
     */
    private void refreshSimSetting(boolean radioPower, int phoneId) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (radioPower) {
                if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
                    phoneId = 0;
                } else if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
                    phoneId = 1;
                }
            } else {
                // Don't update OFF MSIM_MODE_SETTING except Active phone
                if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1 ||
                        phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
                    log("refreshSimSetting phoneId=" + phoneId + ", not update SimSetting!");
                    return;
                }
            }
        }

        int simMode = Settings.System.getInt(mContext.getContentResolver(),
                          Settings.System.MSIM_MODE_SETTING, mBitmapForPhoneCount);
        int oldMode = simMode;

        if (radioPower == RADIO_POWER_OFF) {
            simMode &= ~(MODE_PHONE1_ONLY << phoneId);
        } else {
            simMode |= (MODE_PHONE1_ONLY << phoneId);
        }

        if (simMode != oldMode) {
            log("Refresh MSIM mode setting to " + simMode + " from " + oldMode);
            Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, simMode);
        }
    }

    /*
     * wait ICCID ready when force set radio power
     */
    private class ForceSetRadioPowerRunnable implements Runnable {
        boolean mRetryPower;
        int mRetryPhoneId;
        public  ForceSetRadioPowerRunnable(boolean power, int phoneId) {
            mRetryPower = power;
            mRetryPhoneId = phoneId;
        }
        @Override
        public void run() {
            forceSetRadioPower(mRetryPower, mRetryPhoneId);
        }
    }
    
    public static boolean isMtkIndiaC2k5MSupport() {
        boolean isSupport = C2K_5M.equalsIgnoreCase(
                SystemProperties.get("ro.mtk.c2k.om.mode")) ? true : false;
        boolean isIndia = IS_INDIAN.equalsIgnoreCase(
                SystemProperties.get("ro.mtk_c2k_om_nw_sel_type")) ? true : false;
    
        log("isMtkC2k5M(): " + isSupport);
        log("isIndia(): " + isIndia);
        return isSupport&&isIndia;
    }

    /**
     * force turn on radio and remove iccid for preference to prevent being turned off again
     * 1. For ECC call
     */
    public void forceSetRadioPower(boolean power, int phoneId) {
        log("force set radio power for phone" + phoneId + " ,power: " + power);

        if (isFlightModePowerOffModemEnabled() && mAirplaneMode == AIRPLANE_MODE_ON) {
            log("Force Set Radio Power under airplane mode, ignore");
            return;
        }

        if (bIsInIpoShutdown) {
            log("Force Set Radio Power under ipo shutdown, ignore");
            return;
        }

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
            !SystemProperties.get(PROPERTY_CONFIG_EMDSTATUS_SEND).equals("1")) {
            ForceSetRadioPowerRunnable forceSetRadioPowerRunnable =
                new ForceSetRadioPowerRunnable(power, phoneId);
            postDelayed(forceSetRadioPowerRunnable,
                INITIAL_RETRY_INTERVAL_MSEC);
            return;
        }

        /**
        * We want iccid ready berfore we check if SIM is once manually turned-offedd
        * So we check ICCID repeatedly every 300 ms
        */
        if (!isIccIdReady(phoneId)) {
            log("force set radio power, read iccid not ready, wait for" +
                INITIAL_RETRY_INTERVAL_MSEC + "ms");
            ForceSetRadioPowerRunnable forceSetRadioPowerRunnable =
                new ForceSetRadioPowerRunnable(power, phoneId);
            postDelayed(forceSetRadioPowerRunnable,
                INITIAL_RETRY_INTERVAL_MSEC);
            return;
        }

        boolean radioPower = power;
        refreshIccIdPreference(radioPower, readIccIdUsingPhoneId(phoneId));
        PhoneFactory.getPhone(phoneId).setRadioPower(power);
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                SvlteUiccUtils.getInstance().isUsimWithCsim(phoneId) &&
                phoneId == SvlteModeController.getCdmaSocketSlotId() && !isMtkIndiaC2k5MSupport()) {
            log("forceSetRadioPower: CT 4G card need turn LTE radio: " + power);
            ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getLtePhone().setRadioPower(power);
        }
    }

    /**
     * Force turn on radio and remove iccid for preference to prevent being turned off again
     * For CT ECC call
     * @param power for on/off radio power
     * @param phoneId for phone ID
     * @param isEccOn for if ECC call on-going
     */
    public void forceSetRadioPower(boolean power, int phoneId, boolean isEccOn) {
        log("force set radio power isEccOn: " + isEccOn);
        mIsEccCall = isEccOn;
        forceSetRadioPower(power, phoneId);
    }

    /*
     * wait ICCID ready when SIM mode change
     */
    private class SimModeChangeRunnable implements Runnable {
        boolean mPower;
        int mPhoneId;
        public SimModeChangeRunnable(boolean power, int phoneId) {
            mPower = power;
            mPhoneId = phoneId;
        }
        @Override
        public void run() {
            notifySimModeChange(mPower, mPhoneId);
        }
    }

    /**
     * Refresh ICCID preference due to toggling on SIM management except for below cases:
     * 1. SIM Mode Feature not defined
     * 2. Under Airplane Mode (PhoneGlobals will call GSMPhone.setRadioPower after receving airplane mode change)
     * @param power power on -> remove preference
     *               power off -> add to preference
     */
    public void notifySimModeChange(boolean power, int phoneId) {
        log("SIM mode changed, power: " + power + ", phoneId" + phoneId);
        if (!isMSimModeSupport() || mAirplaneMode == AIRPLANE_MODE_ON) {
            log("Airplane mode on or MSIM Mode option is closed, do nothing!");
            return;
        } else {
            if (!isIccIdReady(phoneId)) {
                log("sim mode read iccid not ready, wait for "
                    + INITIAL_RETRY_INTERVAL_MSEC + "ms");
                SimModeChangeRunnable notifySimModeChangeRunnable
                    = new SimModeChangeRunnable(power, phoneId);
                postDelayed(notifySimModeChangeRunnable, INITIAL_RETRY_INTERVAL_MSEC);
                return;
            }
            //once ICCIDs are ready, then set the radio power
            if (STRING_NO_SIM_INSERTED.equals(readIccIdUsingPhoneId(phoneId))) {
                power = RADIO_POWER_OFF;
                log("phoneId " + phoneId + " sim not insert, set  power  to " + power);
            }
            refreshIccIdPreference(power, readIccIdUsingPhoneId(phoneId));
            log("Set Radio Power due to SIM mode change, power: " + power + ", phoneId: " + phoneId);

            if (power && !isSvlteTestSimAllowPowerOn(phoneId)) {
                log("notifySimModeChange: SvlteTestSimMode bypass power on phone:" + phoneId);
                return;
            }

            PhoneFactory.getPhone(phoneId).setRadioPower(power);
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                updateMsimModeRadioPower(power, phoneId);
            }
        }
    }

    /*
     * wait ICCID ready when MSIM modem change
     * @Deprecated
     */
    private class MSimModeChangeRunnable implements Runnable {
        int mRetryMode;
        public  MSimModeChangeRunnable(int mode) {
            mRetryMode = mode;
        }
        @Override
        public void run() {
            notifyMSimModeChange(mRetryMode);
        }
    }

    /**
     * Refresh ICCID preference due to toggling on SIM management except for below cases:
     * 1. SIM Mode Feature not defined
     * 2. Under Airplane Mode (PhoneGlobals will call GSMPhone.setRadioPower after receving airplane mode change)
     * @param power power on -> remove preference
     *               power off -> add to preference
     * @internal
     * @Deprecated
     */
    public void notifyMSimModeChange(int mode) {
        log("MSIM mode changed, mode: " + mode);
        if (mode == -1) {
            log("Invalid mode, MSIM_MODE intent has no extra value");
            return;
        }
        if (!isMSimModeSupport() || mAirplaneMode == AIRPLANE_MODE_ON) {
            log("Airplane mode on or MSIM Mode option is closed, do nothing!");
            return;
        } else {
            //all ICCCIDs need be ready berfore set radio power
            int phoneId = 0;
            boolean iccIdReady = true;
            for (phoneId = 0; phoneId < mPhoneCount; phoneId++) {
                if (!isIccIdReady(phoneId)) {
                    iccIdReady = false;
                    break;
                }
            }
            if (!iccIdReady) {
                log("msim mode read iccid not ready, wait for "
                    + INITIAL_RETRY_INTERVAL_MSEC + "ms");
                MSimModeChangeRunnable notifyMSimModeChangeRunnable
                    = new MSimModeChangeRunnable(mode);
                postDelayed(notifyMSimModeChangeRunnable, INITIAL_RETRY_INTERVAL_MSEC);
                return;
            }
            //once ICCIDs are ready, then set the radio power
            for (phoneId = 0; phoneId < mPhoneCount; phoneId++) {
                boolean singlePhonePower = ((mode & (MODE_PHONE1_ONLY << phoneId)) == 0) ? RADIO_POWER_OFF : RADIO_POWER_ON;
                if (STRING_NO_SIM_INSERTED.equals(readIccIdUsingPhoneId(phoneId))) {
                    singlePhonePower = RADIO_POWER_OFF;
                    log("phoneId " + phoneId + " sim not insert, set  power  to " + singlePhonePower);
                }
                refreshIccIdPreference(singlePhonePower, readIccIdUsingPhoneId(phoneId));
                log("Set Radio Power due to MSIM mode change, power: " + singlePhonePower
                        + ", phoneId: " + phoneId);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    updateMsimModeRadioPower(singlePhonePower, phoneId);
                } else {
                    PhoneFactory.getPhone(phoneId).setRadioPower(singlePhonePower);
                }
            }
        }
    }

    private void refreshIccIdPreference(boolean power, String iccid) {
        log("refresh iccid preference");
        SharedPreferences.Editor editor = mIccidPreference.edit();
        if (power == RADIO_POWER_OFF && !STRING_NO_SIM_INSERTED.equals(iccid)) {
            putIccIdToPreference(editor, iccid);
        } else {
            removeIccIdFromPreference(editor, iccid);
        }
        editor.commit();
    }

    private void putIccIdToPreference(SharedPreferences.Editor editor, String iccid) {
        if (iccid != null) {
            log("Add radio off SIM: " + iccid);
            editor.putInt(iccid, 0);
         }
    }

    private void removeIccIdFromPreference(SharedPreferences.Editor editor, String iccid) {
        if (iccid != null) {
            log("Remove radio off SIM: " + iccid);
            editor.remove(iccid);
        }
    }

    /*
     * Some Request or AT command must made before EFUN
     * 1. Prevent waiting for response
     * 2. Send commands as the same channel as EFUN or CFUN
     */
    public static void sendRequestBeforeSetRadioPower(boolean power, int phoneId) {
        log("Send request before EFUN, power:" + power + " phoneId:" + phoneId);

        notifyRadioPowerChange(power, phoneId);
    }

    /**
     * MTK Power on feature
     * 1. Radio off a card from SIM Management
     * 2. Flight power off modem
     * @internal
     */
    public static boolean isPowerOnFeatureAllClosed() {
        boolean ret = true;
        if (isFlightModePowerOffModemEnabled()) {
            ret = false;
        } else if (isRadioOffPowerOffModemEnabled()) {
            ret = false;
        } else if (isMSimModeSupport()) {
            ret = false;
        }
        return ret;
    }

    public static boolean isRadioOffPowerOffModemEnabled() {
        return SystemProperties.get("ro.mtk_radiooff_power_off_md").equals("1");
    }

    public static boolean isFlightModePowerOffModemEnabled() {
        if (SystemProperties.get("ril.testmode").equals("1")) {
            return SystemProperties.get("ril.test.poweroffmd").equals("1");
        } else {
            return SystemProperties.get("ro.mtk_flight_mode_power_off_md").equals("1") ||
                   SystemProperties.get("gsm.sim.ril.testsim").equals("1") ||
                   SystemProperties.get("gsm.sim.ril.testsim.2").equals("1") ||
                   SystemProperties.get("gsm.sim.ril.testsim.3").equals("1") ||
                   SystemProperties.get("gsm.sim.ril.testsim.4").equals("1");
        }
    }

    /**
     *  Check if modem is already power off.
     **/
    public static boolean isModemPowerOff(int phoneId) {
        boolean powerOff = false;
        TelephonyManager.MultiSimVariants config
            = TelephonyManager.getDefault().getMultiSimConfiguration();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            // SVLTE project:
            // getCdmaSocketSlotId = 0
            //     MD3 => ril.ipo.radiooff
            //     MD1 => ril.ipo.radiooff.2
            // getCdmaSocketSlotId = 1
            //     MD1 => ril.ipo.radiooff
            //     MD3 => ril.ipo.radiooff.2

            int cdmaSlot = SvlteModeController.getCdmaSocketSlotId();
            log("isModemPowerOff: cdmaSlot=" + cdmaSlot + " ,phoneId=" + phoneId);
            if (((SvltePhoneProxy) PhoneFactory.getPhone(phoneId)).getPhoneById(phoneId).getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                log("isModemPowerOff: C2K phone");
                if (cdmaSlot == 0) {
                    phoneId = 0;
                } else {
                    phoneId = 1;
                }
            } else {
                log("isModemPowerOff: GSM phone");
                if (cdmaSlot == 0) {
                    phoneId = 1;
                } else {
                    phoneId = 0;
                }
            }
        }
        switch(config) {
            case DSDS:
                powerOff = !SystemProperties.get("ril.ipo.radiooff").equals("0");
                break;
            case DSDA:
                switch (phoneId) {
                    case 0: //phone 1
                        powerOff = !SystemProperties.get("ril.ipo.radiooff").equals("0");
                        break;
                    case 1: //phone 2
                        powerOff = !SystemProperties.get("ril.ipo.radiooff.2").equals("0");
                        break;
                    default:
                        powerOff = true;
                        break;
                }
                break;
            case TSTS:
                //TODO: check 3 SIM case
                powerOff = !SystemProperties.get("ril.ipo.radiooff").equals("0");
                break;
            default:
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    // SVLTE single SIM but need check two modem
                    if (phoneId == 0) {
                        powerOff = !SystemProperties.get("ril.ipo.radiooff").equals("0");
                    } else if (phoneId == 1) {
                        powerOff = !SystemProperties.get("ril.ipo.radiooff.2").equals("0");
                    }
                } else {
                    powerOff = !SystemProperties.get("ril.ipo.radiooff").equals("0");
                }
                break;
        }
        return powerOff;
    }

    public static boolean isMSimModeSupport() {
        // TODO: adds logic
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        } else {
            return true;
        }
    }

    private void setAirplaneMode(boolean enabled) {
        log("set mAirplaneMode as:" + enabled);
        mAirplaneMode = enabled;
    }

    private boolean getAirplaneMode() {
        return mAirplaneMode;
    }


    private void resetSimInsertedStatus(int phoneId) {
        log("reset Sim InsertedStatus for Phone:" + phoneId);
        mSimInsertedStatus[phoneId] = SIM_NOT_INITIALIZED;
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        int[] ints;
        String[] strings;
        Message message;
        int phoneIdForMsg = getCiIndex(msg);

        log("handleMessage msg.what: " + eventIdtoString(msg.what));
        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE:
                for (int i = 0; i < mPhoneCount; i++) {
                    if(!mCi[i].getRadioState().isAvailable()) {
                        log("phone " + i + "is not available, so return");
                        return;
                    }
                }
                if (bIsQueueIpoShutdown) {
                    log("bIsQueueIpoShutdown is true");
                    doIpoShutDown();
                    bIsQueueIpoShutdown = false;
                }
                break;
            case EVENT_VIRTUAL_SIM_ON:
                forceSetRadioPower(RADIO_POWER_ON, phoneIdForMsg);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private String eventIdtoString(int what) {
        String str = null;
        switch (what) {
            case EVENT_RADIO_AVAILABLE:
                str = "EVENT_RADIO_AVAILABLE";
                break;
            case EVENT_VIRTUAL_SIM_ON:
                str = "EVENT_VIRTUAL_SIM_ON";
                break;
            default:
                break;
        }
        return str;
    }

    private int getCiIndex(Message msg) {
        AsyncResult ar;
        Integer index = new Integer(PhoneConstants.DEFAULT_CARD_INDEX);

        /*
         * The events can be come in two ways. By explicitly sending it using
         * sendMessage, in this case the user object passed is msg.obj and from
         * the CommandsInterface, in this case the user object is msg.obj.userObj
         */
        if (msg != null) {
            if (msg.obj != null && msg.obj instanceof Integer) {
                index = (Integer)msg.obj;
            } else if(msg.obj != null && msg.obj instanceof AsyncResult) {
                ar = (AsyncResult)msg.obj;
                if (ar.userObj != null && ar.userObj instanceof Integer) {
                    index = (Integer)ar.userObj;
                }
            }
        }
        return index.intValue();
    }

     public static synchronized void registerForRadioPowerChange(String name, IRadioPower iRadioPower) {
        if (name == null) {
            name = NO_NAME;
        }
        log(name + " registerForRadioPowerChange");
        mNotifyRadioPowerChange.put(iRadioPower, name);
    }

    public static synchronized void unregisterForRadioPowerChange(IRadioPower iRadioPower) {
        log(mNotifyRadioPowerChange.get(iRadioPower) + " unregisterForRadioPowerChange");
        mNotifyRadioPowerChange.remove(iRadioPower);
    }

    private static synchronized void notifyRadioPowerChange(boolean power, int phoneId) {
        for (Entry<IRadioPower, String> e : mNotifyRadioPowerChange.entrySet()) {
            log("notifyRadioPowerChange: user:" + e.getValue());
            IRadioPower iRadioPower = e.getKey();
            iRadioPower.notifyRadioPowerChange(power, phoneId);
        }
    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, "[RadioManager] " + s);
    }

    private int getFullCardType(int slotId) {
        if (slotId < 0 || slotId >= TelephonyManager.getDefault().getPhoneCount()) {
            Rlog.e(LOG_TAG, "getFullCardType invalid slotId:" + slotId);
            return UiccController.CARD_TYPE_NONE;
        }
        String cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[slotId]);
        Rlog.d(LOG_TAG, "getFullCardType=" + cardType);
        String appType[] = cardType.split(",");
        int fullType = UiccController.CARD_TYPE_NONE;
        for (int i = 0; i < appType.length; i++) {
            if ("USIM".equals(appType[i])) {
                fullType = fullType | UiccController.CARD_TYPE_USIM;
            } else if ("SIM".equals(appType[i])) {
                fullType = fullType | UiccController.CARD_TYPE_SIM;
            } else if ("CSIM".equals(appType[i])) {
                fullType = fullType | UiccController.CARD_TYPE_CSIM;
            } else if ("RUIM".equals(appType[i])) {
                fullType = fullType | UiccController.CARD_TYPE_RUIM;
            }
        }
        Rlog.d(LOG_TAG, "fullType=" + fullType);
        return fullType;
    }

    /**
     * Check whether poweron is allowed.
     * @param phoneId
     * @return true or false
     */
    private boolean isAllowRadioPowerOn(int phoneId) {
        return SvlteUtils.getSvltePhoneProxy(phoneId)
                       .getSvlteRatController().allowRadioPowerOn(phoneId);
    }

    /**
     * Check whether allow airplane mode change.
     * @return true if allow.
     */
    public boolean isAllowAirplaneModeChange() {
        return mAirplaneRequestHandler.allowSwitching();
    }

    /**
     * Get AirplaneRequestHandler instance.
     * @return AirplaneRequestHandler
     */
    public AirplaneRequestHandler getAirplaneRequestHandler() {
        return mAirplaneRequestHandler;
    }

    /**
     * Set Whether force allow airplane mode change.
     * @return true or false
     */
    public void forceAllowAirplaneModeChange(boolean forceSwitch) {
        mAirplaneRequestHandler.setForceSwitch(forceSwitch);
    }

    private boolean isCdmaOnly(int cardType) {
        Rlog.d(LOG_TAG, "isCdmaOnly, cardType=" + cardType);
        if (cardType == UiccController.CARD_TYPE_RUIM
            || cardType == UiccController.CARD_TYPE_CSIM
            || cardType == (UiccController.CARD_TYPE_RUIM | UiccController.CARD_TYPE_CSIM)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isNonCdma(int cardType) {
        Rlog.d(LOG_TAG, "isNonCdma, cardType=" + cardType);
        if ((cardType & UiccController.CARD_TYPE_RUIM) == 0
            && (cardType & UiccController.CARD_TYPE_CSIM) == 0) {
            return true;
        } else {
            return false;
        }
    }
}
