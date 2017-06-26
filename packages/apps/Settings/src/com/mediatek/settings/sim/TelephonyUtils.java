package com.mediatek.settings.sim;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.settings.Utils;
import com.mediatek.internal.telephony.ITelephonyEx;

import java.util.Iterator;


public class TelephonyUtils {
    private static final String TAG = "TelephonyUtils";

    /**
     * Get whether airplane mode is in on.
     * @param context Context.
     * @return True for on.
     */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Calling API to get subId is in on.
     * @param subId Subscribers ID.
     * @return {@code true} if radio on
     */
    public static boolean isRadioOn(int subId) {
        ITelephony itele = ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
        boolean isOn = false;
        try {
            if (itele != null) {
                isOn = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? false :
                    itele.isRadioOnForSubscriber(subId);
            } else {
                Log.d(TAG, "telephony service is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isOn = " + isOn + ", subId: " + subId);
        return isOn;
    }

    /**
     * check all slot radio on.
     * @param context context
     * @return is all slots radio on;
     */
    public static boolean isAllSlotRadioOn(Context context) {
        boolean isAllRadioOn = true;
        final TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final int numSlots = tm.getSimCount();
            for (int i = 0; i < numSlots; ++i) {
                final SubscriptionInfo sir = Utils.findRecordBySlotId(context, i);
                if (sir != null) {
                    isAllRadioOn = isAllRadioOn && isRadioOn(sir.getSubscriptionId());
                }
            }
            Log.d(TAG, "isAllSlotRadioOn()... isAllRadioOn: " + isAllRadioOn);
            return isAllRadioOn;
    }

    /**
     * capability switch.
     * @return true : switching
     */
    public static boolean isCapabilitySwitching() {
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        boolean isSwitching = false;
        try {
            if (telephonyEx != null) {
                isSwitching = telephonyEx.isCapabilitySwitching();
            } else {
                Log.d(TAG, "mTelephonyEx is null, returen false");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException = " + e);
        }
        Log.d(TAG, "isSwitching = " + isSwitching);
        return isSwitching;
    }


    /**
     * convert PhoneAccountHandle to subId.
     * @param context
     * @param handle
     * @return
     */
    public static int phoneAccountHandleTosubscriptionId(Context context, PhoneAccountHandle handle) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (handle != null) {
            final PhoneAccount phoneAccount = TelecomManager.from(context).getPhoneAccount(handle);
            if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) &&
                    TextUtils.isDigitsOnly(phoneAccount.getAccountHandle().getId())) {
                subId = Integer.parseInt(handle.getId());
            }
        }
        Log.d(TAG, "PhoneAccountHandleTosubscriptionId()... subId: " + subId);
        return subId;
    }

    /**
     * convert subId to PhoneAccountHandle
     * @param context
     * @param subId
     * @return
     */
    public static PhoneAccountHandle subscriptionIdToPhoneAccountHandle(Context context, final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(context);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            final String phoneAccountId = phoneAccountHandle.getId();

            if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    && TextUtils.isDigitsOnly(phoneAccountId)
                    && Integer.parseInt(phoneAccountId) == subId){
                return phoneAccountHandle;
            }
        }

        return null;
    }

    /**
     * which slot have the Main Capability(3G/4G).
     * @return
     */
    public static int getMainCapabilitySlotId() {
        int slotId = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
        Log.d(TAG, "getMainCapabilitySlotId()... slotId: " + slotId);
        return slotId;
    }
}