package com.mediatek.systemui.ext;

import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import static android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT;
import static android.telephony.TelephonyManager.NETWORK_TYPE_CDMA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;

/**
 * M: This enum defines the type of network type.
 */
public enum NetworkType {

    Type_G(0), Type_3G(1), Type_1X(2), Type_1X3G(3), Type_4G(4), Type_E(5), Type_H(6), Type_HP(7);//mod by HQ_machao1 at 20150618 HQ01180230

    private int mTypeId;

    private NetworkType(int typeId) {
        mTypeId = typeId;
    }

    public int getTypeId() {
        return mTypeId;
    }

    private static final String TAG = "NetworkType";
    private static NetworkType sDefaultNetworkType;
    private static final SparseArray<NetworkType> sNetworkTypeLookup =
            new SparseArray<NetworkType>();

    /**
     * Get NetworkType by dataNetType.
     *
     * @param dataNetType DataNet type value.
     * @return NetworkType.
     */
    public static final NetworkType get(final int dataNetType) {
        final NetworkType networkType = sNetworkTypeLookup.get(dataNetType, sDefaultNetworkType);
        Log.d(TAG, "getNetworkType, dataNetType = " + dataNetType
                + " to NetworkType = " + networkType.name());
        return networkType;
    }

    /**
     * Map network type to NetworkType Sets.
     *
     * @param showAtLeast3G Show At Least 3G.
     * @param show4gForLte show 4g For Lte.
     * @param hspaDataDistinguishable hspa Data Distinguishable.
     */
    public static final void mapNetworkTypeSets(final boolean showAtLeast3G,
            final boolean show4gForLte, final boolean hspaDataDistinguishable) {
        sNetworkTypeLookup.clear();

        sNetworkTypeLookup.put(NETWORK_TYPE_EVDO_0, Type_1X3G);
        sNetworkTypeLookup.put(NETWORK_TYPE_EVDO_A, Type_1X3G);
        sNetworkTypeLookup.put(NETWORK_TYPE_EVDO_B, Type_1X3G);
        sNetworkTypeLookup.put(NETWORK_TYPE_EHRPD, Type_1X3G);

        sNetworkTypeLookup.put(NETWORK_TYPE_UMTS, Type_3G);

        if (!showAtLeast3G) {
            sNetworkTypeLookup.put(NETWORK_TYPE_UNKNOWN, Type_G);
            sNetworkTypeLookup.put(NETWORK_TYPE_EDGE, Type_E);
            sNetworkTypeLookup.put(NETWORK_TYPE_CDMA, Type_G);//mod by HQ_machao1 at 20150618 HQ01180230
            sNetworkTypeLookup.put(NETWORK_TYPE_1xRTT, Type_1X);

            sDefaultNetworkType = Type_G;
        } else {
            sNetworkTypeLookup.put(NETWORK_TYPE_UNKNOWN, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_EDGE, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_CDMA, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_1xRTT, Type_3G);

            sDefaultNetworkType = Type_3G;
        }

        //mod by HQ_machao1 at 20150618 HQ01180230 begin
        TelephonyManager telephonyManager = TelephonyManager.getDefault();
        if ("46001".equals(telephonyManager.getNetworkOperator())) {
            sNetworkTypeLookup.put(NETWORK_TYPE_HSDPA, Type_H);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSUPA, Type_H);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSPA, Type_H);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSPAP, Type_HP);
            Log.d(TAG, "networkType is 46001, show H");
        }else {
            sNetworkTypeLookup.put(NETWORK_TYPE_HSDPA, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSUPA, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSPA, Type_3G);
            sNetworkTypeLookup.put(NETWORK_TYPE_HSPAP, Type_3G);
            Log.d(TAG, "networkType is not 46001, show 3G");
        }
        //mod by HQ_machao1 at 20150618 HQ01180230 end

        sNetworkTypeLookup.put(NETWORK_TYPE_LTE, Type_4G);
    }
}
