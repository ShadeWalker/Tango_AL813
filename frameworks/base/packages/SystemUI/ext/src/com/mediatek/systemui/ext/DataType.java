package com.mediatek.systemui.ext;

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
 * M: This enum defines the type of data connection type.
 */
public enum DataType {
    Type_1X(0), Type_3G(1), Type_4G(2), Type_E(3), Type_G(4), Type_H(5), Type_H_PLUS(6), Type_3G_PLUS(7);

    private int mTypeId;

    private DataType(int typeId) {
        mTypeId = typeId;
    }

    public int getTypeId() {
        return mTypeId;
    }

    private static final String TAG = "DataType";
    private static DataType sDefaultDataType;
    private static final SparseArray<DataType> sDataTypeLookup = new SparseArray<DataType>();

    /**
     * Get DataType by dataNetType.
     *
     * @param dataNetType DataNet Type value.
     * @return DataType.
     */
    public static final DataType get(final int dataNetType) {
        final DataType dataType = sDataTypeLookup.get(dataNetType, sDefaultDataType);
        Log.d(TAG, "getDataType, dataNetType = " + dataNetType
                + " to DataType = " + dataType.name());
        return dataType;
    }

    /**
     * Map network type to DataType Sets.
     *
     * @param showAtLeast3G Show At Least 3G.
     * @param show4gForLte show 4g For Lte.
     * @param hspaDataDistinguishable hspa Data Distinguishable.
     */
    public static final void mapDataTypeSets(final boolean showAtLeast3G,
            final boolean show4gForLte, final boolean hspaDataDistinguishable) {
        sDataTypeLookup.clear();

        sDataTypeLookup.put(NETWORK_TYPE_EVDO_0, Type_3G);
        sDataTypeLookup.put(NETWORK_TYPE_EVDO_A, Type_3G);
        sDataTypeLookup.put(NETWORK_TYPE_EVDO_B, Type_3G);
        sDataTypeLookup.put(NETWORK_TYPE_EHRPD, Type_3G);
        sDataTypeLookup.put(NETWORK_TYPE_UMTS, Type_3G);

        if (!showAtLeast3G) {
            sDataTypeLookup.put(NETWORK_TYPE_EDGE, Type_E);
            sDataTypeLookup.put(NETWORK_TYPE_CDMA, Type_1X);
            sDataTypeLookup.put(NETWORK_TYPE_1xRTT, Type_1X);

            sDefaultDataType = Type_G;
        } else {
            sDataTypeLookup.put(NETWORK_TYPE_UNKNOWN, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_EDGE, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_CDMA, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_1xRTT, Type_3G);

            sDefaultDataType = Type_3G;
        }

        if (hspaDataDistinguishable) {
            sDataTypeLookup.put(NETWORK_TYPE_HSDPA, Type_H);
            sDataTypeLookup.put(NETWORK_TYPE_HSUPA, Type_H);
            sDataTypeLookup.put(NETWORK_TYPE_HSPA, Type_H);
            sDataTypeLookup.put(NETWORK_TYPE_HSPAP, Type_H_PLUS);
        } else {
            sDataTypeLookup.put(NETWORK_TYPE_HSDPA, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_HSUPA, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_HSPA, Type_3G);
            sDataTypeLookup.put(NETWORK_TYPE_HSPAP, Type_3G);
        }

        sDataTypeLookup.put(NETWORK_TYPE_LTE, Type_4G);
    }
}
