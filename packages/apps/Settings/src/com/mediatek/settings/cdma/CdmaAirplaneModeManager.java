
package com.mediatek.settings.cdma;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.telephony.TelephonyManagerEx;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.Log;

/**
 * This class is temp solution for C2K since C2K airplane mode could
 * take upto more than 10s, so need to sync with QS to avoid switch too fast
 *
 */
public class CdmaAirplaneModeManager {

    private static final String TAG = "CdmaAirplaneModeManager";

    private static final String INTENT_ACTION_AIRPLANE_CHANGE_DONE = "com.mediatek.intent.action.AIRPLANE_CHANGE_DONE";
    private static final String EXTRA_AIRPLANE_MODE = "airplaneMode";

    private Context mContext;

    private SwitchPreference mPref;
    private TelephonyManagerEx mTelMgr;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isDone = intent.getBooleanExtra(EXTRA_AIRPLANE_MODE, false);
            Log.d(TAG, "isDone = " + isDone);
            mPref.setEnabled(true);
        }
    };

    public CdmaAirplaneModeManager(Context context, SwitchPreference pref) {
        Log.d(TAG, "Construct CdmaAirplaneModeManager");
        mContext = context;
        mPref = pref;
        mTelMgr = TelephonyManagerEx.getDefault();
    }

    public void setEnable() {
        if (mPref != null) {
            mPref.setEnabled(isEnableToSwitch());
        }
    }

    public void registerBroadCastReceiver() {
        Log.d(TAG, "CdmaAirplaneModeManager_registerBroadCastReceiver");
        mContext.registerReceiver(mReceiver, new IntentFilter(INTENT_ACTION_AIRPLANE_CHANGE_DONE));
    }

    public void unRegisterBroadCastReceiver() {
        Log.d(TAG, "CdmaAirplaneModeManager_unRegisterBroadCastReceiver");
        mContext.unregisterReceiver(mReceiver);
    }

    private boolean isEnableToSwitch() {
        boolean isEnable = mTelMgr.isAllowAirplaneModeChange();
        Log.d(TAG, "isEnable = " + isEnable);
        return isEnable;
    }
}