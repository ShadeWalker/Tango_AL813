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

package com.android.settings.wifi.p2p;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.PersistentGroupInfoListener;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.mediatek.settings.FeatureOption;
import com.mediatek.wifi.WifiWpsP2pEmSettings;
//add by wanghui for al813 HQ01398537
import android.view.WindowManager;
import java.util.Locale;


/*
 * Displays Wi-fi p2p settings UI
 */
public class WifiP2pSettings extends SettingsPreferenceFragment
        implements PersistentGroupInfoListener, PeerListListener {

    private static final String TAG = "WifiP2pSettings";
    private static final boolean DBG = true;
    private static final int MENU_ID_SEARCH = Menu.FIRST;
    private static final int MENU_ID_RENAME = Menu.FIRST + 1;
    private static final int DEVICE_NAME_MAX_LENGTH_BYTES = 32;

    private final IntentFilter mIntentFilter = new IntentFilter();
    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;
    private OnClickListener mRenameListener;
    private OnClickListener mDisconnectListener;
    private OnClickListener mCancelConnectListener;
    private OnClickListener mDeleteGroupListener;
    private WifiP2pPeer mSelectedWifiPeer;
    private WifiP2pPersistentGroup mSelectedGroup;
    private String mSelectedGroupName;
    private EditText mDeviceNameText;

    private boolean mWifiP2pEnabled;
    private boolean mWifiP2pSearching;
    private int mConnectedDevices;
    private boolean mLastGroupFormed = false;

    private PreferenceGroup mPeersGroup;
    private PreferenceGroup mPersistentGroup;
	//add by wanghui for al812 wifip2p ui 
	private PreferenceGroup mMydevice;
    private Preference mThisDevicePref;

    private static final int DIALOG_DISCONNECT  = 1;
    private static final int DIALOG_CANCEL_CONNECT = 2;
    private static final int DIALOG_RENAME = 3;
    private static final int DIALOG_DELETE_GROUP = 4;

    private static final String SAVE_DIALOG_PEER = "PEER_STATE";
    private static final String SAVE_DEVICE_NAME = "DEV_NAME";
    private static final String SAVE_SELECTED_GROUP = "GROUP_NAME";

    private WifiP2pDevice mThisDevice;
    private WifiP2pDeviceList mPeers = new WifiP2pDeviceList();

    private boolean IS_RTL = Locale.getDefault().getLanguage().equals("ar")||Locale.getDefault().getLanguage().equals("fa") || Locale.getDefault().getLanguage().equals("iw") || Locale.getDefault().getLanguage().equals("he");  //HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994

    private String mSavedDeviceName;
    /// M: Wifi P2P EM
    WifiWpsP2pEmSettings mWpsP2pEmSettings;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                Log.d(TAG, "receive action: " + action);
            }

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                mWifiP2pEnabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED) == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                handleP2pStateChanged();
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                mPeers = (WifiP2pDeviceList) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                handlePeersChanged();
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager == null) return;
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
                WifiP2pInfo wifip2pinfo = (WifiP2pInfo) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (networkInfo != null && networkInfo.isConnected()) {//chenwenshuai modify for HQ01667982
                    if (DBG) Log.d(TAG, "Connected");
                } else if (mLastGroupFormed != true) {
                    //start a search when we are disconnected
                    //but not on group removed broadcast event
                    startSearch();
                }
				if(wifip2pinfo != null){//chenwenshuai modify for HQ01667982
                	mLastGroupFormed = wifip2pinfo.groupFormed;
				}
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (DBG) Log.d(TAG, "Update device info: " + mThisDevice);
                updateDevicePref();
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                if (DBG) Log.d(TAG, "Discovery state changed: " + discoveryState);
                if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    updateSearchMenu(true);
                } else {
                    updateSearchMenu(false);
                }
            } else if (WifiP2pManager.WIFI_P2P_PERSISTENT_GROUPS_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPersistentGroupInfo(mChannel, WifiP2pSettings.this);
                }
            }
        }
    };

    public WifiP2pSettings() {
        if (DBG) Log.d(TAG, "Creating WifiP2pSettings ...");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        addPreferencesFromResource(R.xml.wifi_p2p_settings);

        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PERSISTENT_GROUPS_CHANGED_ACTION);

        final Activity activity = getActivity();
        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (mWifiP2pManager != null) {
            mChannel = mWifiP2pManager.initialize(activity, getActivity().getMainLooper(), null);
            if (mChannel == null) {
                //Failure to set up connection
                Log.e(TAG, "Failed to set up connection with wifi p2p service");
                mWifiP2pManager = null;
            }
        } else {
            Log.e(TAG, "mWifiP2pManager is null !");
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DIALOG_PEER)) {
            WifiP2pDevice device = savedInstanceState.getParcelable(SAVE_DIALOG_PEER);
            mSelectedWifiPeer = new WifiP2pPeer(getActivity(), device);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DEVICE_NAME)) {
            mSavedDeviceName = savedInstanceState.getString(SAVE_DEVICE_NAME);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_SELECTED_GROUP)) {
            mSelectedGroupName = savedInstanceState.getString(SAVE_SELECTED_GROUP);
        }

        mRenameListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.setDeviceName(mChannel,
                                mDeviceNameText.getText().toString(),
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " device rename success");
                            }
                            public void onFailure(int reason) {
                                Toast.makeText(getActivity(),
                                        R.string.wifi_p2p_failed_rename_message,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        };

        //disconnect dialog listener
        mDisconnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " remove group success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " remove group fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //cancel connect dialog listener
        mCancelConnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.cancelConnect(mChannel,
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " cancel connect success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " cancel connect fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //delete persistent group dialog listener
        mDeleteGroupListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        if (mSelectedGroup != null) {
                            if (DBG) Log.d(TAG, " deleting group " + mSelectedGroup.getGroupName());
                            mWifiP2pManager.deletePersistentGroup(mChannel,
                                    mSelectedGroup.getNetworkId(),
                                    new WifiP2pManager.ActionListener() {
                                public void onSuccess() {
                                    if (DBG) Log.d(TAG, " delete group success");
                                }
                                public void onFailure(int reason) {
                                    if (DBG) Log.d(TAG, " delete group fail " + reason);
                                }
                            });
                            mSelectedGroup = null;
                        } else {
                            if (DBG) Log.w(TAG, " No selected group to delete!" );
                        }
                    }
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (DBG) {
                        Log.d(TAG, " forgetting selected group " + mSelectedGroup.getGroupName());
                    }
                    mSelectedGroup = null;
                }
            }
        };

        setHasOptionsMenu(true);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removeAll();
        preferenceScreen.setOrderingAsAdded(true);

		/*add by wanghui for al812 wifip2p ui begin*/
        mMydevice = new PreferenceCategory(getActivity());
        mMydevice.setTitle(R.string.wifi_p2p_device);
        preferenceScreen.addPreference(mMydevice);
        /*add by wanghui for al812 wifip2p ui end*/
        mThisDevicePref = new Preference(getActivity());
        mThisDevicePref.setPersistent(false);
        mThisDevicePref.setSelectable(false);
        preferenceScreen.addPreference(mThisDevicePref);

        mPeersGroup = new PreferenceCategory(getActivity());
        mPeersGroup.setTitle(R.string.wifi_p2p_peer_devices);
        preferenceScreen.addPreference(mPeersGroup);

        mPersistentGroup = new PreferenceCategory(getActivity());
        mPersistentGroup.setTitle(R.string.wifi_p2p_remembered_groups);
        preferenceScreen.addPreference(mPersistentGroup);

        /// M: Wifi P2P EM @{
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT) {
            mWpsP2pEmSettings = new WifiWpsP2pEmSettings(getActivity(), mWifiP2pManager, mChannel);
        }
        /// @}
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        getActivity().registerReceiver(mReceiver, mIntentFilter);
        if (mWifiP2pManager != null) {
            mWifiP2pManager.requestPeers(mChannel, WifiP2pSettings.this);
        }
        /// M: Wifi P2P EM @{
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.resume();
        }
        /// @}
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (mWifiP2pManager != null) {
            mWifiP2pManager.stopPeerDiscovery(mChannel, null);
        }
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onDestroy() {
        mWifiP2pManager.deinitialize(mChannel);
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        int textId = mWifiP2pSearching ? R.string.wifi_p2p_menu_searching :
                R.string.wifi_p2p_menu_search;
				//add  by  wanghui for al812  ui
        menu.add(Menu.NONE, MENU_ID_SEARCH, 0, textId)
            .setIcon(R.drawable.ic_seacher_device_imager)
            .setEnabled(mWifiP2pEnabled)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_ID_RENAME, 0, R.string.wifi_p2p_menu_rename)
            .setIcon(R.drawable.ic_rename_image)
            .setEnabled(mWifiP2pEnabled)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        /// M: Wifi P2P EM @{
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.createOptionsMenu(menu);
        }
        ///@}
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem searchMenu = menu.findItem(MENU_ID_SEARCH);
        MenuItem renameMenu = menu.findItem(MENU_ID_RENAME);
        /// M: disable search menu when searching @{
        searchMenu.setEnabled(mWifiP2pEnabled && !mWifiP2pSearching);
        renameMenu.setEnabled(mWifiP2pEnabled);
        /// @}

        if (mWifiP2pSearching){
            searchMenu.setTitle(R.string.wifi_p2p_menu_searching);
        } else {
            searchMenu.setTitle(R.string.wifi_p2p_menu_search);
        }

        /// M: Wifi P2P EM @{
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.prepareOptionsMenu(menu);
        }
        /// @}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_SEARCH:
                startSearch();
                return true;
            case MENU_ID_RENAME:
                showDialog(DIALOG_RENAME);
                return true;
        }
        /// M: Wifi P2P EM @{
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            return mWpsP2pEmSettings.optionsItemSelected(item);
        }
        /// @}
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof WifiP2pPeer) {
            mSelectedWifiPeer = (WifiP2pPeer) preference;
            if (mSelectedWifiPeer.device.status == WifiP2pDevice.CONNECTED) {
                showDialog(DIALOG_DISCONNECT);
            } else if (mSelectedWifiPeer.device.status == WifiP2pDevice.INVITED) {
                showDialog(DIALOG_CANCEL_CONNECT);
            } else {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = mSelectedWifiPeer.device.deviceAddress;

                int forceWps = SystemProperties.getInt("wifidirect.wps", -1);

                if (forceWps != -1) {
                    config.wps.setup = forceWps;
                } else {
                    if (mSelectedWifiPeer.device.wpsPbcSupported()) {
                        config.wps.setup = WpsInfo.PBC;
                    } else if (mSelectedWifiPeer.device.wpsKeypadSupported()) {
                        config.wps.setup = WpsInfo.KEYPAD;
                    } else {
                        config.wps.setup = WpsInfo.DISPLAY;
                    }
                }

                mWifiP2pManager.connect(mChannel, config,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " connect success");
                            }
                            public void onFailure(int reason) {
                                Log.e(TAG, " connect fail " + reason);
                                Toast.makeText(getActivity(),
                                        R.string.wifi_p2p_failed_connect_message,
                                        Toast.LENGTH_SHORT).show();
                            }
                    });
            }
        } else if (preference instanceof WifiP2pPersistentGroup) {
            mSelectedGroup = (WifiP2pPersistentGroup) preference;
            showDialog(DIALOG_DELETE_GROUP);
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_DISCONNECT) {
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.device.deviceName) ?
                    mSelectedWifiPeer.device.deviceAddress :
                    mSelectedWifiPeer.device.deviceName;
            String msg;
            if (mConnectedDevices > 1) {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_multiple_message,
                        deviceName, mConnectedDevices - 1);
            } else {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_message, deviceName);
            }
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 begin */
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_disconnect_title)
                .setMessage(msg)
                .setPositiveButton(IS_RTL?getActivity().getString(R.string.dlg_cancel):getActivity().getString(R.string.dlg_ok), mDisconnectListener)
                .setNegativeButton(IS_RTL?getActivity().getString(R.string.dlg_ok):getActivity().getString(R.string.dlg_cancel), null)
                .create();
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 end */
            return dialog;
        } else if (id == DIALOG_CANCEL_CONNECT) {
            int stringId = R.string.wifi_p2p_cancel_connect_message;
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.device.deviceName) ?
                    mSelectedWifiPeer.device.deviceAddress :
                    mSelectedWifiPeer.device.deviceName;
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 begin */
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_cancel_connect_title)
                .setMessage(getActivity().getString(stringId, deviceName))
                /* modified by shanlan for HQ02065742 begin */
                .setPositiveButton(IS_RTL?getActivity().getString(R.string.dlg_cancel):getActivity().getString(R.string.dlg_ok), mCancelConnectListener)
                /* modified by shanlan for HQ02065742 end */
                .setNegativeButton(IS_RTL?getActivity().getString(R.string.dlg_ok):getActivity().getString(R.string.dlg_cancel), null)
                .create();
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 end */
            return dialog;
        } else if (id == DIALOG_RENAME) {
            mDeviceNameText = new EditText(getActivity());
            mDeviceNameText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(30)});
            mDeviceNameText.setTextDirection(View.TEXT_DIRECTION_LOCALE);
            if (mSavedDeviceName != null) {
                mDeviceNameText.setText(mSavedDeviceName);
                mDeviceNameText.setSelection(mSavedDeviceName.length());
            } else if (mThisDevice != null && !TextUtils.isEmpty(mThisDevice.deviceName)) {
                mDeviceNameText.setText(mThisDevice.deviceName);
                mDeviceNameText.setSelection(0, mThisDevice.deviceName.length());
            }
            mSavedDeviceName = null;
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 begin */
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_menu_rename)
                .setView(mDeviceNameText)
                .setPositiveButton(IS_RTL?getActivity().getString(R.string.dlg_cancel):getActivity().getString(R.string.dlg_ok), mRenameListener)
                .setNegativeButton(IS_RTL?getActivity().getString(R.string.dlg_ok):getActivity().getString(R.string.dlg_cancel), null)
                .create();
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 end */
            /*add by wanghui for al813 HQ01398537*/
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            return dialog;
        } else if (id == DIALOG_DELETE_GROUP) {
            int stringId = R.string.wifi_p2p_delete_group_message;
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 begin */
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setMessage(getActivity().getString(stringId))
                .setPositiveButton(IS_RTL?getActivity().getString(R.string.dlg_cancel):getActivity().getString(R.string.dlg_ok), mDeleteGroupListener)
                .setNegativeButton(IS_RTL?getActivity().getString(R.string.dlg_ok):getActivity().getString(R.string.dlg_cancel),
                        mDeleteGroupListener).create();
            /* HQ_zhangpeng5 2015-11-24 modified for modified for Arabic layout HQ00055994 end */
            return dialog;
        }
        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mSelectedWifiPeer != null) {
            outState.putParcelable(SAVE_DIALOG_PEER, mSelectedWifiPeer.device);
        }
        if (mDeviceNameText != null) {
            outState.putString(SAVE_DEVICE_NAME, mDeviceNameText.getText().toString());
        }
        if (mSelectedGroup != null) {
            outState.putString(SAVE_SELECTED_GROUP, mSelectedGroup.getGroupName());
        }
    }

    private void handlePeersChanged() {
        mPeersGroup.removeAll();

        mConnectedDevices = 0;
        if (DBG) Log.d(TAG, "List of available peers");
        for (WifiP2pDevice peer: mPeers.getDeviceList()) {
            if (DBG) Log.d(TAG, "-> " + peer);
            mPeersGroup.addPreference(new WifiP2pPeer(getActivity(), peer));
            if (peer.status == WifiP2pDevice.CONNECTED) mConnectedDevices++;
        }
        if (DBG) Log.d(TAG, " mConnectedDevices " + mConnectedDevices);
    }

    @Override
    public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
        mPersistentGroup.removeAll();

        for (WifiP2pGroup group: groups.getGroupList()) {
            if (DBG) Log.d(TAG, " group " + group);
            WifiP2pPersistentGroup wppg = new WifiP2pPersistentGroup(getActivity(), group);
            mPersistentGroup.addPreference(wppg);
            if (wppg.getGroupName().equals(mSelectedGroupName)) {
                if (DBG) Log.d(TAG, "Selecting group " + wppg.getGroupName());
                mSelectedGroup = wppg;
                mSelectedGroupName = null;
            }
        }
        if (mSelectedGroupName != null) {
            // Looks like there's a dialog pending getting user confirmation to delete the
            // selected group. When user hits OK on that dialog, we won't do anything; but we
            // shouldn't be in this situation in first place, because these groups are persistent
            // groups and they shouldn't just get deleted!
            Log.w(TAG, " Selected group " + mSelectedGroupName + " disappered on next query ");
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        if (DBG) Log.d(TAG, "Requested peers are available");
        mPeers = peers;
        handlePeersChanged();
    }

    private void handleP2pStateChanged() {
        updateSearchMenu(false);
        mThisDevicePref.setEnabled(mWifiP2pEnabled);
        mPeersGroup.setEnabled(mWifiP2pEnabled);
        mPersistentGroup.setEnabled(mWifiP2pEnabled);
        ///M: add mtk feature
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null && !mWifiP2pEnabled) {
            mWpsP2pEmSettings.handleP2pStateChanged();
        }
    }

    private void updateSearchMenu(boolean searching) {
       mWifiP2pSearching = searching;
       Activity activity = getActivity();
       if (activity != null) activity.invalidateOptionsMenu();
    }

    private void startSearch() {
        if (mWifiP2pManager != null && !mWifiP2pSearching) {
            mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                }
                public void onFailure(int reason) {
                    if (DBG) Log.d(TAG, " discover fail " + reason);
                }
            });
        }
    }

    private void updateDevicePref() {
        if (mThisDevice != null) {
            if (TextUtils.isEmpty(mThisDevice.deviceName)) {
                mThisDevicePref.setTitle(mThisDevice.deviceAddress);
            } else {
                mThisDevicePref.setTitle(mThisDevice.deviceName);
            }
        }
    }
}
