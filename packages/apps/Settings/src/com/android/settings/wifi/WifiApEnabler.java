/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
/*add by lihaizhou for HQ01592204 at 20151228 by begin*/
import android.os.SystemProperties;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.Toast;
/*add by lihaizhou for HQ01592204 at 20151228 by end*/
import com.android.settings.R;
import com.android.settings.TetherService;
import com.android.settings.widget.SwitchBar;
import com.mediatek.settings.ext.IWifiExt;
import com.mediatek.settings.ext.IWfcSettingsExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.TetherSettingsExt;
import com.mediatek.settings.UtilsExt;
import com.mediatek.xlog.Xlog;
import android.util.Log;
import java.util.ArrayList;

public class WifiApEnabler extends Fragment
        implements SwitchBar.OnSwitchChangeListener,
                   Preference.OnPreferenceChangeListener {
    static final String TAG = "WifiApEnabler";
    private Context mContext;
    private SwitchPreference mSwitch;
    private CharSequence mOriginalSummary;

    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;
    private TetherSettingsExt mTetherSettingsEx;
    private static final int WIFI_IPV4 = 0x0f;
    private static final int WIFI_IPV6 = 0xf0;
    ConnectivityManager mCm;
    private String[] mWifiRegexs;
    /* modify by wanghui Indicates if we have to wait for WIFI_STATE_CHANGED intent */
    private boolean mWaitForWifiStateChange;

    /// M: @{
    private static final String WIFI_SWITCH_SETTINGS = "wifi_tether_settings";
    private static SharedPreferences wifiApSecurity;//add by lihaizhou for HQ01592204 at 20151228
    private static final int INVALID             = -1;
    private static final int WIFI_TETHERING      = 0;
    IWifiExt mExt;
    IWfcSettingsExt mWfcSettingsExt;
    private SwitchBar mSwitchBar;
    private boolean mStateMachineEvent;

    private int mTetherChoice = INVALID;
    /* Stores the package name and the class name of the provisioning app */
    private String[] mProvisionApp;
    private static final int PROVISION_REQUEST = 0;
    private static final String ACTION_WIFI_TETHERED_SWITCH = "action.wifi.tethered_switch";
    /// @}

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
         //modified here by wanghui begin
            }else if(WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)){
                  if (mWaitForWifiStateChange == true) {
                       handleWifiStateChanged(intent.getIntExtra(
                       WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
                   }
        //modified here by wanghui end
            }else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                if (available != null && active != null && errored != null) {
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                        updateTetherStateForIpv6(available.toArray(), active.toArray(), errored.toArray());
                    } else {
                        updateTetherState(available.toArray(), active.toArray(), errored.toArray());
                    }
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiSwitch();
            }
        }
    };

    public WifiApEnabler(Context context, SwitchPreference switchPreference) {
        mContext = context;
        mSwitch = switchPreference;
        mOriginalSummary = switchPreference != null ? switchPreference.getSummary() : "";
        if (switchPreference != null) {
            switchPreference.setPersistent(false);
        }
		//add by wanghui for al812
        mWaitForWifiStateChange = false;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        //modified here by wanghui for al812
	mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        ///M: fix not commit WifiApEnabler this fragment, can't use getActivity() or onActivityResult
        commitFragment();

        ///M: WFC  @ {
        mWfcSettingsExt = UtilsExt.getWfcSettingsExtPlugin(mContext);
        /// @}
    }

    ///M: fix not commit WifiApEnabler this fragment, can't use getActivity() or onActivityResult
    public WifiApEnabler() {
        
    }
    
    public WifiApEnabler(SwitchBar switchBar, Context context) {
        mContext = context;
        mSwitchBar = switchBar;
        setupSwitchBar();
        mWaitForWifiStateChange = false;
        init(context);
        ///M: fix not commit WifiApEnabler this fragment, can't use getActivity() or onActivityResult
        commitFragment();
        ///M: WFC  @ {
        mWfcSettingsExt = UtilsExt.getWfcSettingsExtPlugin(mContext);
        /// @}
    }
    
    public void setupSwitchBar() {
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.show();
    }

    public void teardownSwitchBar() {
        mSwitchBar.removeOnSwitchChangeListener(this);
        mSwitchBar.hide();
    }

    public void init(Context context) {
        /// M: WifiManager memory leak @{
        //mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        /// @}
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        //modified here by wanghui for al812
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mProvisionApp = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        if (mSwitchBar != null) {
            //mMtkSwitch.setOnCheckedChangeListener(this);
        } else {
            mSwitch.setOnPreferenceChangeListener(this);
        }

        enableWifiSwitch();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);

        if (mSwitchBar != null) {
            //mMtkSwitch.setOnCheckedChangeListener(null);
        } else {
            mSwitch.setOnPreferenceChangeListener(null);
        }
    }

    private void enableWifiSwitch() {
        boolean isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        if (!isAirplaneMode) {
            setSwitchEnabled(true);
        } else {
            if (mSwitchBar == null) {
                mSwitch.setSummary(mOriginalSummary);
            }
            setSwitchEnabled(false);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        final ContentResolver cr = mContext.getContentResolver();
        int wifiSavedState = 0;
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        Log.d(TAG,"mWifiManager.getWifiState  : "+ wifiState);
        if (enable && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }
          //modified here by wanghui begin
         /**
         * Check if we have to wait for the WIFI_STATE_CHANGED intent
         * before we re-enable the Checkbox.
         */
        if (!enable) {
            try {
                wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                Log.d(TAG,"SettingNotFoundException: "+e);
            }
            
            if (wifiSavedState == 1) {
                 mWaitForWifiStateChange = true;
            }
        }
        //modified here by wanghui end
        if (mWifiManager.setWifiApEnabled(null, enable)) {
            setSwitchEnabled(false);
        } else {
            if (mSwitchBar == null && mSwitch != null) {
                mSwitch.setSummary(R.string.wifi_error);
            }
        }

        /**
         *  If needed, restore Wifi on tether disable
         */
        if (!enable) {
          //  int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                Xlog.d(TAG, "SettingNotFoundException");
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
    }

    public void updateConfigSummary(WifiConfiguration wifiConfig) {
        String s = com.mediatek.custom.CustomProperties.getString(com.mediatek.custom.CustomProperties.MODULE_WLAN, 
                    com.mediatek.custom.CustomProperties.SSID, 
                    mContext.getString(com.android.internal.R.string.wifi_tether_configure_ssid_default));
        if (mSwitchBar == null) {
            mSwitch.setSummary(String.format(mContext.getString(R.string.wifi_tether_enabled_subtext),
                    (wifiConfig == null) ? s : wifiConfig.SSID));
        }
    }

    private void updateTetherStateForIpv6(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        int wifiErrorIpv4 = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        int wifiErrorIpv6 = ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR;
        for (Object o : available) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    if (wifiErrorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        wifiErrorIpv4 = (mCm.getLastTetherError(s) & WIFI_IPV4);
                    }
                    if (wifiErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                        wifiErrorIpv6 = (mCm.getLastTetherError(s) & WIFI_IPV6);
                    }
                }
            }
        }

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiTethered = true;
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                        if (wifiErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            wifiErrorIpv6 = (mCm.getLastTetherError(s) & WIFI_IPV6);
                        }
                    }
                }
            }
        }

        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiErrored = true;
                }
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
            String s = mContext.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
            String tetheringActive = String.format(
                mContext.getString(R.string.wifi_tether_enabled_subtext),
                (wifiConfig == null) ? s : wifiConfig.SSID);

            if (mTetherSettingsEx != null && mSwitchBar == null) {
                mSwitch.setSummary(tetheringActive + 
                        mTetherSettingsEx.getIPV6String(wifiErrorIpv4, wifiErrorIpv6));
            }
        } else if (wifiErrored) {
            if (mSwitchBar == null) {
                mSwitch.setSummary(R.string.wifi_error);
            }
        }
    }


    /**
     * set the TetherSettings.
     * @param TetherSettings
     * @return void.
     */
    public void setTetherSettings(TetherSettingsExt tetherSettingsEx) {
        mTetherSettingsEx = tetherSettingsEx;
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiTethered = true;
                }
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiErrored = true;
                }
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
        } else if (wifiErrored) {
            if (mSwitchBar == null) {
                mSwitch.setSummary(R.string.wifi_error);
            }
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
		/*add by lihaizhou for HQ01592204 at 20151224 by begin*/
		 if("1".equals(SystemProperties.get("ro.sys.westeurope.wifi.hotspot"))){
		 wifiApSecurity = mContext.getSharedPreferences("wifi_ap_security", mContext.MODE_PRIVATE);
		 boolean isSecurityNone = wifiApSecurity.getBoolean("wifiSecurityNone", false);
		 if(isSecurityNone)
		 {
		    Toast.makeText(mContext, R.string.wifi_security_tip,Toast.LENGTH_LONG).show();
		 }
		 }
		/*add by lihaizhou for HQ01592204 at 20151224 by end*/
                setSwitchEnabled(false);
                setStartTime(false);
                if (mSwitchBar == null) {
                    mSwitch.setSummary(R.string.wifi_tether_starting);
                }
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether
                 * broadcast notice
                 */
                long eableEndTime = System.currentTimeMillis();
                Xlog.i("WifiHotspotPerformanceTest", "[Performance test][Settings][wifi hotspot] wifi hotspot turn on end ["+ eableEndTime +"]");
                setSwitchChecked(true);
                setSwitchEnabled(true);
                setStartTime(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                setSwitchChecked(false);
                setSwitchEnabled(false);
                if (mSwitchBar == null) {
                    Xlog.d(TAG, "wifi_stopping");
                    mSwitch.setSummary(R.string.wifi_tether_stopping);
                }
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                long disableEndTime = System.currentTimeMillis();
                Xlog.i("WifiHotspotPerformanceTest", "[Performance test][Settings][wifi hotspot] wifi hotspot turn off end ["+ disableEndTime +"]");
                setSwitchChecked(false);
                //setSwitchEnabled(true);
                if (mSwitchBar == null) {
                    mSwitch.setSummary(mOriginalSummary);
                }
		Log.d(TAG,"WIFI_AP_STATE_DISABLED mWaitForWifiStateChange:"+mWaitForWifiStateChange);
                if (mWaitForWifiStateChange == false) {
                    enableWifiSwitch();  //modified here by wanghui for  al812
                }

                break;
            default:
	       if (mWaitForWifiStateChange == false) {
                   enableWifiSwitch();
                 }
                break;
        }
    }
//modified here by wanghui begin
       private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
		Log.d(TAG,"handleWifiStateChanged mWaitForWifiStateChange: "+mWaitForWifiStateChange);
                enableWifiSwitch();
                mWaitForWifiStateChange = false;
                break;
            default:
        }
    }
//modified here by wanghui end
    private void setSwitchChecked(boolean checked) {
        mStateMachineEvent = true;
        if (mSwitchBar != null) {
            mSwitchBar.setChecked(checked);
        } else {
            mSwitch.setChecked(checked);
        }
        sendBroadcast(); // M: ALPS01831234
        Xlog.d(TAG, "setSwitchChecked checked = " + checked);
        mStateMachineEvent = false;
    }
    private void setSwitchEnabled(boolean enabled) {
        mStateMachineEvent = true;
        if (mSwitchBar != null) {
            mSwitchBar.setEnabled(enabled);
        } else {
            if (mSwitch != null) {
                mSwitch.setEnabled(enabled);
            }
        }
        mStateMachineEvent = false;
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference.getKey().equals(WIFI_SWITCH_SETTINGS)) {
            sendBroadcast(); // M: ALPS01831234
            boolean isChecked =  (Boolean) value;
            Xlog.d(TAG,"onPreferenceChange, isChecked:" + isChecked);
            if (isChecked) {
                startProvisioningIfNecessary(WIFI_TETHERING);
            } else {
                if (isProvisioningNeeded()) {
                    TetherService.cancelRecheckAlarmIfNecessary(mContext, WIFI_TETHERING);
                }
                setSoftapEnabled(false);
            }
        }
        return true;
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        sendBroadcast(); // M: ALPS01831234
        //Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return;
        }
        Xlog.d(TAG,"onSwitchChanged, isChecked:" + isChecked);
        if (isChecked) {
            startProvisioningIfNecessary(WIFI_TETHERING);
        } else {
            setSoftapEnabled(false);
        }
    }

    boolean isProvisioningNeeded() {
        return mProvisionApp != null ? mProvisionApp.length == 2 : false;
    }
    private void startProvisioningIfNecessary(int choice) {
        mTetherChoice = choice;
        if (isProvisioningNeeded()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mProvisionApp[0], mProvisionApp[1]);
            startActivityForResult(intent, PROVISION_REQUEST);
            Xlog.d(TAG,"startProvisioningIfNecessary, startActivityForResult");
        } else {
            startTethering();
        }
    }
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PROVISION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                startTethering();
            } 
        }
    }
    private void startTethering() {
        if (mTetherChoice == WIFI_TETHERING) {
            Xlog.d(TAG,"startTethering, setSoftapEnabled");
            //M:WFC : PLugin to show alert on enabling hotpspot
            if (!mWfcSettingsExt.showWfcTetheringAlertDialog(mContext)) {
                Xlog.d(TAG,"startTethering, setSoftapEnabled continued");
                setSoftapEnabled(true);
            }
        }
    }
    private void setStartTime(boolean enable) {
        long startTime = Settings.System.getLong(mContext.getContentResolver(),Settings.System.WIFI_HOTSPOT_START_TIME,
                            Settings.System.WIFI_HOTSPOT_DEFAULT_START_TIME);
        if (enable) {
            if (startTime == Settings.System.WIFI_HOTSPOT_DEFAULT_START_TIME) {
                Settings.System.putLong(mContext.getContentResolver(),Settings.System.WIFI_HOTSPOT_START_TIME,
                         System.currentTimeMillis());
                Xlog.d(TAG,"enable value: " + System.currentTimeMillis());
            }
        } else {
            long newValue = Settings.System.WIFI_HOTSPOT_DEFAULT_START_TIME;
            Xlog.d(TAG,"disable value: " + newValue);
            Settings.System.putLong(mContext.getContentResolver(),Settings.System.WIFI_HOTSPOT_START_TIME, newValue);
        }
    }
    
    /**
     * M: fix not commit WifiApEnabler this fragment, can't use getActivity() or onActivityResult
     */
    private void commitFragment() {
        if (mContext != null) {
            final FragmentTransaction ft = ((Activity)mContext).getFragmentManager().beginTransaction();
            ft.add(this, TAG);
            ft.commitAllowingStateLoss();            
        }

    }

    /* M: send broadcast to tell the action:  Wifi tethered switch changed
     * ALPS01831234: IPV6 Preference state is not right
     * **/
    private void sendBroadcast() {
        Intent wifiTetherIntent = new Intent(ACTION_WIFI_TETHERED_SWITCH);
        mContext.sendBroadcast(wifiTetherIntent);
    }
}
