package com.mediatek.wifi;

import android.telephony.SubscriptionManager;
import android.util.Log;

public class WifiUtils {
	public static final String TAG = "Settings/WifiUtils";
	public static final int GET_SUBID_NULL_ERROR = -1;
    private static final String OP01 = "OP01";
    private static final String RO_OPERATOR_OPTR = "ro.operator.optr";
    public static final String WIFI_AP_AUTO_JOIN = "wifi_ap_auto_join_available";
	
    /*
    * @param slotId the sim slot user chooses,now get sub 0 of the sim,which feature need further 
    * negotiation with fpm
    * @return the sub id of sub 0 of the sim user chooses
    */
	public static int getSubId(int slotId) {
		   int[] SubIds = SubscriptionManager.getSubId(slotId);
		   if (SubIds != null) {
			   return SubIds[0];
		   } else {
			   return GET_SUBID_NULL_ERROR;
		   }   
	}
	
	public static boolean getCMCC() {
		return OP01.equals(android.os.SystemProperties.get(RO_OPERATOR_OPTR));
	}
}
