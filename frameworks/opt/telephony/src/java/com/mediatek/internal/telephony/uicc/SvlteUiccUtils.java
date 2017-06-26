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

import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Provide SVLTE UICC card/application related utilities.
 */

public class SvlteUiccUtils {

    private static final boolean DBG = true;
    private static final String LOG_TAG = "SvlteUiccUtils";

    private static final String[] UICCCARD_PROPERTY_RIL_UICC_TYPE = {
        "gsm.ril.uicctype",
        "gsm.ril.uicctype.2",
        "gsm.ril.uicctype.3",
        "gsm.ril.uicctype.4",
    };
    private static final String[] PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };

    private static final String[] PROPERTY_RIL_CT3G = {
        "gsm.ril.ct3g",
        "gsm.ril.ct3g.2",
        "gsm.ril.ct3g.3",
        "gsm.ril.ct3g.4",
    };

    public static final int SIM_TYPE_NONE = 0;
    public static final int SIM_TYPE_GSM = 1;
    public static final int SIM_TYPE_CDMA = 2;

    private SvlteUiccUtils() {
    }

    /**
     * Singleton to get SvlteUiccUtils instance.
     *
     * @return SvlteUiccUtils instance
     */
    public static synchronized SvlteUiccUtils getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Check if it is USIM/SIM.
     *
     * @param uiccCard for current UICC card type
     * @return true if it is USIM/(SIM ONLY)
     */
    public boolean isUsimSim(UiccCard uiccCard) {
        if (null != uiccCard) {
            HashSet<String> fullUiccType = new HashSet<String>(
                    Arrays.asList(uiccCard.getFullIccCardType()));
            return (fullUiccType.contains("USIM")
                    || (uiccCard.getIccCardType().equals("SIM")));
        } else {
            return false;
        }
    }

    /**
     * Check if it is USIM and CSIM.
     *
     * @param uiccCard for current UICC card type
     * @return true if it is USIM and CSIM
     */
    public boolean isUsimWithCsim(UiccCard uiccCard) {
        if (null != uiccCard) {
            HashSet<String> fullUiccType = new HashSet<String>(
                    Arrays.asList(uiccCard.getFullIccCardType()));
            return (fullUiccType.contains("USIM")
                    && (fullUiccType.contains("CSIM")));
        } else {
            return false;
        }
    }

    /**
     * Check if it is UIM or CSIM.
     *
     * @param uiccCard for current UICC card type
     * @return true if it is UIM or CSIM
     */
    public boolean isRuimCsim(UiccCard uiccCard) {
        if (null != uiccCard) {
            return (uiccCard.getIccCardType().equals("RUIM")
                    || (uiccCard.getIccCardType().equals("CSIM")));
        } else {
            return false;
        }
    }

    /**
     * Check if it is USIM and CSIM.
     *
     * @param slotId for current slot
     * @return true if it is USIM and CSIM
     */
    public boolean isUsimWithCsim(int slotId) {
        HashSet<String> fullUiccType = new HashSet<String>(
                Arrays.asList(getFullIccCardType(slotId)));
        return (fullUiccType.contains("USIM")
                && (fullUiccType.contains("CSIM")));
    }

    /**
     * Check if it is USIM/SIM.
     *
     * @param slotId for current slot
     * @return true if it is USIM/(SIM ONLY)
     */
    public boolean isUsimSim(int slotId) {
        HashSet<String> fullUiccType = new HashSet<String>(
                Arrays.asList(getFullIccCardType(slotId)));
        return (fullUiccType.contains("USIM")||(getIccCardType(slotId).equals("SIM")));
    }

    /**
     * Check if it is UIM or CSIM.
     *
     * @param slotId for current slot
     * @return true if it is UIM or CSIM
     */
    public boolean isRuimCsim(int slotId) {
        return (getIccCardType(slotId).equals("RUIM")
                || (getIccCardType(slotId).equals("CSIM")));
    }

    /**
     * Check if it is USIM.
     *
     * @param slotId for current slot
     * @return true if it is USIM
     */
    public boolean isUsim(int slotId) {
        HashSet<String> fullUiccType = new HashSet<String>(
                Arrays.asList(getFullIccCardType(slotId)));
        return (fullUiccType != null) && (fullUiccType.contains("USIM"));
    }

    /**
     * Check if it has card.
     *
     * @param slotId for current slot
     * @return true if it has card
     */
    public boolean isHaveCard(int slotId) {
        HashSet<String> fullUiccType = new HashSet<String>(
                Arrays.asList(getFullIccCardType(slotId)));
        return (fullUiccType.contains("CSIM")
                || (fullUiccType.contains("RUIM"))
                || (fullUiccType.contains("UIM"))
                || (fullUiccType.contains("USIM"))
                || (fullUiccType.contains("SIM")));
    }

    /**
     * To get SVLTE SIM PIN application.
     *
     * @param uiccController for UiccController instance reference
     * @param slotId for current slot
     * @param family for application family
     * @return true if it is USIM
     */
    public UiccCardApplication getSvlteApplication(UiccController uiccController, int slotId,
            int family) {
        if (null != uiccController) {
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                int familyTemp = adjustAppType(uiccController.getUiccCard(slotId), family);
                if (DBG) {
                    logd("family: " + family + ", familyTemp: " + familyTemp);
                }
                return uiccController.getUiccCardApplication(slotId, familyTemp);
            } else {
                return uiccController.getUiccCardApplication(slotId, family);
            }
        } else {
            return null;
        }
    }

    /**
     * Check if it is USIM ONLY test card.
     *
     * @param slotId for current slot
     * @return true if it is USIM/(SIM ONLY)
     */
    public boolean isUsimOnly(int slotId) {
        return getIccCardType(slotId).equals("USIM");
    }

    /**
     * Check if it USIM+CSIM phone
     *
     * @param phoneId for current phone ID
     * @return true if it is USIM+CSIM phone
     */
    public boolean isUsimCsimPhone(int phoneId) {
        return (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && SvlteUtils.isActiveSvlteMode(phoneId)
                && isUsimWithCsim(phoneId));
    }

    /**
     * Create SvlteUiccUtils instance.
     *
     * @hide
     */
    private static class SingletonHolder {
        public static final SvlteUiccUtils INSTANCE =
                new SvlteUiccUtils();
    }

    /**
     * To get ICC Card type by slot ID.
     * To use it when UiccCard is not ready
     */
    private String getIccCardType(int slotId) {
        return (slotId >= 0 && slotId < UICCCARD_PROPERTY_RIL_UICC_TYPE.length)
                ? SystemProperties.get(UICCCARD_PROPERTY_RIL_UICC_TYPE[slotId]) : "";
    }

    /**
     * To get ICC FULL Card type by slot ID.
     * To use it when UiccCard is not ready
     */
    private String[] getFullIccCardType(int slotId) {
        return (slotId >= 0 && slotId < PROPERTY_RIL_FULL_UICC_TYPE.length)
                ? SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[slotId]).split(",")
                        : new String[]{""};
    }

    /**
     * To adjust application type.
     * Rules:
     * (USIM+CSIM) or (USIM+SIM): SIM PIN by MD1
     * (RUIM+SIM) or (RUIM/CSIM ONLY): SIM PIN by MD3
     *
     * @param uiccCard for current UICC card type
     * @param appType for current application type
     * @return adjusted application type
     */
    private int adjustAppType(UiccCard uiccCard, int appType) {
        if (DBG) {
            logd("appType: " + appType);
        }
        if (isUsimSim(uiccCard)) {
            return UiccController.APP_FAM_3GPP;
        } else {
            return UiccController.APP_FAM_3GPP2;
        }
    }

    /**
     * Check if the specified slot is CT 3G dual mode card.
     * @param slotId slot ID
     * @return if it's CT 3G dual mode card
     */
    public boolean isCt3gDualMode(int slotId) {
        String result = SystemProperties.get(PROPERTY_RIL_CT3G[slotId]);
        if (DBG) {
            logd("isCt3gDualMode: " + result);
        }
        return ("1".equals(result));
    }

    /**
     * Log level.
     *
     * @hide
     */
    private void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    /**
     * Get SIM card type by slot id.
     * @param slotId slot ID
     * @return the card type
     */
    public int getSimType(int slotId) {
        int simType = SIM_TYPE_NONE;
        int phoneCount = TelephonyManager.getDefault().getSimCount();
        if (slotId < 0 || slotId >= phoneCount) {
            logd("getSimType, invalid slotId:" + slotId);
            return simType;
        }

        String uiccType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[slotId]);
        logd("getSimType, uiccType[" + slotId + "] = " + uiccType);
        String appType[] = uiccType.split(",");
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
        
        logd("getSimType, fullType = " + fullType);
        if (fullType == UiccController.CARD_TYPE_NONE) {
            simType = SIM_TYPE_NONE;
        } else if ((fullType & UiccController.CARD_TYPE_CSIM) != 0
                || (fullType & UiccController.CARD_TYPE_RUIM) != 0) {
            simType = SIM_TYPE_CDMA;
        } else {
            simType = SIM_TYPE_GSM;

            // CT3G dual mode sim, may switch to SIM type for use
            if (fullType == UiccController.CARD_TYPE_SIM) {
                String ct3GDualMode = SystemProperties.get(PROPERTY_RIL_CT3G[slotId]);
                logd("ct3GDualMode: " + ct3GDualMode);
                if ("1".equals(ct3GDualMode)) {
                    simType = SIM_TYPE_CDMA;
                }
            }
        }
        logd("getSimType, simType = " + simType);
        return simType;
    }
}
