/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Intent;
import android.content.res.TypedArray;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.AbsListView.LayoutParams;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.graphics.drawable.AnimationDrawable;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;

import com.android.settings.R;

/**
 * This customized version of WifiSettings is shown to the user only during Setup Wizard. Menu
 * selections are limited, clicking on an access point will auto-advance to the next screen (once
 * connected), and, if the user opts to skip ahead without a wifi connection, a warning message
 * alerts of possible carrier data charges or missing software updates.
 */
public class WifiSettingsForSetupWizard extends WifiSettings {

    private static final String TAG = "WifiSettingsForSetupWizard";

    // show a text regarding data charges when wifi connection is required during setup wizard
    protected static final String EXTRA_SHOW_WIFI_REQUIRED_INFO = "wifi_show_wifi_required_info";

    private View mAddOtherNetworkItem;
    private ListAdapter mAdapter;
    private TextView mEmptyFooter;
    private Switch mSwitch;
    private ImageView mStatInout, mStatWifi, mStatSim;
    private WifiStateReceiver mStateReceiver;
    private IntentFilter mFilter;
    private WifiManager mWifiMgr;
    private AnimationDrawable mInoutAnim;
    private boolean mListLastEmpty = false;

    public class WifiStateReceiver extends BroadcastReceiver {
        ImageView wifiStateImage;
        Context context;

        public WifiStateReceiver(Context context, ImageView imageView) {
            this.wifiStateImage = imageView;
            this.context = context;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction().equals(WifiManager.RSSI_CHANGED_ACTION)) {
                WifiInfo wifiInfo = mWifiMgr.getConnectionInfo();
                if (wifiInfo != null && wifiInfo.getBSSID() != null) {
                    int signalLevel = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
                    wifiStateImage.setImageLevel(signalLevel);
                }
            } else if (intent != null && intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info != null && info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    wifiStateImage.setVisibility(View.GONE);
                    mStatInout.setVisibility(View.GONE);
                    mInoutAnim.stop();
                }
                if (info != null && info.isConnected()) {
                    Log.i(TAG, "isConnected");
                    wifiStateImage.setVisibility(View.VISIBLE);
                    mStatInout.setVisibility(View.VISIBLE);
                    wifiStateImage.setImageLevel(4);
                    mInoutAnim.start();
                }
            } else if (intent != null && intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
                    Log.i(TAG, "wifitate: " + wifistate);
                    wifiStateImage.setVisibility(View.GONE);
                    mStatInout.setVisibility(View.GONE);
                    mInoutAnim.stop();
                }
            }
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        int themeID = getActivity().getResources().getIdentifier("androidhwext:style/Theme.Emui", null, null);
        getActivity().setTheme(themeID);
        this.getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mWifiMgr = (WifiManager)getActivity().getSystemService(getActivity().WIFI_SERVICE);
        final TelephonyManager teleMgr = (TelephonyManager) getActivity().getSystemService(getActivity().TELEPHONY_SERVICE);
        final View view = inflater.inflate(R.layout.setup_preference, container, false);

        mStatInout = (ImageView) view.findViewById(R.id.stat_in_out);
        mStatWifi = (ImageView) view.findViewById(R.id.stat_wifi);
        mStatSim = (ImageView) view.findViewById(R.id.stat_sim);

        mStatInout.setBackgroundResource(R.drawable.stat_inout_anim_layout);
        mInoutAnim = (AnimationDrawable)mStatInout.getBackground();

        if (teleMgr != null && teleMgr.hasIccCard()) {
           mStatSim.setVisibility(View.VISIBLE);
        }
        mStateReceiver = new WifiStateReceiver(getActivity(), mStatWifi);
        mFilter=new IntentFilter();
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        //set the sim icon
        if (teleMgr != null && (teleMgr.hasIccCard(0) || teleMgr.hasIccCard(1))) {
            mStatSim.setVisibility(View.VISIBLE);
        }

        final ListView list = (ListView) view.findViewById(android.R.id.list);
        final View title = view.findViewById(R.id.title);
        if (title == null) {
              final View header = inflater.inflate(R.layout.setup_wizard_header_layout, list, false);
              mSwitch = (Switch) header.findViewById(R.id.switch_bar);
              /*HQ_xupeixin at 2015-11-10 modified about disable the wifi in latin_claro product begin*/
              //disable the wifi
              if (SystemProperties.get("ro.hq.claro.wifisetup.control").equals("1")) {
                  mWifiMgr.setWifiEnabled(false);
                  mSwitch.setChecked(false);
              } else {
                  //wifi enabled when com.huawei.hwstartupguide language activity click next button.
                  mSwitch.setChecked(true);
              }
              /*HQ_xupeixin at 2015-11-10 modified end*/
              mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                  @Override
                  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                      //close: false, open: true
                      if (mWifiMgr != null) {
                          if (isChecked) {
                            //enable the wifi
                            if (!mWifiMgr.isWifiEnabled()) {
                                mWifiMgr.setWifiEnabled(true);
                            }
                          } else {
                            //disable the wifi
                            if (mWifiMgr.isWifiEnabled()) {
                                mWifiMgr.setWifiEnabled(false);
                            }
                          }
                      }
                  }
              });
              list.setHeaderDividersEnabled(true);
              list.addHeaderView(header, null, false);
        }

        mAddOtherNetworkItem = inflater.inflate(R.layout.setup_wifi_add_network, list, false);
        list.setFooterDividersEnabled(true);
        list.addFooterView(mAddOtherNetworkItem, null, true);
        mAddOtherNetworkItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWifiManager.isWifiEnabled()) {
                    onAddNetworkPressed();
                }
            }
        });

        final Intent intent = getActivity().getIntent();
        if (intent.getBooleanExtra(EXTRA_SHOW_WIFI_REQUIRED_INFO, false)) {
            view.findViewById(R.id.wifi_required_info).setVisibility(View.VISIBLE);
        }

        return view;
    }

    @Override
    public void onResume () {
        super.onResume();
        //if (mWifiMgr != null && mStateReceiver != null && mFilter != null && mWifiMgr.isWifiEnabled()) {
        //    getActivity().registerReceiver(mStateReceiver, mFilter);
        //}
    }

    @Override
    public void onStop() {
        super.onStop();
        //if (mStateReceiver != null)
        //    getActivity().unregisterReceiver(mStateReceiver);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        /**
        getView().setSystemUiVisibility(
                View.STATUS_BAR_DISABLE_HOME |
                View.STATUS_BAR_DISABLE_RECENT |
                View.STATUS_BAR_DISABLE_BACK |
                View.STATUS_BAR_DISABLE_NOTIFICATION_ALERTS |
                View.STATUS_BAR_DISABLE_CLOCK);
        */
        getView().setSystemUiVisibility(
               View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.NAVIGATION_BAR_TRANSLUCENT
                );
        if (hasNextButton()) {
            getNextButton().setVisibility(View.GONE);
        }

        mAdapter = getPreferenceScreen().getRootAdapter();
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateFooter();
            }
        });
    }

    @Override
    public void registerForContextMenu(View view) {
        // Suppressed during setup wizard
    }

    @Override
    /* package */ WifiEnabler createWifiEnabler() {
        // Not shown during setup wizard
        return null;
    }

    @Override
    /* package */ void addOptionsMenuItems(Menu menu) {
        /*final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        final TypedArray ta = getActivity().getTheme()
                .obtainStyledAttributes(new int[] {R.attr.ic_wps});
        menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                .setIcon(ta.getDrawable(0))
                .setEnabled(wifiIsEnabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setIcon(R.drawable.ic_menu_add_light)
                    .setEnabled(wifiIsEnabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        ta.recycle();*/
    }

    @Override
    protected void connect(final WifiConfiguration config) {
        WifiSetupActivity activity = (WifiSetupActivity) getActivity();
        activity.networkSelected();
        super.connect(config);
    }

    @Override
    protected void connect(final int networkId) {
        WifiSetupActivity activity = (WifiSetupActivity) getActivity();
        activity.networkSelected();
        super.connect(networkId);
    }

    @Override
    protected TextView initEmptyView() {
        mEmptyFooter = new TextView(getActivity());
        mEmptyFooter.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mEmptyFooter.setGravity(Gravity.CENTER);
        mEmptyFooter.setCompoundDrawablesWithIntrinsicBounds(0,
                R.drawable.ic_wifi_emptystate, 0,0);
        return mEmptyFooter;
    }

    protected void updateFooter() {
       //add
        if (getView() == null) {
            Log.d(TAG, "exceptional life cycle that may cause JE");
            return;
        }
        /// @}

        final boolean isEmpty = mAdapter.isEmpty();
        if (isEmpty != mListLastEmpty) {
            final ListView list = getListView();
            if (isEmpty) {
                list.removeFooterView(mAddOtherNetworkItem);
                list.addFooterView(mEmptyFooter, null, false);
            } else {
                list.removeFooterView(mEmptyFooter);
                list.addFooterView(mAddOtherNetworkItem, null, true);
            }
            mListLastEmpty = isEmpty;
        }
    }
}
