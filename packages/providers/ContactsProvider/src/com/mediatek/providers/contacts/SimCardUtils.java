package com.mediatek.providers.contacts;

import android.content.Context;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.telephony.TelephonyManager;
import com.mediatek.internal.telephony.ITelephonyEx;

public class SimCardUtils {

    public static final String TAG = "ProviderSimCardUtils";
    private static final String ACCOUNT_TYPE_POSTFIX = " Account";
    public static TelephonyManager mTelephonyManager;

    public SimCardUtils(Context context) {
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public interface SimType {
        String SIM_TYPE_SIM_TAG = "SIM";
        int SIM_TYPE_SIM = 0;

        String SIM_TYPE_USIM_TAG = "USIM";
        int SIM_TYPE_USIM = 1;

        // UIM
        int SIM_TYPE_UIM = 2;
        int SIM_TYPE_CSIM = 3;
        String SIM_TYPE_UIM_TAG = "RUIM";
        // UIM
        // UICC TYPE
        String SIM_TYPE_CSIM_TAG = "CSIM";
        // UICC TYPE
    }

    /**
     * M: [Gemini+] all possible icc card type are put in this array. it's a map
     * of SIM_TYPE => SIM_TYPE_TAG like SIM_TYPE_SIM => "SIM"
     */
    private static final SparseArray<String> SIM_TYPE_ARRAY = new SparseArray<String>();
    static {
        SIM_TYPE_ARRAY.put(SimType.SIM_TYPE_SIM, SimType.SIM_TYPE_SIM_TAG);
        SIM_TYPE_ARRAY.put(SimType.SIM_TYPE_USIM, SimType.SIM_TYPE_USIM_TAG);
        SIM_TYPE_ARRAY.put(SimType.SIM_TYPE_UIM, SimType.SIM_TYPE_UIM_TAG);
        SIM_TYPE_ARRAY.put(SimType.SIM_TYPE_CSIM, SimType.SIM_TYPE_CSIM_TAG);
    }

    public static boolean isSimInserted(int slotId) {
        boolean isSimInsert = false;

        if (mTelephonyManager != null) {
            if (ContactsProviderUtils.isGeminiSupport()) {
                isSimInsert = mTelephonyManager.hasIccCard(slotId);
            } else {
                isSimInsert = mTelephonyManager.hasIccCard(0);
            }
        }

        return isSimInsert;
    }

    /**
     * M: [Gemini+] get the icc card type by slotId
     *
     * @param slotId
     * @return the integer type
     */
    public static int getSimTypeBySlot(int slotId) {
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        String iccCardType = null;
        try {
            /// For ALPS01399514,ALPS01399519,there may be null pointer
            if (iTel != null) {
                if (ContactsProviderUtils.isGeminiSupport()) {
                    iccCardType = iTel.getIccCardType(slotId);
                } else {
                    iccCardType = iTel.getIccCardType(0);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "catched exception.");
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(iccCardType)) {
            Log.w(TAG, "failed to get iccCardType");
            return -1;
        }
        /*
         * M: add for UICC card type start
         */
//        if (SimType.SIM_TYPE_CSIM_TAG.equals(iccCardType)) {
//            iccCardType = SimType.SIM_TYPE_USIM_TAG;
//        }
        /*
         * M: add for UICC card type end
         */
        for (int i = 0; i < SIM_TYPE_ARRAY.size(); i++) {
            if (TextUtils.equals(SIM_TYPE_ARRAY.valueAt(i), iccCardType)) {
                return SIM_TYPE_ARRAY.keyAt(i);
            }
        }
        Log.w(TAG, "iccCardType " + iccCardType + " is not valid");
        return -1;
    }

    /**
     * M: [Gemini+] get the readable sim account type, like "SIM Account"
     *
     * @param simType
     * the integer sim type
     * @return the string like "SIM Account"
     */
    public static String getSimAccountType(int simType) {
        return SIM_TYPE_ARRAY.get(simType) + ACCOUNT_TYPE_POSTFIX;
    }

    /**
     * M: [Gemini+]SIM account type is a string like "USIM Account"
     *
     * @param accountType
     * @return
     */
    public static boolean isSimAccount(String accountType) {
        for (int i = 0; i < SIM_TYPE_ARRAY.size(); i++) {
            int simType = SIM_TYPE_ARRAY.keyAt(i);
            if (TextUtils.equals(getSimAccountType(simType), accountType)) {
                return true;
            }
        }
        Log.d(TAG, "account " + accountType + " is not SIM account");
        return false;
    }
}

