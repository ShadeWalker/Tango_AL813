package com.mediatek.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.SystemProperties;
import android.util.Log;

import com.android.settings.R;
import com.mediatek.settings.ext.IWifiExt;
import com.mediatek.settings.UtilsExt;

public class AccessPointExt {
    private static final String TAG = "AccessPointExt";

    /* OPEN AP & WFA test support */
    private static final String KEY_PROP_WFA_TEST_SUPPORT = "persist.radio.wifi.wpa2wpaalone";
    private static final String KEY_PROP_OPEN_AP_WPS = "mediatek.wlan.openap.wps";
    private static final String KEY_PROP_WFA_TEST_VALUE = "true";
    private static String sWFATestFlag = null;

    /* security type */
    public static final int SECURITY_WEP = 1;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_WPA_PSK = 3;
    public static final int SECURITY_WPA2_PSK = 4;
    public static final int SECURITY_EAP = 5;
    public static final int SECURITY_WAPI_PSK = 6;
    public static final int SECURITY_WAPI_CERT = 7;

    /* plug in */
    private static IWifiExt sExt = null;

    public AccessPointExt(Context context) {
        /* get plug in */
        if (sExt == null) {
            sExt = UtilsExt.getWifiPlugin(context.getApplicationContext());
        }
    }

    /**
     * add other security, like as wapi, wep
     * @param config
     * @return
     */
    public static int getSecurity(WifiConfiguration config) {
        /* support wapi psk/cert */
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_PSK)) {
            return SECURITY_WAPI_PSK;
        }

        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_CERT)) {
            return SECURITY_WAPI_CERT;
        }

        if (config.wepTxKeyIndex >= 0 && config.wepTxKeyIndex < config.wepKeys.length
                && config.wepKeys[config.wepTxKeyIndex] != null) {
            return SECURITY_WEP;
        }
        return -1;
    }

    public static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WAPI-PSK")) {
            /*  WAPI_PSK */
            return SECURITY_WAPI_PSK;
        } else if (result.capabilities.contains("WAPI-CERT")) {
            /* WAPI_CERT */
            return SECURITY_WAPI_CERT;
        }
        return -1;
    }

    public String getSecurityString(int security, Context context) {

        switch(security) {
            case SECURITY_WAPI_PSK:
                /*return WAPI_PSK string */
                return context.getString(R.string.wifi_security_wapi_psk);
            case SECURITY_WAPI_CERT:
                /* return WAPI_CERT string */
                return context.getString(R.string.wifi_security_wapi_certificate);
            default:
        }
        return null;
    }

    public int compareTo(String ssid, int security, String otherSsid, int otherSecurity) {
        return sExt.getApOrder(ssid, security, otherSsid, otherSecurity);
    }

    /**
     *  support WFA test
     */
    public static boolean isWFATestSupported() {
        if (sWFATestFlag == null) {
            sWFATestFlag = SystemProperties.get(KEY_PROP_WFA_TEST_SUPPORT, "");
            Log.d(TAG, "isWFATestSupported(), sWFATestFlag=" + sWFATestFlag);
        }
        return KEY_PROP_WFA_TEST_VALUE.equals(sWFATestFlag);
    }
    /**
     *  reset WFA Flag
     */
    public static void resetWFAFlag() {
        sWFATestFlag = null;
    }
    /**
     *  support open ap wps test
     */
    public boolean isOpenApWPSSupported(boolean wpsAvailable) {
        boolean supported = false;
        if (wpsAvailable) {
            supported = "true".equals(SystemProperties.get(KEY_PROP_OPEN_AP_WPS, "false"));
        }
        return supported;
    }

}
