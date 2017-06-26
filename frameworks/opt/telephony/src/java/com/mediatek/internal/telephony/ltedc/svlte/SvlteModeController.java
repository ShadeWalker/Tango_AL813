/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.internal.telephony.ltedc.svlte;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * For SVLTE, this is the controller used to switch SVLTE/CSFB based on card type
 *
 * {@hide}
 */
public class SvlteModeController extends Handler {
    private static final boolean DEBUG = true;
    private static final String LOG_TAG = "SvlteModeController";

    /** events id definition */
    protected static final int EVENT_C2K_WP_CARD_TYPE_READY = 1;
    protected static final int EVENT_RIL_CONNECTED = 2;

    private Context mContext;
    private String mOperatorSpec;
    private String mOP09Spec;
    // Radio technology mode definition.
    public static final int RADIO_TECH_MODE_UNKNOWN = 1;
    public static final int RADIO_TECH_MODE_CSFB    = 2;
    public static final int RADIO_TECH_MODE_SVLTE   = 3;

    private static final String OPERATOR_OM = "OM";
    private static final String OPERATOR_OP09 = "OP09";
    private static final String SPEC_OP09_A = "SEGDEFAULT";
    // SVLTE Slot
    public static final int CSFB_ON_SLOT = -1;
    public static final int SVLTE_ON_SLOT_0 = 0;
    public static final int SVLTE_ON_SLOT_1 = 1;

    // Used for maintain the currently Card Modes.
    private int[] mCardTypes = new int[TelephonyManager.getDefault().getPhoneCount()];
    // Used to save the correct RADIO TECH.
    public static final String SVLTE_PROP = "persist.radio.svlte_slot";
    // Used to save the slot id which connected to c2k ril socket.
    public static final String CDMA_PROP = "persist.radio.cdma_slot";
    // Default radio technology mode for SIM 1 and SIM 2.
    public static final String SVLTE_PROP_DEFAULT_VALUE = String.valueOf(RADIO_TECH_MODE_SVLTE) +
            "," + String.valueOf(RADIO_TECH_MODE_CSFB);
    // Used for maintain the Card Modes after calculation.
    private static int[] sCardModes = initCardModes();
    private int[] mOldCardModes;
    private ArrayList<Integer> mSwitchQueue = new ArrayList<Integer>();

    private static final Object mLock = new Object();
    private static SvlteModeController sInstance;
    private AtomicBoolean mInSwitching = new AtomicBoolean(false);
    private int[] mPendingCardTypes;
    private boolean mWaitingRilSocketConnect;
    private int mSlotIdWaitConnect = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private int mSwitchingSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private boolean mSvlteModeOn;
    private boolean mConfgiGsmDone = false;
    private boolean mConfgiC2kDone = false;
    private boolean [] mNeedReSwitch = new boolean[TelephonyManager.getDefault().getPhoneCount()];
    private static final int ACTION_FORM_MODE_CONTROLLER = 2;
    private static final int INITIAL_RETRY_INTERVAL_MSEC = 200;
    private static final String  PROPERTY_NET_CDMA_MDMSTAT = "net.cdma.mdmstat";

    public static final int TELEPHONY_MODE_UNKNOWN = -1;
    public static final int TELEPHONY_MODE_LC_G = 0;
    public static final int TELEPHONY_MODE_LWTG_C = 1;
    public static final int TELEPHONY_MODE_LWTG_G = 2;
    public static final int TELEPHONY_MODE_G_LC = 3;
    public static final int TELEPHONY_MODE_C_LWTG = 4;
    public static final int TELEPHONY_MODE_G_LWTG = 5;
    public static final int TELEPHONY_MODE_LC_SINGLE = 6;
    public static final int TELEPHONY_MODE_LWTG_SINGLE = 7;
    
    public static final String MTK_C2K_SLOT2_SUPPORT = "ro.mtk.c2k.slot2.support";
    public static final String MTK_C2K_6M_SUPPORT = "ro.ct6m_support";


    /// M: The slot is need to switch mode to sim or ruim for c+c.
    private int mSlotBeSwitchCardType = -1;

    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };
    
    private int mLastProtocol = -1;

   /**
    * @return the single instance of SvlteModeController.
    */
    public static SvlteModeController getInstance() {
        synchronized (mLock) {
            if (sInstance == null) {
                throw new RuntimeException(
                        "SvlteModeController.getInstance can't be called before make()");
            }
            return sInstance;
        }
    }

    /**
     * Create the SvlteModeController.
     * @param context The Context to use.
     * @return The instance of SvlteModeController
     */
    public static SvlteModeController make(Context context) {
        synchronized (mLock) {
            if (sInstance != null) {
                throw new RuntimeException(
                        "SvlteRatController.make() should only be called once");
            }
            sInstance = new SvlteModeController(context);
            return sInstance;
        }
    }

    private SvlteModeController(Context context) {
        Rlog.d(LOG_TAG, "SvlteModeController constructor");

        mContext = context;
        mOperatorSpec = SystemProperties.get("ro.operator.optr", OPERATOR_OM);
        mOP09Spec = SystemProperties.get("ro.operator.seg");
        logicLog("Operator Spec:" + mOperatorSpec + ", mOP09Spec:" + mOP09Spec);
        UiccController.getInstance().registerForC2KWPCardTypeReady(this,
                EVENT_C2K_WP_CARD_TYPE_READY, null);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SvlteRatController.INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private static int[] initCardModes() {
        int[] cardModes = new int[TelephonyManager.getDefault().getPhoneCount()];
        for (int i = 0; i < cardModes.length; i++) {
            cardModes[i] = Integer.parseInt(
                SystemProperties.get(SVLTE_PROP, "3,2,2,2").split(",")[i]);
        }
        return cardModes;
    }

    public void dispose() {
        UiccController.getInstance().unregisterForC2KWPCardTypeReady(this);
        mContext.unregisterReceiver(mReceiver);
        mSwitchQueue.clear();
        mInSwitching.set(false);
        mPendingCardTypes = null;
        mWaitingRilSocketConnect = false;
        mSwitchingSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        unregisterForRilConnected();
        mSlotBeSwitchCardType = -1;
        logicLog("dispose!");
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        switch (msg.what) {
            case EVENT_C2K_WP_CARD_TYPE_READY:
                onC2kCardTypeReady();
                break;
            case EVENT_RIL_CONNECTED:
                if (ar.exception == null) {
                    mWaitingRilSocketConnect = (int) ar.result > -1 ? false : true;
                    logicLog("EVENT_RIL_CONNECTED, mWaitingRilSocketConnect ="
                        + mWaitingRilSocketConnect);
                } else {
                    logicLog("Unexpected exception on EVENT_RIL_CONNECTED");
                }
                if (!mWaitingRilSocketConnect && SvlteUtils.isValidateSlotId(mSlotIdWaitConnect)) {
                    setCdmaSocketSlotId(mSlotIdWaitConnect);
                    switchRadioTechnology(mSlotIdWaitConnect);
                    unregisterForRilConnected();
                }
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void onC2kCardTypeReady() {
        mConfgiGsmDone = false;
        mConfgiC2kDone = false;
        if (mInSwitching.get()) {
            logicLog("[onC2kCardTypeReady] Switching now, pended");
            mPendingCardTypes = Arrays.copyOf(UiccController.getInstance().getC2KWPCardType(),
                TelephonyManager.getDefault().getPhoneCount());
        } else {
            logicLog("[onC2kCardTypeReady] Start switch");
            mCardTypes = Arrays.copyOf(UiccController.getInstance().getC2KWPCardType(),
                TelephonyManager.getDefault().getPhoneCount());
            mOldCardModes = sCardModes;
            if (!isSwitchInAirplaneMode()) {
                sCardModes = calculateCardMode();
                setupSvlteSystemProp(sCardModes);
            }
            doSwitchRadioTech();
        }
    }

    private void doSwitchRadioTech() {
        /// M: For C+C. just switchCardType and return.
        if (mSlotBeSwitchCardType > -1) {
            logicLog("doSwitchRadioTech, Just return. AS slot: "
                + mSlotBeSwitchCardType + " switchCardType to sim.");
            UiccController.getInstance().switchCardType(mSlotBeSwitchCardType, 0);
            mSlotBeSwitchCardType = -1;
            sCardModes = mOldCardModes;
            return;
        }
        int firstSwitchSlot = PhoneConstants.SIM_ID_1;
        startSwitchMode();
        mSvlteModeOn = false;
        for (int i = 0; i < sCardModes.length; i++) {
            mSwitchQueue.add(new Integer(i));
            if (mOldCardModes[i] == RADIO_TECH_MODE_CSFB
                    && sCardModes[i] == RADIO_TECH_MODE_SVLTE
                    && i != getCdmaSocketSlotId()) {
                mWaitingRilSocketConnect = true;
                mSlotIdWaitConnect = i;
            }
            if (i == getCdmaSocketSlotId() && sCardModes[i] == RADIO_TECH_MODE_CSFB) {
                firstSwitchSlot = i;
            }
            if (sCardModes[i] == RADIO_TECH_MODE_SVLTE) {
                mSvlteModeOn = true;
            }
        }

        if (mSwitchQueue.size() > 0) {
            logicLog("[doSwitchRadioTech] mSwitchQueue size : " + mSwitchQueue.size());
            if (mSwitchQueue.size() == 1) {
                int slotId = mSwitchQueue.get(0);
                switchRadioTechnology(slotId);
                mSwitchQueue.clear();
            } else if (mSwitchQueue.size() == 2) {
                logicLog("[doSwitchRadioTech] firstSwitchSlot : " + firstSwitchSlot);
                switchRadioTechnology(firstSwitchSlot);
                mSwitchQueue.remove((Integer) firstSwitchSlot);
                logicLog("[doSwitchRadioTech] mSwitchQueue size : " + mSwitchQueue.size());
                logicLog("[doSwitchRadioTech] waiting for " +
                        "INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE broadcast.");
            } else {
                logicLog("[doSwitchRadioTech] Error switch Queue!.");
            }
        } else {
            logicLog("[doSwitchRadioTech] No need switch raido technology.");
            finishSwitchMode();
        }
    }

    /**
     * Provide get network type by soltId.
     * @param slotId the slot
     * @return network type
     */
    public int getNetWorkTypeBySlotId(int slotId) {
        ///provide get network type method for SVLTERatController

        boolean is4GCdmaCard = is4GCdmaCard(mCardTypes[slotId]);
        int netWorkType = SvlteRatController.RAT_MODE_CSFB;
        if (sCardModes[slotId] == RADIO_TECH_MODE_SVLTE) {
            LteDcPhoneProxy lteDcPhoneProxy = (LteDcPhoneProxy) PhoneFactory.getPhone(slotId);
            int subId = lteDcPhoneProxy.getSubId();
            int ratMode = Settings.Global.getInt(mContext.getContentResolver(),
                    SvlteUtils.getCdmaRatModeKey(subId),
                    SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G.ordinal());
            logicLog("getRatMode ratMode= " + ratMode + " subId = " + subId);
            SvlteRatController.SvlteRatMode orginMode
                = SvlteRatController.SvlteRatMode.values()[ratMode];
            int capabilityPhoneId = Integer.valueOf(
                    SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;
            logicLog("[switchRadioTechnology] capabilityPhoneId: " + capabilityPhoneId);

            if (is4GCdmaCard) {
                if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_4G;
                } else if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_2G |
                                  SvlteRatController.RAT_MODE_SVLTE_3G;
                } else {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_2G |
                                  SvlteRatController.RAT_MODE_SVLTE_3G |
                                  SvlteRatController.RAT_MODE_SVLTE_4G;
                }

                if (slotId != capabilityPhoneId) {
                    netWorkType &= ~SvlteRatController.RAT_MODE_SVLTE_4G;
                }

            } else {
                if (isUsimOnlyCard(mCardTypes[slotId])) {
                    // For lab test USIM only card test case fail. Allow USIM only test card
                    // switch to LTE data only mode.
                    if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_4G;
                    } else if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_2G |
                                      SvlteRatController.RAT_MODE_SVLTE_3G;
                    } else {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_2G |
                                      SvlteRatController.RAT_MODE_SVLTE_3G |
                                      SvlteRatController.RAT_MODE_SVLTE_4G;
                    }

                    if (slotId != capabilityPhoneId) {
                        netWorkType &= ~SvlteRatController.RAT_MODE_SVLTE_4G;
                    }
                } else {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                              SvlteRatController.RAT_MODE_SVLTE_2G |
                              SvlteRatController.RAT_MODE_SVLTE_3G;
                }
            }
        }
        return netWorkType;
    }

    private void switchRadioTechnology(int slotId) {
        logicLog("[switchRadioTechnology] Switch slotId: " + slotId
            + (mOldCardModes[slotId] == RADIO_TECH_MODE_SVLTE ? " SVLTE" : " CSFB")
            + " -->" + (sCardModes[slotId] == RADIO_TECH_MODE_SVLTE ? " SVLTE" : " CSFB"));
        boolean is4GCdmaCard = is4GCdmaCard(mCardTypes[slotId]);
        boolean isLteSupport = (SystemProperties.getInt("ro.mtk_lte_support", 0) == 1);
        logicLog("[switchRadioTechnology][is4GCdmaCard]: " + is4GCdmaCard
                + ", [isLteSupport]: " + isLteSupport);

        LteDcPhoneProxy lteDcPhoneProxy = (LteDcPhoneProxy) PhoneFactory.getPhone(slotId);
        SvlteRatController svlteRatController = lteDcPhoneProxy.getSvlteRatController();
        int netWorkType = SvlteRatController.RAT_MODE_CSFB;
        if (sCardModes[slotId] == RADIO_TECH_MODE_SVLTE) {
            if (mWaitingRilSocketConnect) {
                connectRilSocket();
                return;
            }

            int subId = lteDcPhoneProxy.getSubId();
            if (subId < 0) {
                mNeedReSwitch[slotId] = true;
            } else {
                mNeedReSwitch[slotId] = false;
            }
            logicLog("[switchRadioTechnology][needReSwitch][slotId]: "
                    + mNeedReSwitch[slotId] + " subId = " + subId);

            int ratMode = Settings.Global.getInt(mContext.getContentResolver(),
                    SvlteUtils.getCdmaRatModeKey(subId),
                    SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G.ordinal());
            logicLog("getRatMode ratMode= " + ratMode + " subId = " + subId);
            SvlteRatController.SvlteRatMode orginMode
                = SvlteRatController.SvlteRatMode.values()[ratMode];

            int capabilityPhoneId = Integer.valueOf(
                        SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;
            logicLog("[switchRadioTechnology] capabilityPhoneId: " + capabilityPhoneId);
            if (isLteSupport && is4GCdmaCard) {
                if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_4G;
                } else if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_2G |
                                  SvlteRatController.RAT_MODE_SVLTE_3G;
                } else {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                  SvlteRatController.RAT_MODE_SVLTE_2G |
                                  SvlteRatController.RAT_MODE_SVLTE_3G |
                                  SvlteRatController.RAT_MODE_SVLTE_4G;
                }

                if (slotId != capabilityPhoneId) {
                    netWorkType &= ~SvlteRatController.RAT_MODE_SVLTE_4G;
                }

            } else {
                if (isUsimOnlyCard(mCardTypes[slotId])) {
                    // For lab test USIM only card test case fail. Allow USIM only test card
                    // switch to LTE data only mode.
                    if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_4G;
                    } else if (orginMode == SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_2G |
                                      SvlteRatController.RAT_MODE_SVLTE_3G;
                    } else {
                        netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                                      SvlteRatController.RAT_MODE_SVLTE_2G |
                                      SvlteRatController.RAT_MODE_SVLTE_3G |
                                      SvlteRatController.RAT_MODE_SVLTE_4G;
                    }

                    if (slotId != capabilityPhoneId) {
                        netWorkType &= ~SvlteRatController.RAT_MODE_SVLTE_4G;
                    }
                } else {
                    netWorkType = SvlteRatController.RAT_MODE_SVLTE |
                              SvlteRatController.RAT_MODE_SVLTE_2G |
                              SvlteRatController.RAT_MODE_SVLTE_3G;
                }
            }
        }
        configModemStatus();
        logicLog("[switchRadioTechnology][netWorkType]: " + netWorkType);
        mSwitchingSlotId = slotId;
        svlteRatController.setRadioTechnology(netWorkType, null);
    }

    private boolean is4GCdmaCard(int cardType) {
        if (containsUsim(cardType) && containsCdma(cardType)) {
            return true;
        }
        return false;
    }

    private boolean is3GCdmaCard(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_USIM) == 0 &&
            (cardType & UiccController.CARD_TYPE_SIM) == 0 &&
            containsCdma(cardType)) {
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

    private boolean isNonCard(int cardType) {
        if (cardType == 0) {
            return true;
        }
        return false;
    }

    private void setupSvlteSystemProp(int[] cardModes) {
        // Dual or single sim case
        if (TelephonyManager.getDefault().getPhoneCount() ==
                PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            SystemProperties.set(SVLTE_PROP, String.valueOf(cardModes[0]));
        } else {
            SystemProperties.set(SVLTE_PROP, cardModes[0] + "," + cardModes[1]);
        }
    }

    private int[] calculateCardMode() {
        // Get the slots application here.
        int[] cardModes = new int[TelephonyManager.getDefault().getPhoneCount()];
        if (OPERATOR_OP09.equals(mOperatorSpec) && SPEC_OP09_A.equals(mOP09Spec)) {
            cardModes[0] = RADIO_TECH_MODE_SVLTE;
            for (int i = 1; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                cardModes[i] = RADIO_TECH_MODE_CSFB;
            }
            logicLog("[calculateCardMode] >>> OPERATOR_OP09 case.");
            printRadioModes(cardModes);
            return cardModes;
        }

        // Get slot 0 prefer radio tech
        int slot0Prefer = getPreferRadioTech(mCardTypes[PhoneConstants.SIM_ID_1],
                                             PhoneConstants.SIM_ID_1);

        // Dual or single sim case
        if (TelephonyManager.getDefault().getPhoneCount() ==
                PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            // Single slot case
            cardModes[0] = slot0Prefer;
            logicLog("[calculateCardMode] >>> SINGLE SIM case.");
            printRadioModes(cardModes);
            return cardModes;
        }
        // Dual sim case here.
        int slot1Prefer = getPreferRadioTech(mCardTypes[PhoneConstants.SIM_ID_2],
                                             PhoneConstants.SIM_ID_2);
        if (slot0Prefer == slot1Prefer && slot1Prefer == RADIO_TECH_MODE_SVLTE) {
            if ((mCardTypes[0] > UiccController.CARD_TYPE_NONE &&
                    mCardTypes[1] > UiccController.CARD_TYPE_NONE)
                    || (mCardTypes[0] == UiccController.CARD_TYPE_NONE &&
                    mCardTypes[1] == UiccController.CARD_TYPE_NONE)) {
                int capability = SystemProperties.getInt(
                    PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
                int csfbSlot = capability == 1 ? 0 : 1;
                cardModes[capability] = RADIO_TECH_MODE_SVLTE;
                cardModes[csfbSlot] = RADIO_TECH_MODE_CSFB;
                mSlotBeSwitchCardType = -1;
                if (SvlteUiccUtils.getInstance().isCt3gDualMode(csfbSlot)) {
                    mSlotBeSwitchCardType = csfbSlot;
                }
            } else {
                if (mCardTypes[0] > mCardTypes[1]) {
                    cardModes[0] = RADIO_TECH_MODE_SVLTE;
                    cardModes[1] = RADIO_TECH_MODE_CSFB;
                } else {
                    cardModes[0] = RADIO_TECH_MODE_CSFB;
                    cardModes[1] = RADIO_TECH_MODE_SVLTE;
                }
            }
        } else {
            cardModes[0] = slot0Prefer;
            cardModes[1] = slot1Prefer;
        }
        logicLog("[calculateCardMode] >>> WP SOLUTION 2 case.");
        printRadioModes(cardModes);
        return cardModes;
    }

    private void printRadioModes(int[] cardModes) {
        if (cardModes.length == 0) {
            logicLog("[printRadioModes] error cardModes.");
            return;
        }
        for (int slot = 0; slot < cardModes.length; slot++) {
            String mode = (cardModes[slot] == RADIO_TECH_MODE_CSFB ?
                    "RADIO_TECH_MODE_CSFB" : "RADIO_TECH_MODE_SVLTE");
            logicLog("[printRadioModes] slot " + slot + " : mCardTypes = " + mCardTypes[slot] +
                    ", mode = " + mode);
        }
    }

    private int getPreferRadioTech(int cardType, int slot) {
        int prefer = RADIO_TECH_MODE_UNKNOWN;
        switch (cardType) {
        case UiccController.CARD_TYPE_USIM | UiccController.CARD_TYPE_CSIM:
        case UiccController.CARD_TYPE_CSIM:
        case UiccController.CARD_TYPE_RUIM:
            prefer = RADIO_TECH_MODE_SVLTE;
            break;
        case UiccController.CARD_TYPE_NONE:
            prefer = mOldCardModes[slot];
            break;
        default:
            prefer = RADIO_TECH_MODE_CSFB;
            break;
        }
        return prefer;
    }

    private void startSwitchMode() {
        logicLog("Broadcast startSwitchMode");
        mInSwitching.set(true);
        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_START);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        sendBroadcast(intent, UserHandle.USER_ALL);
    }

    private void finishSwitchMode() {
        logicLog("Broadcast finish switch mode");
        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_DONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        sendBroadcast(intent, UserHandle.USER_ALL);
        setSvlteModeProperties();

        if (mPendingCardTypes != null) {
            logicLog("Start switch mode pended");
            mCardTypes = mPendingCardTypes;
            mOldCardModes = sCardModes;
            if (!isSwitchInAirplaneMode()) {
                sCardModes = calculateCardMode();
                setupSvlteSystemProp(sCardModes);
            }
            doSwitchRadioTech();
            mPendingCardTypes = null;
        } else {
            mInSwitching.set(false);
        }
    }

    private void sendBroadcast(Intent intent, int userId) {
        try {
            ActivityManagerNative.getDefault().broadcastIntent(
                null, intent, null, null, Activity.RESULT_OK, null, null,
                null, AppOpsManager.OP_NONE, false, false, userId);
        } catch (RemoteException ex) {
            Rlog.e(LOG_TAG, "Error while calling sendBroadcast", ex);
        }
    }

    /**
     * Get slot1`s radio technology mode.
     * @return slot1`s radio technology mode.
     */
    public static int getRadioTechnologyMode() {
        int mode = getRadioTechnologyMode(PhoneConstants.SIM_ID_1);
        log("[getRadioTechnologyMode] mode : " + mode);
        return mode;
    }

    /**
     * Get the Radio technology Mode by slot.
     * @param slotId slot id
     * @return RADIO_TECH_MODE_UNKNOWN or RADIO_TECH_MODE_SVLTE
     * or RADIO_TECH_MODE_CSFB
     */
    public static int getRadioTechnologyMode(int slotId) {
        int mode = RADIO_TECH_MODE_UNKNOWN;
        Phone phone = PhoneFactory.getPhone(slotId);

        if (phone != null) {
            PhoneBase phoneBase = (PhoneBase) ((PhoneProxy) phone).getActivePhone();
            if (phoneBase != null) {
                if ((phoneBase.getPhoneName()).equals("CDMA")) {
                    mode = RADIO_TECH_MODE_SVLTE;
                } else {
                    mode = RADIO_TECH_MODE_CSFB;
                }
            }
        }
        log("[getRadioTechnologyMode] mode : " + mode + ", slotId : " + slotId);
        return mode;
    }

    /**
     * Get the SVLTE slot id.
     * @return CSFB_ON_SLOT or SVLTE_ON_SLOT_0
     * or SVLTE_ON_SLOT_1
     */
    public static int getActiveSvlteModeSlotId() {
        int svlteSlotId = CSFB_ON_SLOT;
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            log("[getActiveSvlteModeSlotId] SVLTE not support, return -1.");
            return svlteSlotId;
        }
        for (int i = 0; i < sCardModes.length; i++) {
            if (sCardModes[i] == RADIO_TECH_MODE_SVLTE) {
                svlteSlotId = i;
            }
        }
        log("[getActiveSvlteModeSlotId] slotId: " + svlteSlotId);
        return svlteSlotId;
    }

    /**
     * Get slot id which connect to c2k rild socket.
     * @return slot id which connect to c2k rild socket
     */
    public static int getCdmaSocketSlotId() {
        int slotId = Integer.parseInt(SystemProperties.get(CDMA_PROP, "1")) - 1;
        log("[getCdmaSocketSlotId] slotId: " + slotId);
        return  slotId;
    }

    /**
     * Set slotId connect to c2k rild socket.
     * @param slotId slot id which connect to c2k rild socket
     */
    public static void setCdmaSocketSlotId(int slotId) {
        if (SvlteUtils.isValidateSlotId(slotId)) {
            SystemProperties.set(CDMA_PROP, Integer.toString(slotId + 1));
            log("[setCdmaSocketSlotId] slotId: " + slotId);
        }
    }

    private boolean containsCdma(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_RUIM) > 0 ||
            (cardType & UiccController.CARD_TYPE_CSIM) > 0) {
            return true;
        }
        return false;
    }

    private boolean containsGsm(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_SIM) > 0 ||
            (cardType & UiccController.CARD_TYPE_USIM) > 0) {
            return true;
        }
        return false;
    }

    private boolean containsUsim(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_USIM) > 0) {
            return true;
        }
        return false;
    }

    private boolean isUsimOnlyCard(int cardType) {
        return (containsUsim(cardType) && !containsCdma(cardType));
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
       @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logicLog("[SvlteModeController] onReceive, action: " + action);
            if (SvlteRatController.INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE.equals(action)) {
                if (mInSwitching.get()) {
                    boolean needHandler = (ACTION_FORM_MODE_CONTROLLER ==
                            intent.getIntExtra(SvlteRatController.EXTRA_SVLTE_RAT_SWITCH_PRIORITY,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX));
                    int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                            SubscriptionManager.INVALID_PHONE_INDEX);
                    logicLog("[SvlteModeController]  : Phone[" + phoneId + "] Switch Finished!");
                    // Need to disconnect c2k ril  after switch to CSFB from SVLTE
                    int slotId = SvlteUtils.getSlotId(phoneId);
                    boolean isSwitchingSlot = (slotId == mSwitchingSlotId);
                    logicLog("[SvlteModeController] isSwitchingSlot is " + isSwitchingSlot +
                                              " needHandler is " + needHandler);
                    if (!needHandler || !isSwitchingSlot) {
                        logicLog("[SvlteModeController] no need handler this case or Error slot.");
                        return;
                    }
                    mSwitchingSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
                    if (slotId == getCdmaSocketSlotId()
                            && sCardModes[slotId] == RADIO_TECH_MODE_CSFB
                            && mSvlteModeOn) {
                        disconnectRilSocket(phoneId);
                    }
                    if (mSwitchQueue.isEmpty()) {
                        logicLog("[SvlteModeController] All switch task done," +
                                "send ACTION_SET_RADIO_TECHNOLOGY_DONE broadcast.");
                        finishSwitchMode();
                    } else if (mSwitchQueue.size() == 1) {
                        int switchSlot = mSwitchQueue.get(0);
                        logicLog("[SvlteModeController] First slot switch done. Now switch Slot: "
                                + switchSlot);
                        mSwitchQueue.remove(0);
                        switchRadioTechnology(switchSlot);
                    } else {
                        logicLog("[SvlteModeController] This should never happen.");
                    }
                } else {
                    setSvlteModeProperties();
                }
            }
        }
    };

    private void setSvlteModeProperties() {
        for (int slotId = 0; slotId < TelephonyManager.getDefault().getPhoneCount(); slotId++) {
            if (getRadioTechnologyMode(slotId) == RADIO_TECH_MODE_SVLTE) {
                logicLog("[setSvlteModeProperties] svlte");
                SystemProperties.set(TelephonyProperties.PROPERTY_RADIO_SVLTE_MODE, "svlte");
                return;
            }
        }
        logicLog("[setSvlteModeProperties] csfb");
        SystemProperties.set(TelephonyProperties.PROPERTY_RADIO_SVLTE_MODE, "csfb");
    }

    private void connectRilSocket() {
        if (SvlteUtils.isValidateSlotId(mSlotIdWaitConnect)) {
            logicLog("connectRilSocket, slotId = " + mSlotIdWaitConnect);
            SvlteUtils.getSvltePhoneProxy(mSlotIdWaitConnect).getNLtePhone().mCi
                .connectRilSocket();
            SvlteUtils.getSvltePhoneProxy(mSlotIdWaitConnect).getNLtePhone().mCi
                .registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        }
    }

    private void disconnectRilSocket(int phoneId) {
        if (SvlteUtils.isValidPhoneId(phoneId)) {
            logicLog("disconnectRilSocket, phoneId = " + phoneId);
            SvlteUtils.getSvltePhoneProxy(phoneId).getNLtePhone().mCi.disconnectRilSocket();
        }
    }

    private void unregisterForRilConnected() {
        if (SvlteUtils.isValidateSlotId(mSlotIdWaitConnect)) {
            logicLog("unregisterForRilConnected, phoneId = " + mSlotIdWaitConnect);
            SvlteUtils.getSvltePhoneProxy(mSlotIdWaitConnect).getNLtePhone().mCi
                            .unregisterForRilConnected(this);
            mSlotIdWaitConnect = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        }
    }

    /**
     * Check Md3 state when config modem status
     */
    private class ConfigModemRunnable implements Runnable {
        public ConfigModemRunnable() {
        }

        @Override
        public void run() {
            configModemStatus();
        }
    }

    ConfigModemRunnable mConfigModemRunnable = new ConfigModemRunnable();

    /**
     * Send AT+EMDSTATUS to MD1/MD3.
     */
    private void configModemStatus() {
        String md3State = SystemProperties.get(PROPERTY_NET_CDMA_MDMSTAT, "not ready");
        logicLog("configModemStatus md3State = " + md3State);
        if (!md3State.equals("ready")) {
            postDelayed(mConfigModemRunnable, INITIAL_RETRY_INTERVAL_MSEC);
            return;
        }

        int capability = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1);
        int cardType1 = 0;
        int cardType2 = 0;
        if (mCardTypes.length == 1) {
            cardType1 = mCardTypes[0];
        } else if (mCardTypes.length == 2) {
            cardType1 = mCardTypes[0];
            cardType2 = mCardTypes[1];
        }
        logicLog("configModemStatus cardType1=" + cardType1 + ", cardType2="
            + cardType2 + ", capability=" + capability);

        int modemStatus = 2;
        int remoteSimProtocol = 1;
        
        //FIXME: To refine Test SIM flow, 2015/06/25
        if (UiccController.isSvlteTestSimMode()) {
            if (mCardTypes.length == 1) {
                cardType1 = getFullCardType(0);
            } else if (mCardTypes.length == 2) {
                cardType1 = getFullCardType(0);
                cardType2 = getFullCardType(1);
            }
            logicLog("SvlteTestSimMode: cardType1=" + cardType1
                    + " ,cardType2=" + cardType2);
        }
        
        while (true) {
            if (OPERATOR_OP09.equals(mOperatorSpec) && SPEC_OP09_A.equals(mOP09Spec)) {
                logicLog("configModemStatus: OP09 A");
                if (isNonCard(cardType1)) {
                    // no card
                    logicLog("configModemStatus: no card");
                    modemStatus = 1;
                    remoteSimProtocol = 1;
                } else if (isGsmCard(cardType1)) {
                    // GSM only
                    logicLog("configModemStatus: GSM only");
                    modemStatus = 0;
                    remoteSimProtocol = 1;
                } else if (is3GCdmaCard(cardType1)) {
                    //CT 3G
                    logicLog("configModemStatus: CT 3G");
                    modemStatus = 1;
                    remoteSimProtocol = 1;
                } else if (is4GCdmaCard(cardType1)) {
                    logicLog("configModemStatus: CT 4G");
                    // CT 4G
                    if (!CdmaFeatureOptionUtils.isCdmaIratSupport()) {
                        modemStatus = 1;
                    } else {
                        modemStatus = 2;
                    }
                    remoteSimProtocol = 1;
                } else {
                    //other case, may not happen!
                    logicLog("configModemStatus: other case, may not happen!");
                    break;
                }
            } else {
                logicLog("configModemStatus: OM/CT C");
                // case1: slot1 CDMA
                if ((is4GCdmaCard(cardType1) || is3GCdmaCard(cardType1))
                        && (is4GCdmaCard(cardType2) || is3GCdmaCard(cardType2))) {
                    // 2 CDMA SIM, C2K modem always follow 3/4G slot
                    remoteSimProtocol = 1;
                    if (capability == 1) {
                        if (is4GCdmaCard(cardType1)) {
                            modemStatus = 2;
                        } else {
                            modemStatus = 1;
                        }
                    } else {
                        if (is4GCdmaCard(cardType2)) {
                            modemStatus = 2;
                        } else {
                            modemStatus = 1;
                        }
                    }
                    if (!CdmaFeatureOptionUtils.isCdmaIratSupport()) {
                        modemStatus = 1;
                    }
                    break;
                }

                // case2: slot1 CDMA && slot2 non-CDMA
                if (is4GCdmaCard(cardType1) && (isGsmCard(cardType2) || isNonCard(cardType2))) {
                    // slot1 CT 4G, slot2 any
                    if (!CdmaFeatureOptionUtils.isCdmaIratSupport()) {
                        modemStatus = 1;
                    } else {
                        modemStatus = 2;
                    }
                    
                    if (capability == 1) {
                        remoteSimProtocol = 1;
                    } else {
                        remoteSimProtocol = 2;
                    }
                    break;
                } else if (is3GCdmaCard(cardType1) && (isGsmCard(cardType2) || isNonCard(cardType2))) {
                    // slot1 CT 3G, slot2 any
                    modemStatus = 1;
                    if (capability == 1) {
                        remoteSimProtocol = 1;
                    } else {
                        remoteSimProtocol = 2;
                    }
                    break;
                }

                // case2: slot2 CDMA && slot1 non-CDMA
                if (isGsmCard(cardType1) || isNonCard(cardType1)) {
                    if (is4GCdmaCard(cardType2)) {
                        // slot1 GSM or N/A, slot2 CT 4G
                        if (!CdmaFeatureOptionUtils.isCdmaIratSupport()) {
                            modemStatus = 1;
                        } else {
                            modemStatus = 2;
                        }

                        if (capability == 1) {
                            remoteSimProtocol = 2;
                        } else {
                            remoteSimProtocol = 1;
                        }
                        break;
                    } else if (is3GCdmaCard(cardType2)) {
                        // slot1 GSM or N/A, slot2 CT 3G
                        modemStatus = 1;
                        if (capability == 1) {
                            remoteSimProtocol = 2;
                        } else {
                            remoteSimProtocol = 1;
                        }
                        break;
                    }
                }


                // case3: non-CDMA
                logicLog("configModemStatus mLastProtocol=" + mLastProtocol);
                if (isGsmCard(cardType1) && isGsmCard(cardType2)) {
                    // slot1 GSM, slot2 GSM
                    modemStatus = 0;
                    remoteSimProtocol = mLastProtocol != -1 ? mLastProtocol : 1;
                    break;
                } else if (isGsmCard(cardType1) && isNonCard(cardType2)) {
                    // slot1 GSM, slot2 N/A
                    modemStatus = 0;
                    remoteSimProtocol = mLastProtocol != -1 ? mLastProtocol : 1;
                    break;
                } else if (isNonCard(cardType1) && isGsmCard(cardType2)) {
                    // slot1 N/A, slot2 GSM
                    modemStatus = 0;
                    remoteSimProtocol = mLastProtocol != -1 ? mLastProtocol : 1;
                    break;
                } else if (isNonCard(cardType1) && isNonCard(cardType2)) {
                    // slot1 N/A, slot2 N/A
                	// FIXME: For ALPS02288750, to check if it is design typo
                    modemStatus = 2;
                    remoteSimProtocol = mLastProtocol != -1 ? mLastProtocol : 1;
                    break;
                }
            }
            break;
        }
        mLastProtocol = remoteSimProtocol;

        logicLog("configModemStatus modemStatus=" + modemStatus +
                ", remoteSimProtocol=" + remoteSimProtocol);
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        Phone[] proxyPhones = PhoneFactory.getPhones();
        logicLog("configModemStatus mConfgiGsmDone=" + mConfgiGsmDone + " ,mConfgiC2kDone=" +
                mConfgiC2kDone + " ,mWaitingRilSocketConnect=" + mWaitingRilSocketConnect);

        if (!mConfgiGsmDone) {
            for (int i = 0; i < phoneCount; i++) {
                ((SvltePhoneProxy) proxyPhones[i]).getLtePhone().configModemStatus(modemStatus,
                        remoteSimProtocol, null);
                logicLog("configModemStatus for slot[" + i + "] LTE phone.");
            }
            mConfgiGsmDone = true;
        }

        if (!mWaitingRilSocketConnect && !mConfgiC2kDone) {
            int cdmaSocketId = getCdmaSocketSlotId();
            logicLog("configModemStatus for C2K, cdmaSocketId=" + cdmaSocketId);
            ((SvltePhoneProxy) proxyPhones[cdmaSocketId]).getNLtePhone().configModemStatus(
                    modemStatus, remoteSimProtocol, null);
            mConfgiC2kDone = true;
            if (!isOP09Project() && !SystemProperties.get(MTK_C2K_SLOT2_SUPPORT).equals("1")) {
                enableMD3Sleep();
            }
        }
    }
    
    private boolean isOP09Project() {
        boolean isCTA = OPERATOR_OP09.equals(mOperatorSpec) && SPEC_OP09_A.equals(mOP09Spec);
        boolean isCTC = SystemProperties.get(MTK_C2K_6M_SUPPORT).equals("1");
        return isCTA || isCTC;
    }

    private void enableMD3Sleep() {
        
        int capability = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1);
        int cardType1 = 0;
        int cardType2 = 0;
        if (mCardTypes.length == 1) {
            cardType1 = mCardTypes[0];
        } else if (mCardTypes.length == 2) {
            cardType1 = mCardTypes[0];
            cardType2 = mCardTypes[1];
        }
        logicLog("enableMD3Sleep cardType1=" + cardType1 + ", cardType2="
            + cardType2 + ", capability=" + capability);

        if (mCardTypes.length == 2) {
            if (isGsmCard(cardType1) && (is3GCdmaCard(cardType2) || is4GCdmaCard(cardType2))) {
                PhoneBase phone = ((SvltePhoneProxy) PhoneFactory.getPhone(1)).getNLtePhone();
                if ((capability - 1) == 0) {
                    phone.mCi.enableMd3Sleep(1);
                    logicLog("enableMD3Sleep,G+C,set to 1");
                } else {
                    phone.mCi.enableMd3Sleep(0);
                    logicLog("enableMD3Sleep,G+C,set to 0");
                }
                
            } else if (isGsmCard(cardType2) && (is3GCdmaCard(cardType1) || is4GCdmaCard(cardType1))) {
                PhoneBase phone = ((SvltePhoneProxy) PhoneFactory.getPhone(0)).getNLtePhone();
                if ((capability - 1) == 1) {
                    phone.mCi.enableMd3Sleep(1);
                    logicLog("enableMD3Sleep,C+G,set to 1");
                } else {
                    phone.mCi.enableMd3Sleep(0);
                    logicLog("enableMD3Sleep,C+G,set to 1");
                }
            }
        }
        
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

    public boolean isSvlteModeSwitching() {
        return mInSwitching.get();
    }

    /**
     * Get telephony mode for SVLTE.
     * @return current telephony mode
     */
    public static int getTelephonyMode() {
        int currMajorSim = Integer.valueOf(
                SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;
        int svlteSlotId = getActiveSvlteModeSlotId();
        if (TelephonyManager.getDefault().getPhoneCount() ==
                PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            logicLog("[getTelephonyMode] >>> SINGLE SIM case.");
            if (svlteSlotId == PhoneConstants.SIM_ID_1) {
                return TELEPHONY_MODE_LC_SINGLE;
            } else {
                return TELEPHONY_MODE_LWTG_SINGLE;
            }
        } else if (TelephonyManager.getDefault().getPhoneCount() >
                PhoneConstants.MAX_PHONE_COUNT_DUAL_SIM) {
            return TELEPHONY_MODE_UNKNOWN;
        }

        logicLog("[getTelephonyMode] svlteSlotId : " + svlteSlotId
                + ", currMajorSim = " + currMajorSim);
        if (currMajorSim == PhoneConstants.SIM_ID_1) {
            if (svlteSlotId == PhoneConstants.SIM_ID_1) {
                return TELEPHONY_MODE_LC_G;
            } else if (svlteSlotId == PhoneConstants.SIM_ID_2) {
                return TELEPHONY_MODE_LWTG_C;
            } else {
                return TELEPHONY_MODE_LWTG_G;
            }
        } else if (currMajorSim == PhoneConstants.SIM_ID_2) {
            if (svlteSlotId == PhoneConstants.SIM_ID_1) {
                return TELEPHONY_MODE_C_LWTG;
            } else if (svlteSlotId == PhoneConstants.SIM_ID_2) {
                return TELEPHONY_MODE_G_LC;
            } else {
                return TELEPHONY_MODE_G_LWTG;
            }
        }
        return TELEPHONY_MODE_UNKNOWN;
    }

    private boolean isSwitchInAirplaneMode() {
        int airplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        if (airplaneMode == 1) {
            logicLog("[isSwitchInAirplaneMode] Switch mode by previous mode.");
            printRadioModes(sCardModes);
            return true;
        }
        return false;
    }

    /**
     * To override log format, add SvlteModeController prefix.
     * @param msg The log to print
     */
    private static void log(String msg) {
        Rlog.i(LOG_TAG, msg);
    }

    /**
     * Add "[SMC]" tag to division logic method and utils method.
     * @param msg The log to print
     */
    private static void logicLog(String msg) {
        Rlog.i(LOG_TAG, "[SMC]" + msg);
    }

    /**
     * Check if this slot need switch again.
     * @param slotId slot id
     * @return true if this slot need switch again
     */
    public boolean getNeedReSwitch(int slotId) {
        return mNeedReSwitch[slotId];
    }
}
