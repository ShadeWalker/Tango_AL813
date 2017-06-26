package com.mediatek.incallui.volte;

import android.os.Bundle;
import android.text.TextUtils;

import com.android.incallui.Log;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.mediatek.telecom.TelecomManagerEx;

public class InCallUIVolteUtils {

    private static final String LOG_TAG = "InCallUIVolteUtils";
    private static final int INVALID_RES_ID = -1;

    public static boolean isVolteSupport() {
        return FeatureOptionWrapper.MTK_IMS_SUPPORT && FeatureOptionWrapper.MTK_VOLTE_SUPPORT;
    }

    //-------------For VoLTE normal call switch to ECC------------------
    public static boolean isVolteMarkedEcc(final android.telecom.Call.Details details) {
        boolean result = false;
        if (isVolteSupport() && details != null) {
            Bundle bundle = details.getExtras();
            if (bundle != null
                    && bundle.containsKey(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY)) {
                Object value = bundle.get(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY);
                if (value instanceof Boolean) {
                    result = (Boolean)value;
                }
            }
        }
        return result;
    }

    public static boolean isVolteMarkedEccChanged(final android.telecom.Call.Details oldDetails,
            final android.telecom.Call.Details newDetails) {
        boolean result = false;
        boolean isVolteMarkedEccOld = isVolteMarkedEcc(oldDetails);
        boolean isVolteMarkedEccNew = isVolteMarkedEcc(newDetails);
        result = !isVolteMarkedEccOld && isVolteMarkedEccNew;
        return result;
    }

    //-------------For VoLTE PAU field------------------
    public static String getVoltePauField(final android.telecom.Call.Details details) {
        String result = "";
        if (isVolteSupport() && details != null) {
            Bundle bundle = details.getExtras();
            if (bundle != null) {
                result = bundle.getString(TelecomManagerEx.EXTRA_VOLTE_PAU_FIELD, "");
            }
        }
        return result;
    }

    public static String getPhoneNumber(final android.telecom.Call.Details details) {
        String result = "";
        if (details != null) {
            if (details.getGatewayInfo() != null) {
                result = details.getGatewayInfo()
                        .getOriginalAddress().getSchemeSpecificPart();
            } else {
                result = details.getHandle() == null ? null : details.getHandle()
                        .getSchemeSpecificPart();
            }
        }
        if (result == null) {
            result = "";
        }
        return result;
    }

    public static boolean isPhoneNumberChanged(final android.telecom.Call.Details oldDetails,
            final android.telecom.Call.Details newDetails) {
        boolean result = false;
        String numberOld = getPhoneNumber(oldDetails);
        String numberNew = getPhoneNumber(newDetails);
        result = !TextUtils.equals(numberOld, numberNew);
        /*if (result) {
           	 log("number changed from " + numberOld + " to " + numberNew);
        	}*/
        return result;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
