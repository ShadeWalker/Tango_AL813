package com.mediatek.incallui;

import android.os.Bundle;
import android.os.ServiceManager;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.Log;
import com.mediatek.common.dm.DmAgent;
import com.mediatek.telecom.TelecomManagerEx;

public final class InCallUtils {

    private static final String TAG = InCallUtils.class.getSimpleName();
    public static final String EXTRA_IS_IP_DIAL = "com.android.phone.extra.ip";

    public static boolean isDMLocked() {
        boolean locked = false;
        try {
            IBinder binder = ServiceManager.getService("DmAgent");
            DmAgent agent = null;
            if (binder != null) {
                agent = DmAgent.Stub.asInterface(binder);
            }
            if (agent != null) {
                locked = agent.isLockFlagSet();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "isDMLocked(): locked = " + locked);
        return locked;
    }

    private static boolean mPrivacyProtectOpen = false;
    /**
     * check whether PrivacyProtect open.
     * @return
     */
    public static boolean isprivacyProtectOpen() {
        Log.d(TAG, "mPrivacyProtectOpen: " + mPrivacyProtectOpen);
        return mPrivacyProtectOpen;
    }

    /**
     * set privacyProtectOpen value.
     * @param isPrivacyProtectOpen
     */
    public static void setprivacyProtectEnabled(boolean isPrivacyProtectOpen) {
        Log.d(TAG, "isPrivacyProtectOpen: " + isPrivacyProtectOpen);
        mPrivacyProtectOpen = isPrivacyProtectOpen;
    }

    /**
     * When there have more than one active call or background call and has no
     * incoming, it will be true, otherwise false.
     */
    public static boolean canHangupAllCalls() {
        CallList callList = CallList.getInstance();
        Call call = callList.getFirstCall();
        if (call != null && !Call.State.isIncoming(call.getState())
                && callList.getActiveAndHoldCallsCount() > 1) {
            return true;
        }
        return false;
    }

    /**
     * When there have more than one active call or background call and has no
     * incoming, it will be true, otherwise false.
     */
    public static boolean canHangupAllHoldCalls() {
        CallList callList = CallList.getInstance();
        Call call = callList.getFirstCall();
        if (call != null && !Call.State.isIncoming(call.getState())
                && callList.getActiveAndHoldCallsCount() > 1) {
            return true;
        }
        return false;
    }

    /**
     * When there has one active call and a incoming call which can be answered,
     * it will be true, otherwise false.
     */
    public static boolean canHangupActiveAndAnswerWaiting() {
        CallList callList = CallList.getInstance();
        Call call = callList.getFirstCall();
        if (call != null && Call.State.isIncoming(call.getState())
                && call.can(android.telecom.Call.Details.CAPABILITY_ANSWER)
                && callList.getActiveCall() != null
                && !hasCDMACall(call)) {
            return true;
        }
        return false;
    }

    /*
     * Get ip prefix from provider.
     */
    public static String getIpPrefix(Context context, String subId) {
        String ipPrefix = null;
        if (!TextUtils.isEmpty(subId)) {
            ipPrefix = Settings.System.getString(context.getContentResolver(),
                            "ipprefix" + subId);
        }
        Log.d(TAG, "ip prefix = " + ipPrefix);
        return ipPrefix;
    }

    /**
     * Whether this call is ip dial but without IPPrefix.
     * @param context
     * @param call
     * @return
     */
    public static boolean isIpCallWithoutPrefix(Context context, Call call) {
        if (call == null || call.getAccountHandle() == null) {
            Log.d(TAG, "isIpCallWithoutPrefix, call or account handle is null, do nothing.");
            return false;
        }

        Bundle extras = call.getTelecommCall().getDetails().getExtras();
        boolean isIpDial = (extras != null) && extras.getBoolean(EXTRA_IS_IP_DIAL, false);
        if (isIpDial) {
            String ipPrefix = getIpPrefix(context, call.getAccountHandle().getId());
            if (TextUtils.isEmpty(ipPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Added for suggesting phone account feature.
     * @param call
     * @return
     */
    public static PhoneAccountHandle getSuggestedPhoneAccountHandle(Call call) {
        if (call == null) {
            return null;
        }
        Bundle extras = call.getTelecommCall().getDetails().getExtras();
        final PhoneAccountHandle suggestedPhoneAccountHandle;
        if (extras != null) {
            suggestedPhoneAccountHandle = extras
                    .getParcelable(TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE);
        } else {
            suggestedPhoneAccountHandle = null;
        }
        Log.d(TAG, "getSuggestedPhoneAccountHandle(), suggestedPhoneAccountHandle is "
                + suggestedPhoneAccountHandle);
        return suggestedPhoneAccountHandle;
    }

    /**
     * Check if the call's account has CAPABILITY_CDMA_CALL_PROVIDER.
     */
    private static boolean hasCDMACall(Call call) {
        if (null == call) {
            return false;
        }

        Context context = com.android.incallui.InCallPresenter.getInstance()
                .getContext();

        PhoneAccountHandle accountHandle = call.getAccountHandle();

        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager == null) {
            return false;
        }

        PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);

        if (account != null) {
            return account.hasCapabilities(PhoneAccount.CAPABILITY_CDMA_CALL_PROVIDER);
        }
        return false;
    }
}