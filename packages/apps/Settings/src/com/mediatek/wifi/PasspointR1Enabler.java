package com.mediatek.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.R;

/**
 * The class represents the action of Passpoint r1 enabler
 *
 */
public class PasspointR1Enabler implements Preference.OnPreferenceClickListener {
    private static final String TAG = "PasspointR1Enabler";
    private SwitchPreference mPasspointSwitch;
    private Context mContext;
    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;

    public PasspointR1Enabler(Context context, SwitchPreference preference, PreferenceScreen screen) {
        mContext = context;
        mPasspointSwitch = preference;

        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);

        mPasspointSwitch.setTitle(R.string.passpoint_title);
        mPasspointSwitch.setSummary(R.string.passpoint_summary);
        screen.addPreference(mPasspointSwitch);
    }

    public void resume() {
        Log.d(TAG, "resume");
        // Wi-Fi state is sticky, so just let the receiver update UI
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mPasspointSwitch.setOnPreferenceClickListener(this);
        refreshPasspointPreference(mWifiManager.isWifiEnabled());
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mPasspointSwitch.setOnPreferenceClickListener(null);
    }

    /*
     * onPreferenceClick: response for click mPasspointCheckBox
     * @see android.preference.Preference.OnPreferenceClickListener#onPreferenceClick(android.preference.Preference)
     */
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mPasspointSwitch) {
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_PASSPOINT_ON,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        }
        return true;
    }

    /*
     * refreshPasspointPreference: refresh mPasspointCheckBox's status of checked and enabled
     */
    public void refreshPasspointPreference(boolean wifiEnabled) {
        mPasspointSwitch.setChecked(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_PASSPOINT_ON, 0) == 1);
        mPasspointSwitch.setEnabled(wifiEnabled);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    refreshPasspointPreference(true);
                } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                    refreshPasspointPreference(false);
                }
            }
        }
    };
}
