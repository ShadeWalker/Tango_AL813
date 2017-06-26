package com.mediatek.settings;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.R;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.Log;
import android.widget.Toast;

/**
 * Add this class for [MTK_Enhanced4GLTE]
 * we don't always want the switch preference always auto switch, and save the preference.
 * In some conditions the switch should not be switch, and show a toast to user.
 */
public class Enhanced4GLteSwitchPreference extends SwitchPreference {
    private static final String LOG_TAG = "Enhanced4GLteSwitchPreference";
    private int mSubId;

    public Enhanced4GLteSwitchPreference(Context context) {
        super(context);
    }

    public Enhanced4GLteSwitchPreference(Context context, int subId) {
        this(context);
        mSubId = subId;
    }

    @Override
    protected void onClick() {
        if (canNotSetAdvanced4GMode()) {
            log("[onClick] can't set Enhanced 4G mode.");
            ShowTips(R.string.can_not_switch_enhanced_4g_lte_mode_tips);
        } else {
            log("[onClick] can set Enhanced 4G mode.");
            super.onClick();
        }
    }

    /**
     * Three conditions can't switch the 4G button.
     * 1. In call
     * 2. In the process of switching
     * 3. Airplane mode is on
     * @return
     */
    private boolean canNotSetAdvanced4GMode() {
        return TelephonyUtils.isInCall(getContext()) || isInSwitchProcess()
             || TelephonyUtils.isAirplaneModeOn(getContext());
    }

    /**
     * Get the IMS_STATE_XXX, so can get whether the state is in changing.
     * @return true if the state is in changing, else return false.
     */
    private boolean isInSwitchProcess () {
        int imsState = PhoneConstants.IMS_STATE_DISABLED;
        try {
            imsState = ImsManager.getInstance(getContext(), mSubId).getImsState();
        } catch (ImsException e) {
            Log.e(LOG_TAG, "[isInSwitchProcess]" + e);
            return false;
        }
        log("[canSetAdvanced4GMode] imsState = " + imsState);
        return imsState == PhoneConstants.IMS_STATE_DISABLING
                || imsState == PhoneConstants.IMS_STATE_ENABLING;
    }

    /**
     * Used for update the subId.
     * @param subId
     */
    public void setSubId(int subId) {
        mSubId = subId;
    }

    private void ShowTips(int resId) {
        Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
