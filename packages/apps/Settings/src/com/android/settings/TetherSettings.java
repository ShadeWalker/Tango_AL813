/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.widget.TextView;

import com.android.settings.TetherService;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.wifi.WifiApEnabler;

import com.mediatek.bluetooth.BluetoothDun;
import com.mediatek.settings.TetherSettingsExt;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener {
    private static final String TAG = "TetherSettings";

    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String ENABLE_BLUETOOTH_TETHERING = "enable_bluetooth_tethering";
    private static final String TETHER_CHOICE = "TETHER_TYPE";

    private static final int DIALOG_AP_SETTINGS = 1;

    private SwitchPreference mUsbTether;

    private WifiApEnabler mWifiApEnabler;
    private Preference mEnableWifiAp;

    private SwitchPreference mBluetoothTether;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;

    private String[] mWifiRegexs;

    private String[] mBluetoothRegexs;
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference<BluetoothPan>();

    private static final String WIFI_AP_SSID_AND_SECURITY = "wifi_ap_ssid_and_security";
    private static final int CONFIG_SUBTEXT = R.string.wifi_tether_configure_subtext;

    private String[] mSecurityType;
    private Preference mCreateNetwork;

    private WifiApDialog mDialog;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConfig = null;
    private UserManager mUm;

    private boolean mUsbConnected;
    private boolean mMassStorageActive;

    private boolean mBluetoothEnableForTether;

    public static final int INVALID             = -1;
    public static final int WIFI_TETHERING      = 0;
    public static final int USB_TETHERING       = 1;
    public static final int BLUETOOTH_TETHERING = 2;

    /* One of INVALID, WIFI_TETHERING, USB_TETHERING or BLUETOOTH_TETHERING */
    private int mTetherChoice = INVALID;

    /* Stores the package name and the class name of the provisioning app */
    private String[] mProvisionApp;
    private static final int PROVISION_REQUEST = 0;


    /// M:  @{
    private boolean mUsbTethering = false;
    private boolean mUsbTetherCheckEnable = false;
    private boolean mUsbConfigured;
    /** M: for bug solving, ALPS00331223 */
    private boolean mUsbUnTetherDone = true; // must set to "true" for lauch setting case after startup
    private boolean mUsbTetherDone = true; // must set to "true" for lauch setting case after startup
    private boolean mUsbTetherFail = false; // must set to "false" for lauch setting case after startup

    private boolean mUsbHwDisconnected;
    private boolean mIsPcKnowMe = true;
    private static final String USB_DATA_STATE = "mediatek.intent.action.USB_DATA_STATE";

    private TetherSettingsExt mTetherSettingsExt;
    /// @}
    
    private boolean mUnavailable;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if(icicle != null) {
            mTetherChoice = icicle.getInt(TETHER_CHOICE);
        }
        addPreferencesFromResource(R.xml.tether_prefs);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)) {
            mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }

        mTetherSettingsExt = new TetherSettingsExt(getActivity());
        mTetherSettingsExt.onCreate(getPreferenceScreen());
        final Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(activity.getApplicationContext(), mProfileServiceListener,
                    BluetoothProfile.PAN);
        }

        mEnableWifiAp =
                (Preference) findPreference(ENABLE_WIFI_AP);
        Preference wifiApSettings = findPreference(WIFI_AP_SSID_AND_SECURITY);
        mUsbTether = (SwitchPreference) findPreference(USB_TETHER_SETTINGS);
        /*HQ_yuankangbo 2015-07-30 modify for usb network share */
        mUsbTether.setOnPreferenceChangeListener(this);
        mBluetoothTether = (SwitchPreference) findPreference(ENABLE_BLUETOOTH_TETHERING);
        mBluetoothTether.setOnPreferenceChangeListener(this);//modify by wangmingyue for HQ01308106
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = cm.getTetherableUsbRegexs();
        mWifiRegexs = cm.getTetherableWifiRegexs();
        mBluetoothRegexs = cm.getTetherableBluetoothRegexs();

        final boolean usbAvailable = mUsbRegexs.length != 0;
        final boolean wifiAvailable = mWifiRegexs.length != 0;
        final boolean bluetoothAvailable = mBluetoothRegexs.length != 0;

        if (!usbAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(mUsbTether);
        }

        if (wifiAvailable && !Utils.isMonkeyRunning()) {
            initWifiTethering();
        } else {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(wifiApSettings);
        }

         /// M: import,set mWifiApEnabler = null to disble Google default
         mWifiApEnabler = null;
         /// M: import, remove default wifi item, and must call after the initWifiTeterhing()
         mTetherSettingsExt.updateWifiTether(mEnableWifiAp,wifiApSettings,wifiAvailable);
        //HQ_wuhuihui_20150604 modified for HQ01175878
        if (!bluetoothAvailable  && null != mBluetoothTether) {/* HQ_ChenWenshuai 2015-07-18 modified for HQ01264390 begin */
            getPreferenceScreen().removePreference(mBluetoothTether);
        } else {
            BluetoothPan pan = mBluetoothPan.get();
            if (pan != null && pan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
            } else {
                mBluetoothTether.setChecked(false);
            }
            mTetherSettingsExt.updateBtTetherState(mBluetoothTether);
        }

        mProvisionApp = getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(TETHER_CHOICE, mTetherChoice);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void initWifiTethering() {
        final Activity activity = getActivity();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiConfig = mWifiManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);

        mCreateNetwork = findPreference(WIFI_AP_SSID_AND_SECURITY);

        if (mWifiConfig == null) {
            final String s = activity.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    s, mSecurityType[WifiApDialog.OPEN_INDEX]));
        } else {
            int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    mWifiConfig.SSID,
                    mSecurityType[index]));
        }
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothPan.set((BluetoothPan) proxy);
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothPan.set(null);
        }
    };

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            final Activity activity = getActivity();
            mDialog = new WifiApDialog(activity, this, mWifiConfig);
            return mDialog;
        }

        return null;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            /// M: 
            Xlog.d(TAG, "TetherChangeReceiver - onReceive, action is " + action);

            if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);

                /** M: for bug solving, ALPS00331223 */
                mUsbUnTetherDone = intent.getBooleanExtra("UnTetherDone", false);
                mUsbTetherDone = intent.getBooleanExtra("TetherDone", false);
                mUsbTetherFail = intent.getBooleanExtra("TetherFail", false);

                /// M: print log
                Xlog.d(TAG, "mUsbUnTetherDone? :" + mUsbUnTetherDone + " , mUsbTetherDonel? :" +
                    mUsbTetherDone + " , tether fail? :" + mUsbTetherFail);
                updateState(available.toArray(new String[available.size()]),
                        active.toArray(new String[active.size()]),
                        errored.toArray(new String[errored.size()]));
            } else if (action.equals(Intent.ACTION_MEDIA_SHARED)) {
                mMassStorageActive = true;
                updateState();
            } else if (action.equals(Intent.ACTION_MEDIA_UNSHARED)) {
                mMassStorageActive = false;
                updateState();
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                /// M: @{
                mUsbConfigured = intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false);
                mUsbHwDisconnected = intent.getBooleanExtra("USB_HW_DISCONNECTED", false);
                mIsPcKnowMe = intent.getBooleanExtra("USB_IS_PC_KNOW_ME", true);

                Xlog.d(TAG, "TetherChangeReceiver - ACTION_USB_STATE mUsbConnected: " + mUsbConnected +
                        ", mUsbConfigured:  " + mUsbConfigured + ", mUsbHwDisconnected: " + mUsbHwDisconnected);
                /// @}
                updateState();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (mBluetoothEnableForTether) {
                    switch (intent
                            .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        case BluetoothAdapter.STATE_ON:
                            BluetoothPan bluetoothPan = mBluetoothPan.get();
                            if (bluetoothPan != null) {
                                bluetoothPan.setBluetoothTethering(true);
                                mBluetoothEnableForTether = false;
                            }
                            /// M: @{
                            BluetoothDun bluetoothDun = mTetherSettingsExt.BluetoothDunGetProxy();
                            if (bluetoothDun != null) {
                                bluetoothDun.setBluetoothTethering(true);
                                mBluetoothEnableForTether = false;
                            }
                            /// @}
                            break;

                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.ERROR:
                            mBluetoothEnableForTether = false;
                            break;

                        default:
                            // ignore transition states
                    }
                }
                updateState();
            }
         /// M: add
         onReceiveExt(action, intent);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        if (mUnavailable) {
            TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
            getListView().setEmptyView(emptyView);
            if (emptyView != null) {
                emptyView.setText(R.string.tethering_settings_not_available);
            }
            return;
        }

        final Activity activity = getActivity();

        mMassStorageActive = mTetherSettingsExt.isUMSEnabled();
        Xlog.d(TAG, "mMassStorageActive = " + mMassStorageActive);
        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        Intent intent = activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(mTetherChangeReceiver, filter);
        
        mTetherSettingsExt.onStart(activity, mTetherChangeReceiver);

        if (intent != null) mTetherChangeReceiver.onReceive(activity, intent);
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(this);
            mWifiApEnabler.resume();
        }

        updateState();
        
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mUnavailable) {
            return;
        }
        getActivity().unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(null);
            mWifiApEnabler.pause();
        }
    }

    private void updateState() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] available = cm.getTetherableIfaces();
        String[] tethered = cm.getTetheredIfaces();
        String[] errored = cm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(String[] available, String[] tethered,
            String[] errored) {
        /// M: need put firstly @{
        if (updateStateExt(available,tethered,errored)) {
            return;
        }
        // @}

        updateUsbState(available, tethered, errored);
        updateBluetoothState(available, tethered, errored);

        if (!Utils.isMonkeyRunning()) {
            mTetherSettingsExt.updateIpv6Preference(mUsbTether, mBluetoothTether, mWifiManager);
        }
    }

    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean usbAvailable = mUsbConnected && !mMassStorageActive;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        for (String s : available) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = cm.getLastTetherError(s);
                    }
                }
            }
        }
        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        boolean usbErrored = false;
        for (String s: errored) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }

		// M: add for IPV4/IPV6 , must return the usbError
		usbError = mTetherSettingsExt.getUSBErrorCode(available, tethered,
				mUsbRegexs);
    
        Xlog.d(TAG, "updateUsbState - usbTethered : " + usbTethered + " usbErrored: " +
            usbErrored + " usbAvailable: " + usbAvailable);

        if (usbTethered) {
            Xlog.d(TAG, "updateUsbState: usbTethered ! mUsbTether checkbox setEnabled & checked ");
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(true);
            /// M: set usb tethering to false @{
            final String summary = getString(R.string.usb_tethering_active_subtext);
            //modified by maolikui at 2015-12-02 start
            //mTetherSettingsExt.updateUSBPrfSummary(mUsbTether, summary,usbTethered,usbAvailable);
            mUsbTether.setSummary(R.string.usb_tethering_active_subtext);
            //modified by maolikui at 2015-12-02 start
            mUsbTethering = false;
            mTetherSettingsExt.updateUsbTypeListState(false);
            Xlog.d(TAG, "updateUsbState - usbTethered - mUsbTetherCheckEnable: "
                + mUsbTetherCheckEnable);
            /// @}
        } else if (usbAvailable) {
                if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
                } else {
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                }
            mTetherSettingsExt.updateUSBPrfSummary(mUsbTether, null,usbTethered,usbAvailable);
            if (mUsbTetherCheckEnable) {
                Xlog.d(TAG, "updateUsbState - mUsbTetherCheckEnable, " +
                    "mUsbTether checkbox setEnabled, and set unchecked ");
                mUsbTether.setEnabled(true);
                mUsbTether.setChecked(false);
                /// M:
                mUsbTethering = false;
                mTetherSettingsExt.updateUsbTypeListState(true);
            }
            Xlog.d(TAG, "updateUsbState - usbAvailable - mUsbConfigured:  " + mUsbConfigured +
                    " mUsbTethering: " + mUsbTethering +
                    " mUsbTetherCheckEnable: " + mUsbTetherCheckEnable);
        } else if (usbErrored) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            /// M: set usb tethering to false
            mUsbTethering = false;
        } else if (mMassStorageActive) {
            mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            /// M: set usb tethering to false
            mUsbTethering = false;
        } else {
            if (mUsbHwDisconnected || (!mUsbHwDisconnected && !mUsbConnected && !mUsbConfigured)) {
                mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
                mUsbTether.setEnabled(false);
                mUsbTether.setChecked(false);
                mUsbTethering = false;
            } else {
                /// M: update usb state @{
                Xlog.d(TAG, "updateUsbState - else, " +
                    "mUsbTether checkbox setEnabled, and set unchecked ");
                mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
                mUsbTether.setEnabled(true);
                mUsbTether.setChecked(false);
                mUsbTethering = false;
                mTetherSettingsExt.updateUsbTypeListState(true);
                /// @}
            }
            Xlog.d(TAG, "updateUsbState- usbAvailable- mUsbHwDisconnected:" + mUsbHwDisconnected);
        }
    }

    private void updateBluetoothState(String[] available, String[] tethered,
            String[] errored) {
    	mTetherSettingsExt.getBTErrorCode(available);
        boolean bluetoothErrored = false;
        for (String s: errored) {
            for (String regex : mBluetoothRegexs) {
                if (s.matches(regex)) bluetoothErrored = true;
            }
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
            return;
        int btState = adapter.getState();
        Xlog.d(TAG,"btState = " + btState);
        if (btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_off);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
        } else {
            BluetoothPan bluetoothPan = mBluetoothPan.get();
            /// M:
            BluetoothDun bluetoothDun = mTetherSettingsExt.BluetoothDunGetProxy();
            if (btState == BluetoothAdapter.STATE_ON && 
                ((bluetoothPan != null && bluetoothPan.isTetheringOn()) ||
                (bluetoothDun != null && bluetoothDun.isTetheringOn()))) {
                mBluetoothTether.setChecked(true);
                mBluetoothTether.setEnabled(true);
                int bluetoothTethered = 0;
                if (bluetoothPan != null && bluetoothPan.isTetheringOn()) {
                    bluetoothTethered = bluetoothPan.getConnectedDevices().size();
                    Xlog.d(TAG,"bluetooth Tethered PAN devices = " + bluetoothTethered);
                }
                if (bluetoothDun != null && bluetoothDun.isTetheringOn()) {
                    bluetoothTethered += bluetoothDun.getConnectedDevices().size();
                    Xlog.d(TAG,"bluetooth tethered total devices = " + bluetoothTethered);
                }

                if (bluetoothTethered > 1) {
                    String summary = getString(
                            R.string.bluetooth_tethering_devices_connected_subtext, bluetoothTethered);
                    mTetherSettingsExt.updateBTPrfSummary(mBluetoothTether,summary);
                } else if (bluetoothTethered == 1) {
                    String summary = getString(R.string.bluetooth_tethering_device_connected_subtext);
                    mTetherSettingsExt.updateBTPrfSummary(mBluetoothTether,summary);
                } else if (bluetoothErrored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    String summary = getString(R.string.bluetooth_tethering_available_subtext);
                    mTetherSettingsExt.updateBTPrfSummary(mBluetoothTether,summary);
                }
            } else {
                mBluetoothTether.setEnabled(true);
                mBluetoothTether.setChecked(false);
                mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
            }
        }
    }


    public boolean onPreferenceChange(Preference preference, Object value) {
        /*HQ_yuankangbo 2015-07-30 modify for usb network share start*/
        if (preference == mUsbTether) {
            if (!mUsbTethering) {
                boolean newState = (boolean)value;

                /// M: update usb tethering @{
                mUsbTether.setEnabled(false);
                mTetherSettingsExt.updateUsbTypeListState(false);
                mUsbTethering = true;
                mUsbTetherCheckEnable = false;
                if (newState) {
                    mUsbTetherDone = false;
                } else {
                    mUsbUnTetherDone = false;
                }
                mUsbTetherFail = false;

                Xlog.d(TAG, "onPreferenceTreeClick - setusbTethering(" + newState +
                    ") mUsbTethering:  " + mUsbTethering);
                /// @}

                if (newState) {
                    startProvisioningIfNecessary(USB_TETHERING);
                } else {
                    if (isProvisioningNeeded(mProvisionApp)) {
                        TetherService.cancelRecheckAlarmIfNecessary(getActivity(), USB_TETHERING);
                    }
                    setUsbTethering(newState);
                }
            } else {
                return true;
            }
            //modify by wangmingyue for HQ01308106 begin
        }else if (preference == mBluetoothTether) {
            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean newState = (boolean)value;
            if (newState) {
                startProvisioningIfNecessary(BLUETOOTH_TETHERING);
            } else {
                if (isProvisioningNeeded(mProvisionApp)) {
                    TetherService.cancelRecheckAlarmIfNecessary(getActivity(), BLUETOOTH_TETHERING);
                }
                boolean errored = false;
                String [] tethered = cm.getTetheredIfaces();
                String bluetoothIface = findIface(tethered, mBluetoothRegexs);
                if (bluetoothIface != null &&
                        cm.untether(bluetoothIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    errored = true;
                }
                BluetoothPan bluetoothPan = mBluetoothPan.get();
                if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(false);
                /// M: set bluetooth tethering to false @{
                mTetherSettingsExt.updateBtDunTether(false);
                if (errored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
                }
            }
            if (!Utils.isMonkeyRunning()) {
                if (mBluetoothTether.isChecked()) {
                    mBluetoothTether.setChecked(false);
                } else {
                    mBluetoothTether.setChecked(true);
                }
                mTetherSettingsExt.updateIpv6Preference(mUsbTether, mBluetoothTether, mWifiManager);
            }
          //modify by wangmingyue for HQ01308106 end
        }else{
            mTetherSettingsExt.onPreferenceChange(preference, value);
        }
        /*HQ_yuankangbo 2015-07-30 modify for usb network share end*/
        return true;
    }

    public static boolean isProvisioningNeededButUnavailable(Context context) {
        String[] provisionApp = context.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        return (isProvisioningNeeded(provisionApp)
                && !isIntentAvailable(context, provisionApp));
    }

    private static boolean isIntentAvailable(Context context, String[] provisionApp) {
        if (provisionApp.length <  2) {
            throw new IllegalArgumentException("provisionApp length should at least be 2");
        }
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(provisionApp[0], provisionApp[1]);

        return (packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0);
    }


    private static boolean isProvisioningNeeded(String[] provisionApp) {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)
                || provisionApp == null) {
            return false;
        }
        return (provisionApp.length == 2);
    }

    private void startProvisioningIfNecessary(int choice) {
        mTetherChoice = choice;
        if (isProvisioningNeeded(mProvisionApp)) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mProvisionApp[0], mProvisionApp[1]);
            intent.putExtra(TETHER_CHOICE, mTetherChoice);
            startActivityForResult(intent, PROVISION_REQUEST);
        } else {
            startTethering();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PROVISION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                TetherService.scheduleRecheckAlarm(getActivity(), mTetherChoice);
                startTethering();
            } else {
                //BT and USB need switch turned off on failure
                //Wifi tethering is never turned on until afterwards
                switch (mTetherChoice) {
                    case BLUETOOTH_TETHERING:
                        mBluetoothTether.setChecked(false);
                        break;
                    case USB_TETHERING:
                        mUsbTether.setChecked(false);
                        break;
                }
                mTetherChoice = INVALID;
            }
        }
    }

    private void startTethering() {
        switch (mTetherChoice) {
            case WIFI_TETHERING:
                mWifiApEnabler.setSoftapEnabled(true);
                break;
            case BLUETOOTH_TETHERING:
                // turn on Bluetooth first
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    mBluetoothEnableForTether = true;
                    adapter.enable();
                    mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
                    mBluetoothTether.setEnabled(false);
                } else {
                    BluetoothPan bluetoothPan = mBluetoothPan.get();
                    if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(true);

           
                    /// M: set blue tooth dun tethering to true @{
                    mTetherSettingsExt.updateBtDunTether(true);
                    String summary = getString(R.string.bluetooth_tethering_available_subtext);
                    mTetherSettingsExt.updateBTPrfSummary(mBluetoothTether, summary);
                    /// @}
                }
                break;
            case USB_TETHERING:
                setUsbTethering(true);
                break;
            default:
                //should not happen
                break;
        }
    }

    private void setUsbTethering(boolean enabled) {
        ConnectivityManager cm =
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
         //M: move mUsbTether.setChecked(false) as CR ALPS00449289
        if (cm.setUsbTethering(enabled) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            mUsbTether.setChecked(false);
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            return;
        }
        mUsbTether.setSummary("");
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        if (preference == mUsbTether) {
            if (!mUsbTethering) {
                boolean newState = mUsbTether.isChecked();

                /// M: update usb tethering @{
                mUsbTether.setEnabled(false);
                mTetherSettingsExt.updateUsbTypeListState(false);
                mUsbTethering = true;
                mUsbTetherCheckEnable = false;
                if (newState) {
                    mUsbTetherDone = false;
                } else {
                    mUsbUnTetherDone = false;
                }
                mUsbTetherFail = false;

                Xlog.d(TAG, "onPreferenceTreeClick - setusbTethering(" + newState +
                    ") mUsbTethering:  " + mUsbTethering);
                /// @}

                if (newState) {
                    startProvisioningIfNecessary(USB_TETHERING);
                } else {
                    if (isProvisioningNeeded(mProvisionApp)) {
                        TetherService.cancelRecheckAlarmIfNecessary(getActivity(), USB_TETHERING);
                    }
                    setUsbTethering(newState);
                }
            } else {
                return true;
            }
        } else if (preference == mBluetoothTether) {
            boolean bluetoothTetherState = mBluetoothTether.isChecked();

            if (bluetoothTetherState) {
                startProvisioningIfNecessary(BLUETOOTH_TETHERING);
            } else {
                if (isProvisioningNeeded(mProvisionApp)) {
                    TetherService.cancelRecheckAlarmIfNecessary(getActivity(), BLUETOOTH_TETHERING);
                }
                boolean errored = false;

                String [] tethered = cm.getTetheredIfaces();
                String bluetoothIface = findIface(tethered, mBluetoothRegexs);
                if (bluetoothIface != null &&
                        cm.untether(bluetoothIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    errored = true;
                }

                BluetoothPan bluetoothPan = mBluetoothPan.get();
                if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(false);
                
                /// M: set bluetooth tethering to false @{
                mTetherSettingsExt.updateBtDunTether(false);
 
                if (errored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
                }
            }
            if (!Utils.isMonkeyRunning()) {
                mTetherSettingsExt.updateIpv6Preference(mUsbTether, mBluetoothTether, mWifiManager);
            }
        } else if (preference == mCreateNetwork) {
            showDialog(DIALOG_AP_SETTINGS);
        }
        mTetherSettingsExt.onPreferenceClick(preference);
        return super.onPreferenceTreeClick(screen, preference);
    }

    private static String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up
                 * else restart with new config
                 * TODO: update config on a running access point when framework support is added
                 */
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                    mWifiManager.setWifiApEnabled(null, false);
                    mWifiManager.setWifiApEnabled(mWifiConfig, true);
                } else {
                    mWifiManager.setWifiApConfiguration(mWifiConfig);
                }
                int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
                mCreateNetwork.setSummary(String.format(getActivity().getString(CONFIG_SUBTEXT),
                        mWifiConfig.SSID,
                        mSecurityType[index]));
            }
        }
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_tether;
    }

    /**
     * Checks whether this screen will have anything to show on this device. This is called by
     * the shortcut picker for Settings shortcuts (home screen widget).
     * @param context a context object for getting a system service.
     * @return whether Tether & portable hotspot should be shown in the shortcuts picker.
     */
    public static boolean showInShortcuts(Context context) {
        final ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final boolean isSecondaryUser = UserHandle.myUserId() != UserHandle.USER_OWNER;
        return !isSecondaryUser && cm.isTetheringSupported();
    }

private boolean updateStateExt(String[] available, String[] tethered,
            String[] errored) {
        Xlog.d(TAG, "=======> updateState - mUsbConnected: " + mUsbConnected +
                ", mUsbConfigured:  " + mUsbConfigured + ", mUsbHwDisconnected: " +
                mUsbHwDisconnected + ", checked: " + mUsbTether.isChecked() +
                ", mUsbUnTetherDone: " + mUsbUnTetherDone + ", mUsbTetherDone: " +
                mUsbTetherDone + ", tetherfail: " + mUsbTetherFail + ", mIsPcKnowMe: " + mIsPcKnowMe);

        /** M: for bug solving, ALPS00331223 */
        // turn on tethering case
        if (mUsbTether.isChecked()) {
            if (mUsbConnected && mUsbConfigured && !mUsbHwDisconnected) {
                if (mUsbTetherFail || mUsbTetherDone || !mIsPcKnowMe) {
                    mUsbTetherCheckEnable = true;
                }
            } else {
                mUsbTetherCheckEnable = false ;
            }
        } else { // turn off tethering case or first launch case
            if (mUsbConnected && !mUsbHwDisconnected) {
                if (mUsbUnTetherDone || mUsbTetherFail) {
                    mUsbTetherCheckEnable = true;
                }
            } else {
                mUsbTetherCheckEnable = false ;
            }
        }
        
        return false;
}

private void onReceiveExt(String action,Intent intent) {
          if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                /// M: update ipv4 & ipv6 preference @{
                int state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                if (state == WifiManager.WIFI_AP_STATE_ENABLED || state == WifiManager.WIFI_AP_STATE_DISABLED) {
                    if (!Utils.isMonkeyRunning()) {
                        mTetherSettingsExt.updateIpv6Preference(
                                mUsbTether, mBluetoothTether, mWifiManager);
                    }
                }
                /// @}
            } else if (action.equals(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED)
                        || action.equals(BluetoothDun.STATE_CHANGED_ACTION)) {
                updateState();
            } else if (mTetherSettingsExt.ACTION_WIFI_TETHERED_SWITCH.equals(action)) {
                if (!Utils.isMonkeyRunning()) {
                    mTetherSettingsExt.updateIpv6Preference(
                            mUsbTether, mBluetoothTether, mWifiManager);
                }
            }
    }
}
