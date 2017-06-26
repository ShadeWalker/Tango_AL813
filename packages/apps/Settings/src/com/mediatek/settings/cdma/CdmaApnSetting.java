package com.mediatek.settings.cdma;

import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.TelephonyProperties;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.telephony.TelephonyManagerEx;

public class CdmaApnSetting {

    private static final String TAG = "CdmaApnSetting";
    private static final String CHINA_NW_MCC = "460";
    private static final String MACOO_NW_MCC = "455";
    private static final String CT_NUMERIC_1 = "46011";
    private static final String CT_NUMERIC_2 = "46003";

    public static String customizeQuerySelection(
            IApnSettingsExt ext, String numeric, int subId, String where) {
        String result = where;
        int slotId = SubscriptionManager.getSlotId(subId);
        if (ext.isCtPlugin() || CdmaUtils.getSIMCardType(slotId) == CdmaUtils.NOT_CT_SIM
                || SvlteModeController.getActiveSvlteModeSlotId() != slotId) {
            Log.d(TAG, "insert card is not CT card, just return");
            return result;
        }
        String sqlStr = "";
        String apn = "";
        String sourceType = "";
        try {
            ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                    ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            String mvnoType = telephonyEx.getMvnoMatchType(subId);
            String mvnoPattern = telephonyEx.getMvnoPattern(subId, mvnoType);
            // If mvnoType or mvnoPattern is null, should replace with ''
            sqlStr = " mvno_type=\'" + replaceNull(mvnoType)
                + "\'" + " and mvno_match_data=\'" + replaceNull(mvnoPattern) + "\'";
        }  catch (android.os.RemoteException e) {
            Log.d(TAG, "RemoteException " + e);
        }
        Log.d(TAG, "subId = " + subId + " slotId = " + slotId);
        ///M: for ap irat feature,numeric need get from PROPERTY_OPERATOR_NUMERIC,
        ///when has lte and ctwap,use lte
        String plmnNumeric =
                SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
        Log.d(TAG, "plmnNumeric = " + plmnNumeric + " numeric = " + numeric);
        if (numeric != null && (numeric.contains(CT_NUMERIC_1)
                || numeric.contains(CT_NUMERIC_2))) {
            numeric = getApnNumeric(numeric, subId);
            if (!(numeric.contains(CT_NUMERIC_2) || numeric.contains(CT_NUMERIC_1))) {
                sqlStr = sqlStr + " and apn <> \'ctwap\'";
            }
            Log.d(TAG, "final numeric = " + numeric);
            sqlStr = "((" + sqlStr + ")" + " or (sourceType = \'1\'))";
            result = "numeric=\'" + numeric + "\' and " + sqlStr;
            Log.d(TAG, "getFillListQuery result=" + result);
            return result;
        }
        if (plmnNumeric != null && plmnNumeric.length() >= 3
                && !plmnNumeric.startsWith(CHINA_NW_MCC)
                && !numeric.startsWith(MACOO_NW_MCC)) {
            Log.d(TAG, "ROAMING");
            apn += " and apn <> \'ctwap\'";
            result = "numeric=\'" + numeric + "\' and "
                    + "((" + sqlStr + apn + ")" + " or (sourceType = \'1\'))";
            Log.d(TAG, "getFillListQuery roaming result=" + result);
            return result;
        }
        return result;
    }

    // Since APN list is for current data network, so getDataNetworkType will be more accurate
    public static int getNetworkType(int subId) {
        int pstype = TelephonyManager.SIM_STATE_UNKNOWN;
        try {
            ITelephonyEx tphony = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            if (null != tphony) {
                Bundle bd = null;
                bd = tphony.getSvlteServiceState(subId);
                if (null != bd) {
                    ServiceState ss = ServiceState.newFromBundle(bd);
                    if (ss != null) {
                        Log.d(TAG, "ss = " + ss);
                        pstype = ss.getDataNetworkType();
                    }
                }
            }
        } catch (android.os.RemoteException e) {
            Log.d(TAG, "RemoteException " + e);
        }
       Log.d(TAG, "pstype = " + pstype);
       return pstype;
    }
    
    public static void customizeUnselectablePreferences(ArrayList<Preference> prefList, int subId) {
        if (CdmaUtils.getSIMCardType(
                SubscriptionManager.getSlotId(subId)) != CdmaUtils.NOT_CT_SIM) {
            prefList.clear();
        }
    }

    public static int getPreferredSubId(Context context, int subId) {
        if (!FeatureOption.MTK_SVLTE_SUPPORT) {
            return subId;
        }
        int c2kSlot = SvlteModeController.getActiveSvlteModeSlotId();
        int svlteRatMode = Settings.Global.getInt(context.getContentResolver(),
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(subId), TelephonyManagerEx.SVLTE_RAT_MODE_4G);
        // In 4G data only mode, only LteDcPhone is active, do not need to update
        // preferred apn sub id
        if (SubscriptionManager.getSlotId(subId) == c2kSlot &&
                svlteRatMode != TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY) {
            String numeric = TelephonyManager.getDefault().getSimOperator(subId);
            if (numeric != null &&
                    (numeric.equals(CT_NUMERIC_2) || numeric.equals(CT_NUMERIC_1))) {
                // If registered to LTE network, should set preferred apn using LTE sub
                numeric = getApnNumeric(numeric, subId);
                if (numeric != null && numeric.equals(CT_NUMERIC_1)) {
                    subId = SvlteUtils.getLteDcSubId(c2kSlot);
                    Log.d(TAG, "getPreferredSubId subId will use LTE_DC_SUB_ID");
                }
            }
         }
        return subId;
    }

    /**
     * get the target numeric dependent on the network camp.
     * 
     * @param numeric the CT SIM imsi contains mccmnc, should be 46003/46011
     * @param subId sub id
     * @return the result
     */
    private static String getApnNumeric(String numeric, int subId) {
        int pstype = getNetworkType(subId);
        String plmnNumeric =
                SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
        if (plmnNumeric.contains(CT_NUMERIC_1) && plmnNumeric.contains(CT_NUMERIC_2)) {
            if (pstype == TelephonyManager.NETWORK_TYPE_LTE ||
                    pstype == TelephonyManager.NETWORK_TYPE_EHRPD) {
                numeric = CT_NUMERIC_1;
            } else {
                // In case catch 4G network, but data actually connect to 3G,
                // so show 3G apn list
                numeric = CT_NUMERIC_2;
            }
        } else if (plmnNumeric.contains(CT_NUMERIC_1)) {
            // 4G only data, so numeric is only 46011
            numeric = CT_NUMERIC_1;
        } else if (plmnNumeric.contains(CT_NUMERIC_2)) {
            // In CT case if data network is EHRPD, need use CTLTE apn data
            if (pstype == TelephonyManager.NETWORK_TYPE_EHRPD) {
                numeric = CT_NUMERIC_1;
            } else {
                numeric = CT_NUMERIC_2;
            }
        } else {
            numeric = TelephonyManager.getDefault().getNetworkOperatorForSubscription(subId);
            if (numeric == null) {
                numeric = CT_NUMERIC_2;
            }
            Log.d(TAG, "plmnNumeric not contains 46003 or 46011, as ROAMING mumeric: " + numeric);
        }
        return numeric;
    }

    private static String replaceNull(String origString) {
        if (origString == null) {
            return "";
        } else {
            return origString;
        }
    }
}
