/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.security.Credentials;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.AppListSwitchPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.mediatek.wifi.AdvancedWifiSettingsExt;

import java.util.Collection;

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_SCAN_ALWAYS_AVAILABLE = "wifi_scan_always_available";
    private static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String KEY_WIFI_ASSISTANT = "wifi_assistant";
    private static final String KEY_WIFI_DIRECT = "wifi_direct";
    private static final String KEY_WPS_PUSH = "wps_push_button";
    private static final String KEY_WPS_PIN = "wps_pin_entry";

    private WifiManager mWifiManager;
    private NetworkScoreManager mNetworkScoreManager;
    private AppListSwitchPreference mWifiAssistantPreference;
    //M: add for mtk feature and bug fix
    private AdvancedWifiSettingsExt mAdvancedWifiSettingsExt;
    //M: add for auto join feature
    private static final String KEY_AUTO_JOIN = "wifi_ap_auto_join_available";
	
	/* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,begin */
	private Preference wpsPushPref;
	private Preference wpsPinPref;
	/* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,end */

    private IntentFilter mFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
			/* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,begin */
            String action = intent.getAction();
            if (action.equals(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION) ||
                action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                refreshWifiInfo();
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
				handleWifiStateChanged(intent.getIntExtra(
										   WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
			}
			/*HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,end */
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
	/*add by wanghui for al812 begin,HQ01341924*/
	PreferenceScreen screen = getPreferenceScreen();
	Preference wifiDirect = (Preference) screen.findPreference(KEY_WIFI_DIRECT);
	screen.removePreference(wifiDirect);
	/*add by wanghui for al812 end,HQ01341924*/
        mAdvancedWifiSettingsExt = new AdvancedWifiSettingsExt(this);
        //M: add for mtk feature and bug fix
        mAdvancedWifiSettingsExt.onCreate();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);//HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mNetworkScoreManager =
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE);
        //M: add for mtk feature and bug fix
        mAdvancedWifiSettingsExt.onActivityCreated(getContentResolver());
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        getActivity().registerReceiver(mReceiver, mFilter);
        refreshWifiInfo();
        //M: add for mtk feature and bug fix
        mAdvancedWifiSettingsExt.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
        //M: add for mtk feature and bug fix
        mAdvancedWifiSettingsExt.onPause();
    }

    private void initPreferences() {
	/*add by wanghui for al812 begin,HQ01341924*/
	//PreferenceScreen screen = getPreferenceScreen();
	//Preference wifiDirect = (Preference) screen.findPreference(KEY_WIFI_DIRECT);
	//screen.removePreference(wifiDirect);
	/*add by wanghui for al812 end,HQ01341924*/
        SwitchPreference notifyOpenNetworks =
            (SwitchPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());
		/*add by wanghui for al812  ,HQ01337042,HQ01315280*/
		notifyOpenNetworks.setOnPreferenceChangeListener(this);

        SwitchPreference scanAlwaysAvailable =
            (SwitchPreference) findPreference(KEY_SCAN_ALWAYS_AVAILABLE);
        scanAlwaysAvailable.setChecked(Global.getInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1);
		/*add by wanghui for al812  ,HQ01337042,HQ01315280*/
		scanAlwaysAvailable.setOnPreferenceChangeListener(this);

        Intent intent = new Intent(Credentials.INSTALL_AS_USER_ACTION);
        intent.setClassName("com.android.certinstaller",
                "com.android.certinstaller.CertInstallerMain");
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, android.os.Process.WIFI_UID);
        Preference pref = findPreference(KEY_INSTALL_CREDENTIALS);
        pref.setIntent(intent);

        final Context context = getActivity();
        mWifiAssistantPreference = (AppListSwitchPreference) findPreference(KEY_WIFI_ASSISTANT);
        Collection<NetworkScorerAppData> scorers =
                NetworkScorerAppManager.getAllValidScorers(context);
        if (UserHandle.myUserId() == UserHandle.USER_OWNER && !scorers.isEmpty()) {
            mWifiAssistantPreference.setOnPreferenceChangeListener(this);
            initWifiAssistantPreference(scorers);
        } else if (mWifiAssistantPreference != null) {
            getPreferenceScreen().removePreference(mWifiAssistantPreference);
        }
	/*delete by wanghui for al812 begin,HQ01341924*/
        /*Intent wifiDirectIntent = new Intent(context,
                com.android.settings.Settings.WifiP2pSettingsActivity.class);
        Preference wifiDirectPref = findPreference(KEY_WIFI_DIRECT);
        wifiDirectPref.setIntent(wifiDirectIntent);*/
	/*delete by wanghui for al812 end,HQ01341924*/

        // WpsDialog: Create the dialog like WifiSettings does.
        //delete by wanghui for al812
        wpsPushPref = findPreference(KEY_WPS_PUSH);
        wpsPushPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference arg0) {
                    WpsFragment wpsFragment = new WpsFragment(WpsInfo.PBC);
                    wpsFragment.show(getFragmentManager(), KEY_WPS_PUSH);
                    return true;
                }
        });

        // WpsDialog: Create the dialog like WifiSettings does.
        //delete by wanghui for al812
        wpsPinPref = findPreference(KEY_WPS_PIN);
        wpsPinPref.setOnPreferenceClickListener(new OnPreferenceClickListener(){
                public boolean onPreferenceClick(Preference arg0) {
                    WpsFragment wpsFragment = new WpsFragment(WpsInfo.DISPLAY);
                    wpsFragment.show(getFragmentManager(), KEY_WPS_PIN);
                    return true;
                }
        });
		
		/* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,begin */
		wpsPushPref.setEnabled(mWifiManager.isWifiEnabled());
		wpsPinPref.setEnabled(mWifiManager.isWifiEnabled());
		/* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,end */

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(context)) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
			//modify by wanghui resume the code,because of the idea is error
            sleepPolicyPref.setOnPreferenceChangeListener(this);
                int value = Settings.Global.getInt(getContentResolver(),
                        Settings.Global.WIFI_SLEEP_POLICY,
                        Settings.Global.WIFI_SLEEP_POLICY_NEVER);
                String stringValue = String.valueOf(value);
                sleepPolicyPref.setValue(stringValue);
                updateSleepPolicySummary(sleepPolicyPref, stringValue);
            }            
    }

    private void initWifiAssistantPreference(Collection<NetworkScorerAppData> scorers) {
        int count = scorers.size();
        String[] packageNames = new String[count];
        int i = 0;
        for (NetworkScorerAppData scorer : scorers) {
            packageNames[i] = scorer.mPackageName;
            i++;
        }
        mWifiAssistantPreference.setPackageNames(packageNames,
                mNetworkScoreManager.getActiveScorerPackage());
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();
		//boolean  ischeck = ((SwitchPreference) preference).isChecked();
		/*modify by wanghui for al812  begin,HQ01337042,HQ01315280*/
		//Log.d("wanghui","key:" + key);
		//Log.d("wanghui","ischeck:" + ischeck);
		//Log.d("wanghui","getContentResolver:" + getContentResolver());

        /*if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {	
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_SCAN_ALWAYS_AVAILABLE.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else*/ 
        /*modify by wanghui for al812  end,HQ01337042,HQ01315280*/
        if(KEY_AUTO_JOIN.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_AUTO_JOIN,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
        String key = preference.getKey();

        if (KEY_WIFI_ASSISTANT.equals(key)) {
            NetworkScorerAppData wifiAssistant =
                    NetworkScorerAppManager.getScorer(context, (String) newValue);
            if (wifiAssistant == null) {
                mNetworkScoreManager.setActiveScorer(null);
                return true;
            }

            Intent intent = new Intent();
            if (wifiAssistant.mConfigurationActivityClassName != null) {
                // App has a custom configuration activity; launch that.
                // This custom activity will be responsible for launching the system
                // dialog.
                intent.setClassName(wifiAssistant.mPackageName,
                        wifiAssistant.mConfigurationActivityClassName);
            } else {
                // Fall back on the system dialog.
                intent.setAction(NetworkScoreManager.ACTION_CHANGE_ACTIVE);
                intent.putExtra(NetworkScoreManager.EXTRA_PACKAGE_NAME,
                        wifiAssistant.mPackageName);
            }

            startActivity(intent);
            // Don't update the preference widget state until the child activity returns.
            // It will be updated in onResume after the activity finishes.
            return false;
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
		/*add  by wanghui for al812  begin,HQ01337042,HQ01315280*/
		if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
        boolean checked = ((Boolean) newValue).booleanValue();
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    (checked ? 1 : 0));
			return true;
        } else if (KEY_SCAN_ALWAYS_AVAILABLE.equals(key)) {
        boolean checked = ((Boolean) newValue).booleanValue();
            Global.putInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    checked ? 1 : 0);
			return true;
        }
		/*add by wanghui for al812  end,HQ01337042,HQ01315280*/

        return true;
    }

    private void refreshWifiInfo() {
        final Context context = getActivity();
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : context.getString(R.string.status_unavailable));
        wifiMacAddressPref.setSelectable(false);

        // M: remove default IP address solution
   }
   
   /* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,begin */
   private void handleWifiStateChanged(int state) {
	   Log.d(TAG, "handleWifiStateChanged, state = " + state);
	   switch (state) {
		   case WifiManager.WIFI_STATE_ENABLING:
			   wpsPushPref.setEnabled(true);
			   wpsPinPref.setEnabled(true);
			   break;
		   case WifiManager.WIFI_STATE_DISABLING:
			   wpsPushPref.setEnabled(false);
			   wpsPinPref.setEnabled(false);
			   break;
		   default:
			   ;
	   }
   }
   /* HQ01337066,wanghui,modify to disable the wps menu when the wlan is disabled,end */

    /* Wrapper class for the WPS dialog to properly handle life cycle events like rotation. */
    public static class WpsFragment extends DialogFragment {
        private static int mWpsSetup;

        // Public default constructor is required for rotation.
        public WpsFragment() {
            super();
        }

        public WpsFragment(int wpsSetup) {
            super();
            mWpsSetup = wpsSetup;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new WpsDialog(getActivity(), mWpsSetup);
        }
    }

}
