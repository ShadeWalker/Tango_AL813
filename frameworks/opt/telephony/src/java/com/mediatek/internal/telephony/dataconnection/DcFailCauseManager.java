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
 * MediaTek Inc. (C) 2015. All rights reserved.
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


package com.mediatek.internal.telephony.dataconnection;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.RetryManager;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ServiceState;
import android.telephony.Rlog;

import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.IGsmDCTExt;

import java.util.HashMap;
import java.util.EnumSet;



public class DcFailCauseManager {
    static public final String LOG_TAG = "DcFailCauseManager";
    static public final boolean DBG = true;
    static public final boolean VDBG = false;

    /**
        * Enable/Disable the feature of DcFailCauseManager.
        * The initial value empty is that means feature on.
        */
    public static final String MTK_DC_FCMGR_ENABLE = "persist.dc.fcmgr.enable";

    /**
        * Enable/Disable the AP fallback support.
        * The initial value empty is that means support.
        */
    public static final String MTK_AP_FALLBACK_SUPPORT = "persist.ap.fallback.support";

    private PhoneBase mPhone;
    public Operator mOperator = Operator.NONE;

    // FailCause defined by OP request
    private static final int USER_AUTHENTICATION = 29;
    private static final int SERVICE_OPTION_NOT_SUBSCRIBED = 33;

    private static final int PDP_FAIL_FALLBACK_RETRY = -1000;

    private static final String[][] specificPLMN = {{"33402", "334020"},  // OP001Ext
                                                    {"50501"}};           // OP002Ext

    public static boolean MTK_CC33_SUPPORT =
        SystemProperties.getInt("persist.data.cc33.support", 0) == 1 ? true : false;

    public static final boolean MTK_FALLBACK_RETRY_SUPPORT =
        SystemProperties.get("ro.mtk_fallback_retry_support").equals("1") ? true : false;

    private static final String FALLBACK_DATA_RETRY_CONFIG =
            "max_retries=13, 5000,10000,30000,60000,300000,1800000,3600000,14400000,"
            + "28800000,28800000,28800000,28800000,28800000";

    public enum Operator {
        NONE(-1),
        OP001Ext(0),
        OP002Ext(1);

        private static final HashMap<Integer,Operator> lookup
            = new HashMap<Integer,Operator>();

        static {
            for(Operator op : EnumSet.allOf(Operator.class)) {
                lookup.put(op.getValue(), op);
            }
        }

        private int value;

        private Operator(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Operator get(int value) {
            return lookup.get(value);
        }
    }

    private static final int[] OP001Ext_FAIL_CAUSE_TABLE = {
        USER_AUTHENTICATION,
        SERVICE_OPTION_NOT_SUBSCRIBED
    };

    private static final int[] OP002Ext_FAIL_CAUSE_TABLE = {
        PDP_FAIL_FALLBACK_RETRY
    };

    private boolean mIsBsp = SystemProperties.getBoolean("ro.mtk_bsp_package", false);
    private IGsmDCTExt mGsmDCTExt;

    public int mMaxRetryCount;
    public int mRetryTime;
    public int mRandomizationTime;
    public int mRetryCount;

    private enum retryConfigForDefault {
        maxRetryCount(1),
        retryTime(0),
        randomizationTime(0);

        private final int value;

        private retryConfigForDefault(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private enum retryConfigForOp001Ext {
        maxRetryCount(2),
        retryTime(45000),
        randomizationTime(0);

        private final int value;

        private retryConfigForOp001Ext(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private void setRetryConfig(Operator op, Object retryManager) {
        RetryManager rm = (RetryManager) retryManager;
        mRetryCount = rm.getRetryCount();
        if (DBG) {
            log("RetryCount: " + mRetryCount);
        }

        switch (op) {
            case OP001Ext:
                mMaxRetryCount = retryConfigForOp001Ext.maxRetryCount.getValue();
                mRetryTime = retryConfigForOp001Ext.retryTime.getValue();
                mRandomizationTime = retryConfigForOp001Ext.randomizationTime.getValue();
                log("[" + op + "] set SmRetry Config:" + mMaxRetryCount
                        + "/" + mRetryTime + "/" + mRandomizationTime);
                rm.configure(mMaxRetryCount, mRetryTime, mRandomizationTime);
                rm.setRetryCount(mRetryCount);
                break;
            case OP002Ext:
                log("[" + op + "] set SmRetry Config:"
                        + FALLBACK_DATA_RETRY_CONFIG);
                rm.configure(FALLBACK_DATA_RETRY_CONFIG);
                rm.setRetryCount(mRetryCount);
                break;
            default:
                mMaxRetryCount = retryConfigForDefault.maxRetryCount.getValue();
                mRetryTime = retryConfigForDefault.retryTime.getValue();
                mRandomizationTime = retryConfigForDefault.randomizationTime.getValue();
                log("[default] set SmRetry Config:" + mMaxRetryCount
                        + "/" + mRetryTime + "/" + mRandomizationTime);
                rm.configure(mMaxRetryCount, mRetryTime, mRandomizationTime);
                rm.setRetryCount(mRetryCount);
                break;
        }

    }

    public boolean createGsmDCTExt(PhoneBase phone) {
        mPhone = phone;
        boolean success = false;
        if (mIsBsp == false) {
            try{
                mGsmDCTExt =
                    MPlugin.createInstance(IGsmDCTExt.class.getName(), mPhone.getContext());
                if (DBG) {
                    log("mGsmDCTExt init on phone[" + mPhone.getPhoneId() + "]");
                }
                success = true;
            } catch (Exception e) {
                if (DBG) {
                    loge("mGsmDCTExt init fail");
                }
                e.printStackTrace();
            }
        }
        return success;
    }

    /** Constructor */
    public DcFailCauseManager() {
        if (DBG) {
            log("constructor");
        }
    }

    public boolean canHandleFailCause(Object cause, Object retryManager, String reason) {
        if (!SystemProperties.getBoolean(MTK_DC_FCMGR_ENABLE, true)) {
            if (DBG) {
                loge("dc fail cause handling mechanism is disabled");
            }
            return false;
        }

        boolean canHandle = false;
        boolean need = false;
        String handleCase = "";
        DcFailCause failCause = (DcFailCause) cause;

        int phoneId = -1;
        String plmn = "";
        try {
            phoneId = SubscriptionManager.getPhoneId(mPhone.getSubId());
            plmn = TelephonyManager.getDefault().getNetworkOperatorForPhone(phoneId);
            log("Check PLMN=" + plmn);
        } catch(Exception e) {
            if (DBG) {
                log("get plmn fail");
            }
            e.printStackTrace();
        }

        if (cause != null) {
            // Case 1: Check fail cause supported or not.
            handleCase = "c1";
        }

        if (cause != null && retryManager != null) {
            // Case 2: Check fail cause supported or not and if needs to reset retry configure.
            handleCase = "c2";
        } else if (cause != null && reason != null) {
            // Case 3: check reason if needs to retry dc or not.
            handleCase = "c3";
        }

        if ("c2".equals(handleCase) && reason != null) {
            // Case 4: Base on case 2, check reason if needs to retry dc or not.
            handleCase = "c4";
        }

        for (int i = 0; i < specificPLMN.length; i++) {
            //reset flag
            boolean isServingInSpecificPlmn = false;

            //check if serving in specific plmn
            for (int j = 0; j < specificPLMN[i].length; j++) {
                if (plmn.equals(specificPLMN[i][j])) {
                    isServingInSpecificPlmn = true;
                }
            }

            if (isServingInSpecificPlmn == true) {
                mOperator = Operator.get(i);
                log("Serving in specific op=" + mOperator + "(" + i + ")");
                break;
            }
        }

        switch (mOperator) {
            case OP001Ext:
                for (int tempCause : OP001Ext_FAIL_CAUSE_TABLE) {
                    DcFailCause dcFailCause = DcFailCause.fromInt(tempCause);
                    if (MTK_CC33_SUPPORT && failCause.equals(dcFailCause)) {
                        canHandle = true;
                    }
                }
                break;
            default:
                if (SystemProperties.getBoolean(MTK_AP_FALLBACK_SUPPORT, true)) {
                    DcFailCause dcFailCause = DcFailCause.fromInt(PDP_FAIL_FALLBACK_RETRY);
                    if (failCause.equals(dcFailCause)) {
                        canHandle = true;
                    }
                }
                break;
        }

        if (canHandle) {
            boolean ignoreReason = false;
            if ("c2".equals(handleCase)) {
                // Case 2
                setRetryConfig(mOperator, retryManager);
            } else if ("c3".equals(handleCase)) {
                // Case 3
                if (canIgnoredReason(mOperator, reason)) {
                    ignoreReason = true;
                    log("Can ignore this setup conn reason by Plmn!");
                }
                return ignoreReason;
            } else if ("c4".equals(handleCase)) {
                // Case 4
                setRetryConfig(mOperator, retryManager);
                if (canIgnoredReason(mOperator, reason)) {
                    ignoreReason = true;
                    log("Can ignore this setup conn reason by Plmn!");
                }
                return ignoreReason;
            }
            return canHandle;
        }

        if (!mIsBsp) {
            try {
                need = mGsmDCTExt.needSmRetry(failCause);
            } catch (Exception e) {
                loge("check needSmRetry fail!");
                e.printStackTrace();
            }
        }

        if (need) {
            boolean ignoreReasonByOp = false;
            if ("c2".equals(handleCase)) {
                // Case 2
                mGsmDCTExt.setSmRetryConfig(retryManager);
            } else if ("c3".equals(handleCase)) {
                // Case 3
                if (canIgnoredReason(mOperator, reason)) {
                    ignoreReasonByOp = true;
                    log("Can not ignore this setup conn reason by OP!");
                }
                return ignoreReasonByOp;
            } else if ("c4".equals(handleCase)) {
                // Case 4
                mGsmDCTExt.setSmRetryConfig(retryManager);
                if (canIgnoredReason(mOperator, reason)) {
                    ignoreReasonByOp = true;
                    log("Can not ignore this setup conn reason by OP!");
                }
                return ignoreReasonByOp;
            }
            return need;
        }

        log("Can not handle this fail cause!");
        return false;
    }

    private boolean canIgnoredReason(Operator op, String reason) {
        boolean ignored = false;

        switch (op) {
            case OP001Ext:
                if (TextUtils.equals(reason, Phone.REASON_DATA_ATTACHED)
                        || TextUtils.equals(reason, Phone.REASON_LOST_DATA_CONNECTION)
                        || TextUtils.equals(reason, DcFailCause.LOST_CONNECTION.toString())) {
                    ignored = true;
                }
                break;
            default:
                break;
        }

        return ignored;
    }

    @Override
    public String toString() {
        String ret = "DcFailCauseManager: { operator=" + mOperator + " maxRetry=" + mMaxRetryCount
                + " retryTime=" + mRetryTime + " randomizationTime" + mRandomizationTime
                + " retryCount" + mRetryCount;
        ret += "}";
        return ret;
    }

    private void loge(String s) {
        Rlog.e(LOG_TAG, s);
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
