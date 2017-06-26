package com.mediatek.contacts.util;

import java.util.List;

import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.mediatek.telephony.TelephonyManagerEx;

public class VolteUtils {
    private static final String TAG = "VolteUtils";

    /**
     * Returns whether the VoLTE conference call enabled.
     * @param context the context
     * @return true if the VOLTE is supported and has Volte phone account
     */
    public static boolean isVoLTEConfCallEnable(Context context) {
        final TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccount> phoneAccouts = telecomManager.getAllPhoneAccounts();
        for (PhoneAccount phoneAccount : phoneAccouts) {
            if (phoneAccount.hasCapabilities(
                    PhoneAccount.CAPABILITY_VOLTE_ENHANCED_CONFERENCE)) {
                /// M:for ALPS02085376, need to judge if network type is LTE, because IMS may register
                // at GSM network, and this time can not make enhance conference call. @{
                PhoneAccountHandle handle = phoneAccount.getAccountHandle();
                if (handle != null) {
                    try {
                        String id = handle.getId();
                        int slotId = SubscriptionManager.from(context).getSlotId(Integer.parseInt(id));
                        int type = TelephonyManagerEx.getDefault().getNetworkType(slotId);
                        Log.d(TAG, "isVoLTEConfCallEnable,  id = " + id + ", slotId = " + slotId + ", type = " + type);
                        if (TelephonyManager.NETWORK_TYPE_LTE == type) {
                            return true;
                        } else {
                            continue;
                        }
                    } catch (NumberFormatException ex) {
                        Log.d(TAG, "isVoLTEConfCallEnable number error. (" + ex.toString() + ")");
                    }

                }
                /// @}
            }
        }
        return false;
    }
}
