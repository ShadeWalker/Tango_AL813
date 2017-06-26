/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.mediatek.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;

/**
 * Provide SVLTE UICC card/application related flow control.
 */

public class SvlteUiccController extends Handler {

    private static final boolean DBG = true;
    private static final String LOG_TAG = "SvlteUiccController";

    private static final int EVENT_C2K_WP_CARD_TYPE_READY = 1;
    private static final int EVENT_ICC_CHANGED = 2;

    private static final int INVALID_APP_TYPE = -1;

    private UiccController mUiccController;

    /**
     * To make sure SvlteUiccController single instance is created.
     *
     * @return SvlteUiccController instance
     */
    public static SvlteUiccController make() {
        return getInstance();
    }

    /**
     * Singleton to get SvlteUiccController instance.
     *
     * @return SvlteUiccController instance
     */
    public static synchronized SvlteUiccController getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private SvlteUiccController() {
        logd("Constructing");
        mUiccController = UiccController.getInstance();
        mUiccController.registerForC2KWPCardTypeReady(this, EVENT_C2K_WP_CARD_TYPE_READY, null);
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
    }

    /**
     * SvlteUiccController clear up.
     *
     */
    public void dispose() {
        logd("Disposing");
        //Cleanup ICC references
        mUiccController.unregisterForC2KWPCardTypeReady(this);
        mUiccController.unregisterForIccChanged(this);
        mUiccController = null;
    }

    /**
     * To check if it is under SvlteTestSimMode.
     *
     * @return true if it is under SvlteTestSimMode
     */
    public boolean isSvlteTestSimMode() {
        String testCardFlag = "persist.sys.forcttestcard";
        String forceCTTestCard = SystemProperties.get(testCardFlag, "0");
        logd("testCardFlag: " + testCardFlag + " = " + forceCTTestCard);
        return forceCTTestCard.equals("1");
    }

    @Override
    public void handleMessage(Message msg) {
        logd("receive message " + msg.what);
        AsyncResult ar = null;

        switch (msg.what) {
            case EVENT_C2K_WP_CARD_TYPE_READY:
                logd("handleMessage (EVENT_C2K_WP_CARD_TYPE_READY).");
                onCardTypeReady();
                break;
            case EVENT_ICC_CHANGED:
                ar = (AsyncResult) msg.obj;
                int index = 0;
                if (ar != null && ar.result instanceof Integer) {
                    index = ((Integer) ar.result).intValue();
                    logd("handleMessage (EVENT_ICC_CHANGED) , index = " + index);
                } else {
                    logd("handleMessage (EVENT_ICC_CHANGED), come from myself");
                }
                // SVLTE
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                        && UiccController.INDEX_SVLTE == index) {
                    index = SvlteModeController.getCdmaSocketSlotId();
                }
                onIccCardStatusChange(index);
                break;
            default:
                loge("Unhandled message with number: " + msg.what);
                break;
        }
    }

    private void doIccAppTypeSwitch(int phoneId, int radioTech) {
        IccCardProxy iccCard = (IccCardProxy) PhoneFactory.getPhone(phoneId).getIccCard();
        iccCard.setVoiceRadioTech(radioTech);
    }

    private void onCardTypeReady() {
        doRemoteSimAppSwitchCheck();
    }

    private void onIccCardStatusChange(int slotId) {
        if (DBG) {
            logd("slotId: " + slotId);
        }
        do {
            if (isSimReady(slotId) && isUsimTestSim(slotId)) {
                doOP09SvlteTestSimAppTypeSwitch(slotId);
                break;
            }

            if (isNeedSwitchRemoteSimApp(slotId)
                    && isNeedAlignRemoteSimAppType(slotId)) {
                int appType = getRemoteSimSlotAppType(slotId);
                if (DBG) {
                    logd("ICC: Remote SIM, switch to" + getAppFamilyTypeName(appType));
                }
                doIccAppTypeSwitch(slotId, getRadioTechByUiccAppType(appType));
            }
        } while (false);
    }

    private void doOP09SvlteTestSimAppTypeSwitch(int slotId) {
        //Workaround solution for OP09 SVLTE TDD ONLY USIM test SIM
        if (DBG) {
            logd("OP09 Switch gsm radio technology for usim in slot: " + slotId);
        }
        doIccAppTypeSwitch(slotId, ServiceState.RIL_RADIO_TECHNOLOGY_GSM);
    }

    private boolean isUsimTestSim(int slotId) {
        return (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && (isOP09())
                && (SvlteModeController.getCdmaSocketSlotId() == slotId)
                && (SvlteUiccUtils.getInstance().isUsimOnly(slotId))
                && (isSvlteTestSimMode()));
    }

    private boolean isOP09() {
        return SystemProperties.get("ro.operator.optr", "OM").equals("OP09");
    }

    private boolean isSimReady(int slotId) {
        UiccCard newCard = mUiccController.getUiccCard(slotId);
        return ((null != newCard)
                && (CardState.CARDSTATE_PRESENT == newCard.getCardState()));
    }

    /* ALPS02148729, For GSM+CDMA card, need switch SIM APP for SIM change PIN {*/
    private void doRemoteSimAppSwitchCheck() {
        int slotId = SvlteModeController.getActiveSvlteModeSlotId();
        if (isNeedSwitchRemoteSimApp(slotId)) {
            int appType = getRemoteSimSlotAppType(slotId);
            if (DBG) {
                logd("Remote SIM, IccCard switch to " + getAppFamilyTypeName(appType));
            }
            doIccAppTypeSwitch(slotId, getRadioTechByUiccAppType(appType));
        }
    }

    private boolean isNeedSwitchRemoteSimApp(int slotId) {
        return (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && (SvlteModeController.CSFB_ON_SLOT != slotId)
                && (SvlteUiccUtils.getInstance().isUsimWithCsim(slotId)));
    }

    private int getRemoteSimSlotAppType(int slotId) {
        return UiccController.APP_FAM_3GPP;
    }

    private int getRadioTechByUiccAppType(int appType) {
        switch (appType) {
            case UiccController.APP_FAM_3GPP: return ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
            case UiccController.APP_FAM_3GPP2: return ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A;
            default: return ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
        }
    }

    private String getAppFamilyTypeName(int appType) {
        switch (appType) {
            case UiccController.APP_FAM_3GPP: return "APP_FAM_3GPP";
            case UiccController.APP_FAM_3GPP2: return "APP_FAM_3GPP2";
            default: return "UNKNOWN";
        }
    }
    /* ALPS02148729, For GSM+CDMA card, need switch SIM APP for SIM change PIN }*/

    /* ALPS02266960: Always check to switch to 3GPP when ICC change {*/
    private boolean isNeedAlignRemoteSimAppType(int slotId) {
        return getActivePhoneAppType(slotId) != getRemoteSimSlotAppType(slotId);
    }

    private int getActivePhoneAppType(int phoneId) {
        PhoneProxy phone = (PhoneProxy) PhoneFactory.getPhone(phoneId);
        if (null != phone) {
            return (PhoneConstants.PHONE_TYPE_GSM == phone.getActivePhone().getPhoneType()) ?
                UiccController.APP_FAM_3GPP : UiccController.APP_FAM_3GPP2;
        } else {
            if (DBG) {
                loge("Could not get valid phone instance.");
            }
            return INVALID_APP_TYPE;
        }
    }
    /* ALPS02266960: Always check to switch to 3GPP when ICC change }*/

    /**
     * Create SvlteUiccApplicationUpdateStrategy instance.
     *
     * @hide
     */
    private static class SingletonHolder {
        public static final SvlteUiccController INSTANCE =
                new SvlteUiccController();
    }

    /**
     * Log level.
     *
     * @hide
     */
    private void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

}
