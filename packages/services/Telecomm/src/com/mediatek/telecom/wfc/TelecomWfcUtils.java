package com.mediatek.telecom.wfc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.android.server.telecom.ErrorDialogActivity;

import com.mediatek.internal.telephony.ITelephonyEx;

/**
* Class to provide WFC related utility to Telecom
**/
public class TelecomWfcUtils {
    private static final String LOG_TAG = "TelecomWfcUtils";
    public static boolean isWfcEnabled(Context context) {
        boolean isWfcEnabled = (TelephonyManager.WifiCallingChoices.ALWAYS_USE == Settings.System.getInt(
                context.getContentResolver(), Settings.System.WHEN_TO_MAKE_WIFI_CALLS, TelephonyManager.WifiCallingChoices.NEVER_USE));
        Log.i(LOG_TAG, "[WFC] isWfcEnabled " + isWfcEnabled);
        return isWfcEnabled;
    }
    public static void showNoWifiServiceDialog(Context context) {
        Log.i(LOG_TAG, "[WFC] showNoWifiServiceDialog ");
        final Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(ErrorDialogActivity.SHOW_WIFI_UNAVAILABLE, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    public static boolean isSimPresent(Context context) {
        boolean ret = false;
        int[] subs =
                SubscriptionManager.from(context).getActiveSubscriptionIdList();
        if (subs.length == 0) {
            ret =  false;
        } else {
            ret = true;
        }
        Log.i(LOG_TAG, "[WFC]isSimPresent ret " + ret);
        return ret;
    }

    /* Checks whether any of RAT present: 2G/3G/LTE/Wi-Fi */
    public static boolean isRatPresent(Context context) {
        int cellularState = ServiceState.STATE_IN_SERVICE;
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        Bundle bundle = null;
        try {
            bundle = telephonyEx.getServiceState(SubscriptionManager.getDefaultVoiceSubId());
        } catch (RemoteException e) {
            Log.i(LOG_TAG, "[wfc]getServiceState() exception, subid: "
                    + SubscriptionManager.getDefaultVoiceSubId());
            e.printStackTrace();
        }
        if (bundle != null) {
            cellularState = ServiceState.newFromBundle(bundle).getState();
        }
        Log.i(LOG_TAG, "[wfc]cellularState:" + cellularState);
        WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi =
                cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        Log.i(LOG_TAG, "[wfc]wifi state:" + wifiManager.getWifiState());
        Log.i(LOG_TAG, "[wfc]wifi connected:" + wifi.isConnected());
        if ((wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED
                || (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED && !wifi.isConnected()))
                && cellularState != ServiceState.STATE_IN_SERVICE) {
            Log.i(LOG_TAG, "[wfc]No RAT present");
            return false;
        } else {
            Log.i(LOG_TAG, "[wfc]RAT present");
            return true;
        }
    }

}
