/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsApplication.SmsApplicationData;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.IWfcSettingsExt;

import com.mediatek.settings.FeatureOption;
import com.mediatek.wfc.WfcSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import android.telephony.SubscriptionInfo;
import com.android.internal.telephony.PhoneConstants;





public class WirelessSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener, Indexable {
    private static final String TAG = "WirelessSettings";

    private static final String KEY_TOGGLE_AIRPLANE = "toggle_airplane";
    private static final String KEY_WIMAX_SETTINGS = "wimax_settings";
    private static final String KEY_ANDROID_BEAM_SETTINGS = "android_beam_settings";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_TETHER_SETTINGS = "tether_settings";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";
    private static final String KEY_MANAGE_MOBILE_PLAN = "manage_mobile_plan";
    private static final String KEY_TOGGLE_NSD = "toggle_nsd"; //network service discovery
    private static final String KEY_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";
	private static final String KEY_CB_CELLBROADCAST_SETTINGS = "cb_cellbroadcast_settings";
	public String SUB_TITLE_NAME = "sub_title_name";
	public String PREF_KEY_CELL_BROADCAST = "pref_key_cell_broadcast";
	public String TITLE_NAME = "title_name";

    /// M: @{
    private static final String RCSE_SETTINGS_INTENT = "com.mediatek.rcse.RCSE_SETTINGS";
    private static final String KEY_RCSE_SETTINGS = "rcse_settings";
    ///M: WFC  @ {
    private static final String KEY_WFC_SETTINGS = "wfc_pref";
    /// @}

    public static final String EXIT_ECM_RESULT = "exit_ecm_result";
    public static final int REQUEST_CODE_EXIT_ECM = 1;

    private AirplaneModeEnabler mAirplaneModeEnabler;
    private SwitchPreference mAirplaneModePreference;
    private NsdEnabler mNsdEnabler;

    /// M: @{
    private PreferenceScreen mNetworkSettingsPreference;
	private PreferenceScreen mCbbroadcastsettingPreference;

    private ConnectivityManager mConnectivityManager;
    private IntentFilter mIntentFilter;
    private Preference mTetherSettings;
    ///M: WFC @ {
    private Preference mWFCSettingsPreference;
    private WfcSummary mWfcSummary;
    private IWfcSettingsExt mWfcSettingsExt;
    /// @}

    private ConnectivityManager mCm;
    private TelephonyManager mTm;
    private PackageManager mPm;
    private UserManager mUm;

    private static final int MANAGE_MOBILE_PLAN_DIALOG_ID = 1;
    private static final String SAVED_MANAGE_MOBILE_PLAN_MSG = "mManageMobilePlanMessage";

    /// M: the feature of manage mobile plan removed temporarily @{
    private boolean mDisableMobilePlan = true;
    /// @}


    /**
     * M: receiver
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                Log.d(TAG, "ACTION_SIM_INFO_UPDATE received");
                updateMobileNetworkEnabled();
            }
            ///M: WFC @ {
            else if (WfcSummary.ACTION_WFC_SUMMARY_CHANGE.equals(action)) {
                setWfcSummary(intent.getStringExtra(WfcSummary.EXTRA_SUMMARY));
            } else if (TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE.equals(action)
                    || (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)
                    && (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))
                    || IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))))) {
                if (!ImsManager.isImsSupportByCarrier(context)) {
                    log("sim changed or Master sim changed, remove WCF setting");
                    log("getPrefScreen:" + getPreferenceScreen());
                    if (mWFCSettingsPreference != null) {
                        log("remove WCF setting");
                        getPreferenceScreen().removePreference(mWFCSettingsPreference);
                        mWFCSettingsPreference = null;
                    }
                }
            }
            /// @}
        }
    };

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceFragment's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        log("onPreferenceTreeClick: preference=" + preference);
        if (preference == mAirplaneModePreference && Boolean.parseBoolean(
                SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode launch ECM app dialog
            startActivityForResult(
                new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                REQUEST_CODE_EXIT_ECM);
            return true;
        } else if (preference == findPreference(KEY_MANAGE_MOBILE_PLAN)) {
            onManageMobilePlanClick();
        }else if(preference == mCbbroadcastsettingPreference){
   //add by wanghui for surfes
			List<SubscriptionInfo> subInfoList = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoList();
        	    int mSubCount = (subInfoList != null && !subInfoList.isEmpty()) ? subInfoList
				.size() : 0;
			    int subId = subInfoList.get(0).getSubscriptionId();
			
                if(mSubCount>1){
			        Intent it = new Intent();
					ComponentName cn=new ComponentName("com.android.contacts",  
                      "com.android.mms.ui.SubSelectActivity");  
                    it.putExtra("PREFERENCE_KEY", PREF_KEY_CELL_BROADCAST);
                    it.putExtra("PREFERENCE_TITLE",getResources().getString(R.string.viewer_title_cb));
					it.putExtra(TITLE_NAME, getResources().getString(R.string.viewer_title_cb));
					it.setComponent(cn);
					startActivity(it);
				}
				else{
				      Intent it = new Intent();
				      ComponentName cn=new ComponentName("com.android.contacts",  
                      "com.mediatek.cbsettings.CellBroadcastActivity");  
				      it.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
                      it.putExtra(SUB_TITLE_NAME, subInfoList.get(0).getDisplayName().toString());
                      it.setComponent(cn); 
				      Log.d("wanghui","intent");
                      startActivity(it);
				 }
		}
        //M: @}
        // Let the intents be launched by the Preference manager
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private String mManageMobilePlanMessage;
    public void onManageMobilePlanClick() {
        log("onManageMobilePlanClick:");
        mManageMobilePlanMessage = null;
        Resources resources = getActivity().getResources();

        NetworkInfo ni = mCm.getProvisioningOrActiveNetworkInfo();
        if (mTm.hasIccCard() && (ni != null)) {
            // Check for carrier apps that can handle provisioning first
            Intent provisioningIntent = new Intent(TelephonyIntents.ACTION_CARRIER_SETUP);
            List<String> carrierPackages =
                    mTm.getCarrierPackageNamesForIntent(provisioningIntent);
            if (carrierPackages != null && !carrierPackages.isEmpty()) {
                if (carrierPackages.size() != 1) {
                    Log.w(TAG, "Multiple matching carrier apps found, launching the first.");
                }
                provisioningIntent.setPackage(carrierPackages.get(0));
                startActivity(provisioningIntent);
                return;
            }

            // Get provisioning URL
            String url = mCm.getMobileProvisioningUrl();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                        Intent.CATEGORY_APP_BROWSER);
                intent.setData(Uri.parse(url));
                intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                        Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "onManageMobilePlanClick: startActivity failed" + e);
                }
            } else {
                // No provisioning URL
                String operatorName = mTm.getSimOperatorName();
                if (TextUtils.isEmpty(operatorName)) {
                    // Use NetworkOperatorName as second choice in case there is no
                    // SPN (Service Provider Name on the SIM). Such as with T-mobile.
                    operatorName = mTm.getNetworkOperatorName();
                    if (TextUtils.isEmpty(operatorName)) {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_unknown_sim_operator);
                    } else {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_no_provisioning_url, operatorName);
                    }
                } else {
                    mManageMobilePlanMessage = resources.getString(
                            R.string.mobile_no_provisioning_url, operatorName);
                }
            }
        } else if (mTm.hasIccCard() == false) {
            // No sim card
            mManageMobilePlanMessage = resources.getString(R.string.mobile_insert_sim_card);
        } else {
            // NetworkInfo is null, there is no connection
            mManageMobilePlanMessage = resources.getString(R.string.mobile_connect_to_internet);
        }
        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            log("onManageMobilePlanClick: message=" + mManageMobilePlanMessage);
            showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        log("onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(mManageMobilePlanMessage)
                            .setCancelable(false)
                            .setPositiveButton(com.android.internal.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    log("MANAGE_MOBILE_PLAN_DIALOG.onClickListener id=" + id);
                                    mManageMobilePlanMessage = null;
                                }
                            })
                            .create();
        }
        return super.onCreateDialog(dialogId);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    public static boolean isRadioAllowed(Context context, String type) {
        if (!AirplaneModeEnabler.isAirplaneModeOn(context)) {
            return true;
        }
        // Here we use the same logic in onCreate().
        String toggleable = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        return toggleable != null && toggleable.contains(type);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mManageMobilePlanMessage = savedInstanceState.getString(SAVED_MANAGE_MOBILE_PLAN_MSG);
        }
        log("onCreate: mManageMobilePlanMessage=" + mManageMobilePlanMessage);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mTm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mPm = getPackageManager();
        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        addPreferencesFromResource(R.xml.wireless_settings);

        final int myUserId = UserHandle.myUserId();
        final boolean isSecondaryUser = myUserId != UserHandle.USER_OWNER;
        final boolean isRestrictedUser = mUm.getUserInfo(myUserId).isRestricted();

        final Activity activity = getActivity();
        mAirplaneModePreference = (SwitchPreference) findPreference(KEY_TOGGLE_AIRPLANE);
        PreferenceScreen androidBeam = (PreferenceScreen) findPreference(KEY_ANDROID_BEAM_SETTINGS);
        SwitchPreference nsd = (SwitchPreference) findPreference(KEY_TOGGLE_NSD);
        mWfcSettingsExt = UtilsExt.getWfcSettingsExtPlugin(getActivity());
        mWfcSettingsExt.customizedWfcPreference(getActivity(), getPreferenceScreen());
        /// M: @{
        mNetworkSettingsPreference = (PreferenceScreen) findPreference(KEY_MOBILE_NETWORK_SETTINGS);
        /// @}
         ///M: WFC: add wfc preference @ {
        mWFCSettingsPreference = (Preference) findPreference(KEY_WFC_SETTINGS);
        if (mWFCSettingsPreference != null) {
            mWfcSummary = new WfcSummary(activity);
            if (!FeatureOption.MTK_WFC_SUPPORT || !ImsManager.isImsSupportByCarrier(activity)) {
                getPreferenceScreen().removePreference(mWFCSettingsPreference);
                mWFCSettingsPreference = null;
            }
        }
        /// @}
		mCbbroadcastsettingPreference = (PreferenceScreen) findPreference(KEY_CB_CELLBROADCAST_SETTINGS);

        mAirplaneModeEnabler = new AirplaneModeEnabler(activity, mAirplaneModePreference);

        // Remove NSD checkbox by default
        getPreferenceScreen().removePreference(nsd);
        //mNsdEnabler = new NsdEnabler(activity, nsd);
         // hanchao add  begin for HQ01609815
         int mCurrentSubCount = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoCount();        
         if (mCurrentSubCount < 1) {
        	 mCbbroadcastsettingPreference.setEnabled(false);
         }
        //hanchao add end for HQ01609815
        String toggleable = Settings.Global.getString(activity.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);

        //enable/disable wimax depending on the value in config.xml
        final boolean isWimaxEnabled = !isSecondaryUser && this.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if (!isWimaxEnabled
                || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
            if (ps != null) root.removePreference(ps);
        } else {
            if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIMAX )
                    && isWimaxEnabled) {
                Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
                ps.setDependency(KEY_TOGGLE_AIRPLANE);
            }
        }

        // Manually set dependencies for Wifi when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIFI)) {
            findPreference(KEY_VPN_SETTINGS).setDependency(KEY_TOGGLE_AIRPLANE);
        }
        // Disable VPN.
        if (isSecondaryUser || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_VPN)) {
            removePreference(KEY_VPN_SETTINGS);
        }

        // Manually set dependencies for Bluetooth when not toggleable.
        //if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_BLUETOOTH)) {
            // No bluetooth-dependent items in the list. Code kept in case one is added later.
        //}

        /// M: the feature of manage mobile plan removed temporarily @{
        if (mDisableMobilePlan) {
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        }
        /// @}

        // Remove Mobile Network Settings and Manage Mobile Plan for secondary users,
        // if it's a wifi-only device, or if the settings are restricted.
        if (isSecondaryUser || Utils.isWifiOnly(getActivity())
                || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            /// M: remove preference
            getPreferenceScreen().removePreference(mNetworkSettingsPreference);
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        }
        // Remove Mobile Network Settings and Manage Mobile Plan
        // if config_show_mobile_plan sets false.
        final boolean isMobilePlanEnabled = this.getResources().getBoolean(
                R.bool.config_show_mobile_plan);
        if (!isMobilePlanEnabled) {
            Preference pref = findPreference(KEY_MANAGE_MOBILE_PLAN);
            if (pref != null) {
                removePreference(KEY_MANAGE_MOBILE_PLAN);
            }
        }

        // Remove Airplane Mode settings if it's a stationary device such as a TV.
        if (mPm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            removePreference(KEY_TOGGLE_AIRPLANE);
        }

        // Enable Proxy selector settings if allowed.
        Preference mGlobalProxy = findPreference(KEY_PROXY_SETTINGS);
        final DevicePolicyManager mDPM = (DevicePolicyManager)
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        // proxy UI disabled until we have better app support
        getPreferenceScreen().removePreference(mGlobalProxy);
        mGlobalProxy.setEnabled(mDPM.getGlobalProxyAdmin() == null);

        /// M: @{
        // Disable Tethering if it's not allowed or if it's a wifi-only device
        mConnectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTetherSettings = findPreference(KEY_TETHER_SETTINGS);
        if (mConnectivityManager != null) {
            if (isSecondaryUser || !mConnectivityManager.isTetheringSupported() || Utils.isWifiOnly(getActivity())
                    || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)) {
                getPreferenceScreen().removePreference(mTetherSettings);
            } else {
                mTetherSettings.setTitle(Utils.getTetheringLabel(mConnectivityManager));
                mTetherSettings.setEnabled(!TetherSettings
                    .isProvisioningNeededButUnavailable(getActivity()));
            }
        }
        /// @}
        // Enable link to CMAS app settings depending on the value in config.xml.
        boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                com.android.internal.R.bool.config_cellBroadcastAppLinks);
        try {
            if (isCellBroadcastAppLinkEnabled) {
                if (mPm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                }
            }
        } catch (IllegalArgumentException ignored) {
            isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
        }
        if (isSecondaryUser || !isCellBroadcastAppLinkEnabled
                || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = findPreference(KEY_CELL_BROADCAST_SETTINGS);
            if (ps != null) root.removePreference(ps);
        }

        /// M: Remove the entrance if RCSE not support. @{
        if (isAPKInstalled(activity, RCSE_SETTINGS_INTENT)) {
            Intent intent = new Intent(RCSE_SETTINGS_INTENT);
            findPreference(KEY_RCSE_SETTINGS).setIntent(intent);
        } else {
            Log.w(TAG, RCSE_SETTINGS_INTENT + " is not installed");
            getPreferenceScreen().removePreference(findPreference(KEY_RCSE_SETTINGS));
        }
        /// @}
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        ///M: WFC @ {
        if (mWFCSettingsPreference != null) {
            mIntentFilter.addAction(WfcSummary.ACTION_WFC_SUMMARY_CHANGE);
        }
        mIntentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        /// @}
    }
    /**
     * M: update mobile network enabled
     */
    private void updateMobileNetworkEnabled() {
        ///M: GEMINI+
        ///   modify in a simple way to get whether there is sim card inserted
        ISettingsMiscExt miscExt = UtilsExt.getMiscPlugin(getActivity());
		
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int callState = telephonyManager.getCallState();
        int simNum = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoCount();
        Log.d(TAG, "callstate = " + callState + " simNum = " + simNum);
        if (simNum > 0 && callState == TelephonyManager.CALL_STATE_IDLE) {
            mNetworkSettingsPreference.setEnabled(true);
        } else {
            mNetworkSettingsPreference.setEnabled(false);
            mNetworkSettingsPreference.setEnabled(miscExt.useCTTestcard());
        }
        /// M:  @{
    }
    /// M:  @{
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            Log.d(TAG, "PhoneStateListener, new state=" + state);
            if (state == TelephonyManager.CALL_STATE_IDLE && getActivity() != null) {
                updateMobileNetworkEnabled();
            }
        }
    };
    /// @}

    @Override
    public void onStart() {
        super.onStart();
            /// M: ALPS02243976, update sms summary
           //updateSmsApplicationSetting();
    }

    @Override
    public void onResume() {
        super.onResume();

        mAirplaneModeEnabler.resume();
        if (mNsdEnabler != null) {
            mNsdEnabler.resume();
        }

        /// M:  @{
        TelephonyManager telephonyManager =
            (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        updateMobileNetworkEnabled();
        getActivity().registerReceiver(mReceiver, mIntentFilter);
        
        ///M: WFC: set wfc summary @ {
        if (mWFCSettingsPreference != null)  {
            mWfcSummary.registerWfcSummary();
            ImsManager imsManager = ImsManager.getInstance(getActivity(),
                    SubscriptionManager.getDefaultVoicePhoneId());
            Log.d(TAG, "Wfc state:" + imsManager.getWfcStatusCode());
            setWfcSummary(mWfcSummary.getWfcSummaryText(imsManager.getWfcStatusCode()));
        }
        mWfcSettingsExt.customizedWfcPreference(getActivity(), getPreferenceScreen());
        
        /// @}
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            outState.putString(SAVED_MANAGE_MOBILE_PLAN_MSG, mManageMobilePlanMessage);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        /// M:  @{
        TelephonyManager telephonyManager =
            (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        getActivity().unregisterReceiver(mReceiver);
        /// @}

        mAirplaneModeEnabler.pause();
        if (mNsdEnabler != null) {
            mNsdEnabler.pause();
        }
          ///M: WFC @ {
        if (mWFCSettingsPreference != null)  {
            mWfcSummary.unRegisterWfcSummary();
        }
        /// @}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        /// M : Add for bug fix ALPS01772247, since in case not receive call back listener, and 
        /// press back, need to unregister listener here
       // mAirplaneModeEnabler.destroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EXIT_ECM) {
            Boolean isChoiceYes = data.getBooleanExtra(EXIT_ECM_RESULT, false);
            // Set Airplane mode based on the return value and checkbox state
            mAirplaneModeEnabler.setAirplaneModeInECM(isChoiceYes,
                    mAirplaneModePreference.isChecked());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_more_networks;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(
                    Context context, boolean enabled) {
                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.wireless_settings;
                return Arrays.asList(sir);
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                final ArrayList<String> result = new ArrayList<String>();

                result.add(KEY_TOGGLE_NSD);

                final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
                final int myUserId = UserHandle.myUserId();
                final boolean isSecondaryUser = myUserId != UserHandle.USER_OWNER;
                final boolean isRestrictedUser = um.getUserInfo(myUserId).isRestricted();
                final boolean isWimaxEnabled = !isSecondaryUser
                        && context.getResources().getBoolean(
                        com.android.internal.R.bool.config_wimaxEnabled);
                if (!isWimaxEnabled
                        || um.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
                    result.add(KEY_WIMAX_SETTINGS);
                }

                if (isSecondaryUser) { // Disable VPN
                    result.add(KEY_VPN_SETTINGS);
                }

                // Remove Mobile Network Settings and Manage Mobile Plan if it's a wifi-only device.
                if (isSecondaryUser || Utils.isWifiOnly(context)) {
                    result.add(KEY_MOBILE_NETWORK_SETTINGS);
                    result.add(KEY_MANAGE_MOBILE_PLAN);
                }

                // Remove Mobile Network Settings and Manage Mobile Plan
                // if config_show_mobile_plan sets false.
                final boolean isMobilePlanEnabled = context.getResources().getBoolean(
                        R.bool.config_show_mobile_plan);
                if (!isMobilePlanEnabled) {
                    result.add(KEY_MANAGE_MOBILE_PLAN);
                }

                final PackageManager pm = context.getPackageManager();

                // Remove Airplane Mode settings if it's a stationary device such as a TV.
                if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                    result.add(KEY_TOGGLE_AIRPLANE);
                }

                // proxy UI disabled until we have better app support
                result.add(KEY_PROXY_SETTINGS);

                // Disable Tethering if it's not allowed or if it's a wifi-only device
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (isSecondaryUser || !cm.isTetheringSupported()) {
                    result.add(KEY_TETHER_SETTINGS);
                }

                // Enable link to CMAS app settings depending on the value in config.xml.
                boolean isCellBroadcastAppLinkEnabled = context.getResources().getBoolean(
                        com.android.internal.R.bool.config_cellBroadcastAppLinks);
                try {
                    if (isCellBroadcastAppLinkEnabled) {
                        if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
                }
                if (isSecondaryUser || !isCellBroadcastAppLinkEnabled) {
                    result.add(KEY_CELL_BROADCAST_SETTINGS);
                }

                ///M: Reomve RCSE search if not support.
                if (!isAPKInstalled(context, RCSE_SETTINGS_INTENT)) {
                    result.add(KEY_RCSE_SETTINGS);
                }

                return result;
            }
        };

    ///M:
    private static boolean isAPKInstalled(Context context, String action) {
         Intent intent = new Intent(action);
         List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(intent, 0);
         return !(apps == null || apps.size() == 0);
    }

    ///M: WFC:set wfc summary @ {
    private void setWfcSummary(String summary) {
        mWFCSettingsPreference.setSummary(summary);
    }
    /// @}
}
