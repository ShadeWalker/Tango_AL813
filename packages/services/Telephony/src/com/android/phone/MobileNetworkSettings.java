/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import com.android.ims.ImsManager;
import com.android.ims.ImsException;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.IMobileNetworkSettingsExt;
import com.mediatek.settings.Enhanced4GLteSwitchPreference;
import com.mediatek.settings.TelephonyUtils;
import com.mediatek.settings.cdma.CdmaNetworkSettings;
import com.mediatek.settings.cdma.TelephonyUtilsEx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.ChildMode;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.RadioAccessFamily;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.Toast;
import java.util.Locale;
import android.os.SystemProperties;

/**
 * "Mobile network settings" screen.  This preference screen lets you
 * enable/disable mobile data, and control data roaming and other
 * network-specific mobile data features.  It's used on non-voice-capable
 * tablets as well as regular phone devices.
 *
 * Note that this PreferenceActivity is part of the phone app, even though
 * you reach it from the "Wireless & Networks" section of the main
 * Settings app.  It's not part of the "Call settings" hierarchy that's
 * available from the Phone app (see CallFeaturesSetting for that.)
 */
public class MobileNetworkSettings extends PreferenceActivity implements
        DialogInterface.OnClickListener, DialogInterface.OnDismissListener,
        Preference.OnPreferenceChangeListener {

    // debug data
    private static final String LOG_TAG = "NetworkSettings";
    private static final boolean DBG = true;
    public static final int REQUEST_CODE_EXIT_ECM = 17;

    // Number of active Subscriptions to show tabs
    private static final int TAB_THRESHOLD = 2;

    //String keys for preference lookup

    private static final String BUTTON_CELLULAR_DATA_ALWAYS_ON_KEY = "button_data_always_on_key";
    private SwitchPreference mButtonDataAlwaysOn;
    private static final String POWER_SAVING_ON = "power_saving_on";
    private static final String BUTTON_NETWORK_ALWAYS_ON_KEY = "button_network_always_on_key";
    private static final boolean isEnablePowerSaving = SystemProperties.getBoolean("ro.config.hw_power_saving", false);


    private static final String BUTTON_CELLULAR_DATA_KEY = "button_cellular_data_key";
    public static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
    private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
    private static final String BUTTON_CDMA_LTE_DATA_SERVICE_KEY = "cdma_lte_data_service_key";
    public static final String BUTTON_ENABLED_NETWORKS_KEY = "enabled_networks_key";
    private static final String BUTTON_4G_LTE_KEY = "enhanced_4g_lte";
    private static final String BUTTON_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";
    private static final String BUTTON_APN_EXPAND_KEY = "button_apn_key";
    private static final String BUTTON_OPERATOR_SELECTION_EXPAND_KEY = "button_carrier_sel_key";
    private static final String BUTTON_CARRIER_SETTINGS_KEY = "carrier_settings_key";
    private static final String BUTTON_CDMA_SYSTEM_SELECT_KEY = "cdma_system_select_key";
	private static final String BUTTON_WLAN_DATA_SWITCH_KEY = "wlan_data_switch_key";
	private static final String  myAction = "com.android.telephony.USER_ACTION";
    static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;

    //Information about logical "up" Activity
    private static final String UP_ACTIVITY_PACKAGE = "com.android.settings";
    private static final String UP_ACTIVITY_CLASS =
            "com.android.settings.Settings$WirelessSettingsActivity";

    private SubscriptionManager mSubscriptionManager;

    //UI objects
    private ListPreference mButtonPreferredNetworkMode;
    private ListPreference mButtonEnabledNetworks;
	private ListPreference mButtonWlanDataSwitch;
    private SwitchPreference mButtonCellularData;
    private SwitchPreference mButtonDataRoam;
    private SwitchPreference mButton4glte;
    private Preference mLteDataServicePref;

    private static final String iface = "rmnet0"; //TODO: this will go away
    private List<SubscriptionInfo> mActiveSubInfos;

    private UserManager mUm;
    private Phone mPhone,mPhone1,mPhone2;
    private MyHandler mHandler;
    private boolean mOkClicked;

    // We assume the the value returned by mTabHost.getCurrentTab() == slotId
    private TabHost mTabHost;

    //GsmUmts options and Cdma options
    GsmUmtsOptions mGsmUmtsOptions;
    CdmaOptions mCdmaOptions;

    private Preference mClickedPreference;
    private boolean mShow4GForLTE;
    private boolean mIsGlobalCdma;
    private boolean mUnavailable;

    /// Add for C2K OM features
    private CdmaNetworkSettings mCdmaNetworkSettings;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /*
         * Enable/disable the 'Enhanced 4G LTE Mode' when in/out of a call
         * and depending on TTY mode and TTY support over VoLTE.
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);
            Preference pref = getPreferenceScreen().findPreference(BUTTON_4G_LTE_KEY);
            if (pref != null) {
                pref.setEnabled((state == TelephonyManager.CALL_STATE_IDLE) &&
                        ImsManager.isNonTtyOrTtyOnVolteEnabled(getApplicationContext()));
            }
            updateScreenStatus();
        }
    };

    //This is a method implemented for DialogInterface.OnClickListener.
    //  Used to dismiss the dialogs when they come up.
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mPhone.setDataRoamingEnabled(true);
            mOkClicked = true;
        } else {
            // Reset the toggle
            mButtonDataRoam.setChecked(false);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        // Assuming that onClick gets called first
        mButtonDataRoam.setChecked(mOkClicked);
        /*HQ_yulifeng for data roaming state HQ01311843, 20151103,b*/
        if(SystemProperties.get("ro.hq.roam.with.card.latin_om").equals("1")){
            Log.d("MobileNetworkSettings","ylf[onDismiss]mOkClicked:"+mOkClicked);
            if(mOkClicked){
                int phoneSubId = mPhone.getSubId();
                boolean saveSuccess = false;
                saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE+ phoneSubId, 1);
                Log.d("MobileNetworkSettings","ylf[onDismiss]saveSuccess = " + saveSuccess +"phoneSubId:"+phoneSubId);
            }
        }
        /*HQ_yulifeng for data roaming state HQ01311843, 20151103,e*/
        /*HQ_yulifeng for data roaming state movistar HQ01508448, 20151205,begin*/
        if(SystemProperties.get("ro.hq.roam.with.card.movistar").equals("1")){
            Log.d("MobileNetworkSettings","ylf[onDismiss]mOkClicked:"+mOkClicked);
            if(mOkClicked){
                int phoneSubId = mPhone.getSubId();
                boolean saveSuccess = false;
                saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE+ phoneSubId, 1);
                Log.d("MobileNetworkSettings","ylf[onDismiss]saveSuccess = " + saveSuccess +"phoneSubId:"+phoneSubId);
            }
        }
        /*HQ_yulifeng for data roaming state movistar HQ01508448, 20151205,end*/
        /*HQ_yulifeng for data roaming state SW_GLOBAL_EUROPE_009  HQ01591289, 20151228,begin*/
        if(SystemProperties.get("ro.hq.roam.with.card.westeur").equals("1")){
            Log.d("MobileNetworkSettings","ylf[onDismiss]mOkClicked:"+mOkClicked);
            if(mOkClicked){
                int phoneSubId = mPhone.getSubId();
                boolean saveSuccess = false;
                saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE+ phoneSubId, 1);
                Log.d("MobileNetworkSettings","ylf[onDismiss]saveSuccess = " + saveSuccess +"phoneSubId:"+phoneSubId);
            }
        }
        /*HQ_yulifeng for data roaming state SW_GLOBAL_EUROPE_009  HQ01591289, 20151228,end*/
    }

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        /** TODO: Refactor and get rid of the if's using subclasses */
        final int phoneSubId = mPhone.getSubId();
        if (mCdmaNetworkSettings != null && mCdmaNetworkSettings.onPreferenceTreeClick(preferenceScreen, preference)) {
            return true;
        }
        /// M: Add for Plug-in @{
        if (mExt.onPreferenceTreeClick(preferenceScreen, preference)) {
            return true;
        } else
        /// @}
        if (preference.getKey().equals(BUTTON_4G_LTE_KEY)) {
            return true;
        } else if (mGsmUmtsOptions != null &&
                mGsmUmtsOptions.preferenceTreeClick(preference) == true) {
            return true;
        } else if (mCdmaOptions != null &&
                   mCdmaOptions.preferenceTreeClick(preference) == true) {
            if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {

                mClickedPreference = preference;

                // In ECM mode launch ECM app dialog
                startActivityForResult(
                    new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                    REQUEST_CODE_EXIT_ECM);
            }
            return true;
        } else if (preference == mButtonPreferredNetworkMode) {
            //displays the value taken from the Settings.System
            int settingsNetworkMode = android.provider.Settings.Global.getInt(mPhone.getContext().
                    getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            return true;
        } else if (preference == mLteDataServicePref) {
            String tmpl = android.provider.Settings.Global.getString(getContentResolver(),
                        android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL);
            if (!TextUtils.isEmpty(tmpl)) {
                TelephonyManager tm = (TelephonyManager) getSystemService(
                        Context.TELEPHONY_SERVICE);
                String imsi = tm.getSubscriberId();
                if (imsi == null) {
                    imsi = "";
                }
                final String url = TextUtils.isEmpty(tmpl) ? null
                        : TextUtils.expandTemplate(tmpl, imsi).toString();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } else {
                android.util.Log.e(LOG_TAG, "Missing SETUP_PREPAID_DATA_SERVICE_URL");
            }
            return true;
        }  else if (preference == mButtonEnabledNetworks) {
            int settingsNetworkMode = android.provider.Settings.Global.getInt(mPhone.getContext().
                    getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);
            mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
            return true;
        } else if (preference == mButtonDataAlwaysOn) {
            return true;           
        } else if (preference == mButtonCellularData) {
            // Do not disable the preference screen if the user clicks Cellular Data.
            return true;
        } else if (preference == mButtonDataRoam) {
            // Do not disable the preference screen if the user clicks Data roaming.
            return true;
        } else if(preference==mButtonWlanDataSwitch){
        		return true;
        }else {
            // if the button is anything but the simple toggle preference,
            // we'll need to disable all preferences to reject all click
            // events until the sub-activity's UI comes up.
            preferenceScreen.setEnabled(false);
            // Let the intents be launched by the Preference manager
            return false;
        }
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
            = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (DBG) log("onSubscriptionsChanged: start");
            /// M: add for hot swap @{
            if (TelephonyUtils.isHotSwapHanppened(
                    mActiveSubInfos, PhoneUtils.getActiveSubInfoList())) {
                log("onSubscriptionsChanged:hot swap hanppened");
                dissmissDialog(mButtonPreferredNetworkMode);
                dissmissDialog(mButtonEnabledNetworks);
                finish();
                return;
            }
            /// @}
            initializeSubscriptions();
            log("onSubscriptionsChanged: end");
        }
    };

    private void initializeSubscriptions() {
        int currentTab = 0;
        if (DBG) log("initializeSubscriptions:+");

        // Before updating the the active subscription list check
        // if tab updating is needed as the list is changing.
        List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        TabState state = isUpdateTabsNeeded(sil);

        // Update to the active subscription list
        mActiveSubInfos.clear();
        if (sil != null) {
            mActiveSubInfos.addAll(sil);
            /* M: remove for 3SIM feature
            // If there is only 1 sim then currenTab should represent slot no. of the sim.
            if (sil.size() == 1) {
                currentTab = sil.get(0).getSimSlotIndex();
            }*/
        }

        switch (state) {
            case UPDATE: {
                if (DBG) log("initializeSubscriptions: UPDATE");
                currentTab = mTabHost != null ? mTabHost.getCurrentTab() : mCurrentTab;

                setContentView(R.layout.network_settings);

                mTabHost = (TabHost) findViewById(android.R.id.tabhost);
                mTabHost.setup();

                // Update the tabName. Since the mActiveSubInfos are in slot order
                // we can iterate though the tabs and subscription info in one loop. But
                // we need to handle the case where a slot may be empty.

                /// M: change design for 3SIM feature @{
                for (int index = 0; index  < mActiveSubInfos.size(); index++) {
                    String tabName = String.valueOf(mActiveSubInfos.get(index).getDisplayName());
                    //add by liruihong for HQ01363844
                    String slotStr = getResources().getString((index == 0) ? R.string.slot_1 : R.string.slot_2);
                    tabName = slotStr;

                    if (DBG) {
                        log("initializeSubscriptions: tab=" + index + " name=" + tabName);
                    }

                    mTabHost.addTab(buildTabSpec(String.valueOf(index), tabName));
                }
                /// @}

                mTabHost.setOnTabChangedListener(mTabListener);
                mTabHost.setCurrentTab(currentTab);
                break;
            }
            case NO_TABS: {
                if (DBG) log("initializeSubscriptions: NO_TABS");

                if (mTabHost != null) {
                    mTabHost.clearAllTabs();
                    mTabHost = null;
                }
                setContentView(R.layout.network_settings);
                break;
            }
            case DO_NOTHING: {
                if (DBG) log("initializeSubscriptions: DO_NOTHING");
                if (mTabHost != null) {
                    currentTab = mTabHost.getCurrentTab();
                }
                break;
            }
        }
        
        //yanqing add for HQ01448212 start
        ListView listView = (ListView)findViewById(android.R.id.list);
        if (listView != null) {
            listView.setOverScrollMode(View.OVER_SCROLL_NEVER);            
        }
        //yanqing add for HQ01448212 end

        updatePhone(convertTabToSlot(currentTab));
        updateBody();
        if (DBG) log("initializeSubscriptions:-");
    }

    private enum TabState {
        NO_TABS, UPDATE, DO_NOTHING
    }
    private TabState isUpdateTabsNeeded(List<SubscriptionInfo> newSil) {
        TabState state = TabState.DO_NOTHING;
        if (newSil == null) {
            if (mActiveSubInfos.size() >= TAB_THRESHOLD) {
                if (DBG) log("isUpdateTabsNeeded: NO_TABS, size unknown and was tabbed");
                state = TabState.NO_TABS;
            }
        } else if (newSil.size() < TAB_THRESHOLD && mActiveSubInfos.size() >= TAB_THRESHOLD) {
            if (DBG) log("isUpdateTabsNeeded: NO_TABS, size went to small");
            state = TabState.NO_TABS;
        } else if (newSil.size() >= TAB_THRESHOLD && mActiveSubInfos.size() < TAB_THRESHOLD) {
            if (DBG) log("isUpdateTabsNeeded: UPDATE, size changed");
            state = TabState.UPDATE;
        } else if (newSil.size() >= TAB_THRESHOLD) {
            Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.iterator();
            for(SubscriptionInfo newSi : newSil) {
                SubscriptionInfo curSi = siIterator.next();
                if (!newSi.getDisplayName().equals(curSi.getDisplayName())) {
                    if (DBG) log("isUpdateTabsNeeded: UPDATE, new name=" + newSi.getDisplayName());
                    state = TabState.UPDATE;
                    break;
                }
            }
        }
        if (DBG) {
            log("isUpdateTabsNeeded:- " + state
                + " newSil.size()=" + ((newSil != null) ? newSil.size() : 0)
                + " mActiveSubInfos.size()=" + mActiveSubInfos.size());
        }
        return state;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            if (DBG) log("onTabChanged:");
            // The User has changed tab; update the body.
            updatePhone(convertTabToSlot(Integer.parseInt(tabId)));
            mCurrentTab = Integer.parseInt(tabId);
            updateBody();
        }
    };

    private void updatePhone(int slotId) {
        final SubscriptionInfo sir = findRecordBySlotId(slotId);
        if (sir != null) {
            mPhone = PhoneFactory.getPhone(
                    SubscriptionManager.getPhoneId(sir.getSubscriptionId()));
        }
        if (mPhone == null) {
            // Do the best we can
            mPhone = PhoneGlobals.getPhone();
        }
        if (DBG) log("updatePhone:- slotId=" + slotId + " sir=" + sir);
    }

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }

	private Context mContext;
	private boolean europemode=false;
	
    @Override
    protected void onCreate(Bundle icicle) {
        if (DBG) log("onCreate:+");
        /* begin: delete by donghongjing for HQ01332172 */
        //setTheme(R.style.Theme_Material_Settings);
        /* end: delete by donghongjing for HQ01332172 */
        super.onCreate(icicle);
		mContext = this;
        mHandler = new MyHandler();
        mUm = (UserManager) getSystemService(Context.USER_SERVICE);
        mSubscriptionManager = SubscriptionManager.from(this);

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            mUnavailable = true;
            setContentView(R.layout.telephony_disallowed_preference_screen);
            return;
        }

        addPreferencesFromResource(R.xml.network_setting);

        mButton4glte = (SwitchPreference)findPreference(BUTTON_4G_LTE_KEY);

        mButton4glte.setOnPreferenceChangeListener(this);

        //yanqing add 
        mButtonDataAlwaysOn = (SwitchPreference)findPreference(BUTTON_CELLULAR_DATA_ALWAYS_ON_KEY);
        mButtonDataAlwaysOn.setOnPreferenceChangeListener(this);



        try {
            //Context con = createPackageContext("com.android.systemui", 0);
            Context con = createPackageContext("com.android.settings", 0);//modify by gaoyuhao
            //int id = con.getResources().getIdentifier("config_show4GForLTE",
             //       "bool", "com.android.systemui");
			int id = con.getResources().getIdentifier("config_show4GForLTE",
                    "bool", "com.android.settings");//modify by gaoyuhao
            mShow4GForLTE = con.getResources().getBoolean(id);
            	//mShow4GForLTE =true;
        } catch (NameNotFoundException e) {
            loge("NameNotFoundException for show4GFotLTE");
            mShow4GForLTE = false;
        }

        //get UI object references
        PreferenceScreen prefSet = getPreferenceScreen();

        mButtonCellularData = (SwitchPreference) prefSet.findPreference(BUTTON_CELLULAR_DATA_KEY);
        mButtonDataRoam = (SwitchPreference) prefSet.findPreference(BUTTON_ROAMING_KEY);
        mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference(
                BUTTON_PREFERED_NETWORK_MODE);
        mButtonEnabledNetworks = (ListPreference) prefSet.findPreference(
                BUTTON_ENABLED_NETWORKS_KEY);
        //HQ_hushunli add for HQ01559476 begin
        if (SystemProperties.get("ro.hq.att.network.select").equals("1")) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imsi = tm.getSubscriberId();
            if (imsi != null && (imsi.startsWith("334090"))) {
                CharSequence[] entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto),getString(R.string.network_wcdma_gsm_auto),getString(R.string.network_wcdma_only)};
                CharSequence[] entryValues = new CharSequence[]{"9","3","2"};
                mButtonEnabledNetworks.setEntries(entries);
                mButtonEnabledNetworks.setEntryValues(entryValues);
            }
        }
        //HQ_hushunli add for HQ01559476 end
        /*HQ_guomiao add for HQ01311856 begin*/
        if (SystemProperties.get("ro.hq.phone.pref.network.type").equals("1")) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imsi = tm.getSubscriberId();
            if (imsi != null && (imsi.startsWith("732103") || imsi.startsWith("732111"))) {
                CharSequence[] entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto),
                        getString(R.string.network_wcdma_gsm_auto),getString(R.string.network_wcdma_only)};
                CharSequence[] entryValues = new CharSequence[]{"9","3","2"};
                mButtonEnabledNetworks.setEntries(entries);
                mButtonEnabledNetworks.setEntryValues(entryValues);
            }
        }
        /*HQ_guomiao add for HQ01311856 end*/
        /*HQ_guomiao add for HQ01453066 begin*/
        if (SystemProperties.get("ro.hq.tigo.phone.net.select").equals("1")) {
            CharSequence[] entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto),
                    getString(R.string.network_wcdma_gsm_auto),getString(R.string.network_wcdma_only)};
            CharSequence[] entryValues = new CharSequence[]{"9","3","2"};
            mButtonEnabledNetworks.setEntries(entries);
            mButtonEnabledNetworks.setEntryValues(entryValues);
        }
        /*HQ_guomiao add for HQ01453066 end*/
        /*HQ_guomiao add for HQ01508465 begin*/
        if (SystemProperties.get("ro.hq.movistar.network.select").equals("1")) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imsi = tm.getSubscriberId();
            if (imsi != null) {
                CharSequence[] entries = null;
                CharSequence[] entryValues = null;
                if (imsi.startsWith("72207")/*Argentina*/
                        || imsi.startsWith("73002")/*Chile*/
                        || imsi.startsWith("732123")/*Colombia*/
                        || imsi.startsWith("71204")/*Costa Rica */) {
                    entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto_movistar_72207),
                            getString(R.string.network_gsm_only_movistar_72207),
                            getString(R.string.network_wcdma_only_movistar_72207)};
                    entryValues = new CharSequence[]{"9","1","2"};
                } else if (imsi.startsWith("33403") || imsi.startsWith("334030")/*Mexico*/
                        || imsi.startsWith("71606")/*Peru*/
                        || imsi.startsWith("74807")/*Uruguay*/
                        || imsi.startsWith("73404")/*Venezuela*/) {
                    entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto_movistar_33403),
                            getString(R.string.network_gsm_only_movistar_33403),
                            getString(R.string.network_wcdma_only_movistar_33403)};
                    entryValues = new CharSequence[]{"9","1","2"};
                } else if (imsi.startsWith("74000")/*Ecuador*/
                        || imsi.startsWith("70604") || imsi.startsWith("706040")/*El Salvador*/
                        || imsi.startsWith("70403") || imsi.startsWith("704030")/*Guatemala*/
                        || imsi.startsWith("71030") || imsi.startsWith("710300")/*Nicaragua*/
                        || imsi.startsWith("71402") || imsi.startsWith("714020")/*Panama*/) {
                    entries = new CharSequence[]{getString(R.string.network_lte_wcdma_gsm_auto_movistar_74000),
                            getString(R.string.network_wcdma_gsm_auto_movistar_74000),
                            getString(R.string.network_gsm_only_movistar_74000),
                            getString(R.string.network_wcdma_only_movistar_74000)};
                    entryValues = new CharSequence[]{"9","3","1","2"};
                }
                if (entries != null && entryValues != null) {
                    mButtonEnabledNetworks.setEntries(entries);
                    mButtonEnabledNetworks.setEntryValues(entryValues);
                }
            }
        }
        /*HQ_guomiao add for HQ01508465 end*/
        /*caohaolin add for HQ01455083 begin*/
        if (SystemProperties.get("ro.hq.network.mode.claro").equals("1")) {
            CharSequence[] entries = new CharSequence[]{getString(R.string.network_automatic_claro),getString(R.string.network_wcdma_4G_claro),getString(R.string.network_wcdma_gsm_claro),getString(R.string.network_wcdma_only_claro),getString(R.string.network_gsm_only_claro)};
            CharSequence[] entryValues = new CharSequence[]{"9","12","3","2","1"};
            mButtonEnabledNetworks.setEntries(entries);
            mButtonEnabledNetworks.setEntryValues(entryValues);
        }
        /*caohaolin add for HQ01455083 end*/
        //add by wanghui  for al812 
        
        
/*    int NETWORK_MODE_WCDMA_PREF     = 0;  GSM/WCDMA (WCDMA preferred) 
    int NETWORK_MODE_GSM_ONLY       = 1;  GSM only 
    int NETWORK_MODE_WCDMA_ONLY     = 2;  WCDMA only 
    int NETWORK_MODE_GSM_UMTS       = 3;  GSM/WCDMA (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu
    int NETWORK_MODE_CDMA           = 4;  CDMA and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu
    int NETWORK_MODE_CDMA_NO_EVDO   = 5;  CDMA only 
    int NETWORK_MODE_EVDO_NO_CDMA   = 6;  EvDo only 
    int NETWORK_MODE_GLOBAL         = 7;  GSM/WCDMA, CDMA, and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu
    int NETWORK_MODE_LTE_CDMA_EVDO  = 8;  LTE, CDMA and EvDo 
    int NETWORK_MODE_LTE_GSM_WCDMA  = 9;  LTE, GSM/WCDMA 
    int NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10;  LTE, CDMA, EvDo, GSM/WCDMA 
    int NETWORK_MODE_LTE_ONLY       = 11;  LTE Only mode. 
    int NETWORK_MODE_LTE_WCDMA      = 12;  LTE/WCDMA 
*/

        
/*        支持LTE时：
        4G/3G/2G auto 【默认】  9
        3G/2G auto   3
        3G only   2
        2G only   1   */
/*      
        英国O2（23410）要求在4G手机保留2G only选项，并且2G only后面增加一条说明：voice&sms only. 只支持sms和call,
        
        西班牙vodafone（MCCMNC 21401)没有2G网络，所以当插入西班牙vodafone（MCCMNC 21401)的卡的时候，去掉2G only的选项；
        英国EE（23430,23431,23432,23433,23434,23486），爱尔兰H3G(27205)要求在4G和3G手机去除所有2G only的选项
        英国和黄H3G（MCCMNC 23420，23594)没有2G网络，所以当插入和黄H3G（MCCMNC 23420，23594)的卡的时候，去掉2G only的选项；
        爱尔兰和黄h3g（272/05），O2（272/02，被和黄收购）没有2G网络，所以当插入和黄h3g（272/05），O2（272/02，被和黄收购）的卡的时候，去掉2G only的选项；
        
        俄罗斯（MCC：250）要求在4G手机上添加4G only 选项
        西班牙Spain TME（21407），Yoigo（21404）要求去掉3G only */
        if (SystemProperties.get("ro.hq.network.europemode").equals("1")) {
        	Log.i("tang", "europemode");
        	europemode=true;
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imsi = tm.getSubscriberId();
            if (imsi != null) {
                CharSequence[] entries = null;
                CharSequence[] entryValues = null;
                entries = new CharSequence[]{getString(R.string.network_4G),
                        getString(R.string.network_3G),
                        getString(R.string.network_3G_only),getString(R.string.network_2G)};
                entryValues = new CharSequence[]{"9","3","2","1"};
//                西班牙vodafone（MCCMNC 21401)没有2G网络，所以当插入西班牙vodafone（MCCMNC 21401)的卡的时候，去掉2G only的选项；
//                英国EE（23430,23431,23432,23433,23434,23486），爱尔兰H3G(27205)要求在4G和3G手机去除所有2G only的选项
//                英国和黄H3G（MCCMNC 23420，23594)没有2G网络，所以当插入和黄H3G（MCCMNC 23420，23594)的卡的时候，去掉2G only的选项；
//                爱尔兰和黄h3g（272/05），O2（272/02，被和黄收购）没有2G网络，所以当插入和黄h3g（272/05），O2（272/02，被和黄收购）的卡的时候，去掉2G only的选项；
				if (imsi.startsWith("21401") ||imsi.startsWith("23430") || imsi.startsWith("23431")
						|| imsi.startsWith("23432") || imsi.startsWith("23433")
						|| imsi.startsWith("23434") || imsi.startsWith("23486")
						||imsi.startsWith("23420")||imsi.startsWith("23594")||imsi.startsWith("27205")||imsi.startsWith("27202")) {
	                entries = new CharSequence[]{getString(R.string.network_4G),
	                        getString(R.string.network_3G),
	                        getString(R.string.network_3G_only)};
	                entryValues = new CharSequence[]{"9","3","2"};//
                } 
//		        英国O2（23410）要求在4G手机保留2G only选项，并且2G only后面增加一条说明：voice&sms only. 只支持sms和call,
				else if (imsi.startsWith("23410")) {
	                entries = new CharSequence[]{getString(R.string.network_4G),
	                        getString(R.string.network_3G),
	                        getString(R.string.network_3G_only),getString(R.string.network_2G)+"voice&sms only"};
	                entryValues = new CharSequence[]{"9","3","2","1"};
                } 
				
//		        俄罗斯（MCC：250）要求在4G手机上添加4G only 选项
				else if (imsi.startsWith("250")) {
	                entries = new CharSequence[]{getString(R.string.network_4G_only),getString(R.string.network_4G),
	                        getString(R.string.network_3G),
	                        getString(R.string.network_3G_only),getString(R.string.network_2G)};
	                entryValues = new CharSequence[]{"11","9","3","2","1"};
                }
//				  西班牙Spain TME（21407），Yoigo（21404）要求去掉3G only
				else if (imsi.startsWith("21407")||imsi.startsWith("21404")) {
				     entries = new CharSequence[]{getString(R.string.network_4G),
		                        getString(R.string.network_3G),getString(R.string.network_2G)};
		                entryValues = new CharSequence[]{"9","3","1"};
                }
                if (entries != null && entryValues != null) {
                    mButtonEnabledNetworks.setEntries(entries);
                    mButtonEnabledNetworks.setEntryValues(entryValues);
                }
            }
        }else {
			europemode=false;
		}
        
        
        
        
        
        if(!(SystemProperties.get("ro.hq.wifi.disconnect.reminder").equals("1"))){
                prefSet.removePreference(findPreference(BUTTON_WLAN_DATA_SWITCH_KEY));
            }  
            else{
                mButtonWlanDataSwitch = (ListPreference) prefSet.findPreference(BUTTON_WLAN_DATA_SWITCH_KEY);
                mButtonWlanDataSwitch.setOnPreferenceChangeListener(this);
				//add by wanghui for defaultvalue design for HQ01533601
				if(SystemProperties.get("ro.hq.wifi.switch.default").equals("1")){
				    if(mButtonWlanDataSwitch.getValue() == null){
					  mButtonWlanDataSwitch.setValueIndex(0);
					}
				}else if(SystemProperties.get("ro.hq.latin.wifi.switch.default").equals("1")||SystemProperties.get("ro.product.name").equals("TAG-L03")
				          ||SystemProperties.get("ro.product.name").equals("TAG-L21")
					  ||SystemProperties.get("ro.product.name").equals("TAG-L22")){   //add by chiguoqing
					if(mButtonWlanDataSwitch.getValue() == null){
					  mButtonWlanDataSwitch.setValueIndex(1);//add by wanghui for HQ01557482
					}				
				 }else{
                      mButtonWlanDataSwitch.setValueIndex(2); 
				}
				String saveValue = mContext.getResources().getString(R.string.pre_wlan_state);
	            mButtonWlanDataSwitch.setSummary(saveValue+mButtonWlanDataSwitch.getEntry());
            }
        //add by wanghui  for al812 
        mButtonDataRoam.setOnPreferenceChangeListener(this);
        mLteDataServicePref = prefSet.findPreference(BUTTON_CDMA_LTE_DATA_SERVICE_KEY);
        mButtonCellularData.setOnPreferenceChangeListener(this);
        // Initialize mActiveSubInfo
        int max = mSubscriptionManager.getActiveSubscriptionInfoCountMax();
        mActiveSubInfos = new ArrayList<SubscriptionInfo>(max);
        /// M: for screen rotate
        if (icicle != null) {
            mCurrentTab = icicle.getInt(CURRENT_TAB);
        }

        initializeSubscriptions();

        initIntentFilter();
        registerReceiver(mReceiver, mIntentFilter);
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        TelephonyManager.getDefault().listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		/*add by wanghui for al812 begin*/
        String list_state = Settings.System.getString(getContentResolver(),"flag_state");
		if(list_state == null){
			list_state = "0";
			}
		if(list_state.equals("1")){
            if (getPreferenceScreen().findPreference(BUTTON_WLAN_DATA_SWITCH_KEY) != null)  {
                          String keyValue  = "3";
                          Log.d("LOG_TAG","keyValue"+keyValue);
                          mButtonWlanDataSwitch.setValue(keyValue);
                          String strAuto = mContext.getResources().getString(R.string.auto_wlan_state);
                          mButtonWlanDataSwitch.setSummary(strAuto);
						  Settings.System.putString(getContentResolver(),"flag_state","0");
                      }
		}
		/*add by wanghui for al812 end*/
        if (DBG) log("onCreate:-");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCdmaNetworkSettings != null) {
            mCdmaNetworkSettings.onResume();
        }

        if (DBG) log("onResume:+");

        if (mUnavailable) {
            if (DBG) log("onResume:- ignore mUnavailable == false");
            return;
        }

        // upon resumption from the sub-activity, make sure we re-enable the
        // preferences.
        // getPreferenceScreen().setEnabled(true);

        // Set UI state in onResume because a user could go home, launch some
        // app to change this setting's backend, and re-launch this settings app
        // and the UI state would be inconsistent with actual state
        mButtonCellularData.setChecked(mPhone.getDataEnabled());
        mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());

        //yanqing
        if (isEnablePowerSaving) {
            int power_saving_on = 0;
            try {
                power_saving_on = android.provider.Settings.System.getInt(mPhone.getContext().getContentResolver(),POWER_SAVING_ON, 0);
            } catch (Exception e) {
                if (DBG) Log.v(LOG_TAG, "onResume->NoExtAPIException!");
            }
            if (power_saving_on == 1) {
                mButtonDataAlwaysOn.setChecked(false);
            } else {
                mButtonDataAlwaysOn.setChecked(true);
            }
            // if (mPhone.getDataEnabled()) {
            //     mButtonDataAlwaysOn.setEnabled(true);
            // }
            // else {
            //     mButtonDataAlwaysOn.setEnabled(false);
            // }
        }


        if (getPreferenceScreen().findPreference(BUTTON_PREFERED_NETWORK_MODE) != null)  {
            mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
        }

        if (getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null)  {
            mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
        }

        /** M: Add For [MTK_Enhanced4GLTE]
        if (ImsManager.isVolteEnabledByPlatform(this)
                && ImsManager.isVolteProvisionedOnDevice(this)) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        mButton4glte.setChecked(ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this)
                && ImsManager.isNonTtyOrTtyOnVolteEnabled(this));
        // NOTE: The button will be enabled/disabled in mPhoneStateListener*/
        /**M: Move this to onCreate @{
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        @} */
        /// M: For screen update
        updateScreenStatus();
        /// M: For plugin to update UI
        mExt.onResume();

        if (DBG) log("onResume:-");

    }

    private void updateBody() {
        final Context context = getApplicationContext();
        PreferenceScreen prefSet = getPreferenceScreen();
        boolean isLteOnCdma = mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;
        final int phoneSubId = mPhone.getSubId();

        if (DBG) {
            log("updateBody: isLteOnCdma=" + isLteOnCdma + " phoneSubId=" + phoneSubId);
        }

        if (prefSet != null) {
            prefSet.removeAll();
            prefSet.addPreference(mButtonCellularData);
            if (isEnablePowerSaving) {
                prefSet.addPreference(mButtonDataAlwaysOn);
            }
            prefSet.addPreference(mButtonDataRoam);
            prefSet.addPreference(mButtonPreferredNetworkMode);
            if(SystemProperties.get("ro.hq.wifi.disconnect.reminder").equals("1")){
                prefSet.addPreference(mButtonWlanDataSwitch);
            }
            prefSet.addPreference(mButtonEnabledNetworks);
		   //add by zhangjinqiang for HQ01811982 -start
		   if(mButton4glte!=null){
            		prefSet.addPreference(mButton4glte);
		   }
		   //add by zhangjinqiang for HQ01811982 -end
        }

        int settingsNetworkMode = android.provider.Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                preferredNetworkMode);

        mIsGlobalCdma = isLteOnCdma && getResources().getBoolean(R.bool.config_show_cdma);
        int shouldHideCarrierSettings = android.provider.Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                android.provider.Settings.Global.HIDE_CARRIER_NETWORK_SETTINGS, 0);
        if (shouldHideCarrierSettings == 1 ) {
            Log.i(LOG_TAG, "updatebody shouldHideCarrierSettings==1 remove");
            prefSet.removePreference(mButtonPreferredNetworkMode);
            prefSet.removePreference(mButtonEnabledNetworks);
            prefSet.removePreference(mLteDataServicePref);
        } else if (getResources().getBoolean(R.bool.world_phone) == true) {
            Log.i(LOG_TAG, "updatebody is world phone");

            prefSet.removePreference(mButtonEnabledNetworks);
            // set the listener for the mButtonPreferredNetworkMode list preference so we can issue
            // change Preferred Network Mode.
            mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);

            mCdmaOptions = new CdmaOptions(this, prefSet, mPhone);
            mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, phoneSubId);
        } else {
            Log.i(LOG_TAG, "updatebody is not world phone");
            prefSet.removePreference(mButtonPreferredNetworkMode);
            final int phoneType = mPhone.getPhoneType();
            if (TelephonyUtilsEx.isCDMAPhone(mPhone)) {//HQ_hushunli 2015-09-10 modify for HQ01300994
                Log.i(LOG_TAG, "phoneType == PhoneConstants.PHONE_TYPE_CDMA");
                mCdmaOptions = new CdmaOptions(this, prefSet, mPhone);
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                Log.i(LOG_TAG, "phoneType == PhoneConstants.PHONE_TYPE_GSM");
                mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, phoneSubId);
            }

            /**if (TelephonyUtilsEx.isCDMAPhone(mPhone)) {
                Log.i(LOG_TAG, "phoneType == PhoneConstants.PHONE_TYPE_CDMA");

                int lteForced = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.LTE_SERVICE_FORCED + mPhone.getSubId(),
                        0);

                if (isLteOnCdma) {
                    if (lteForced == 0) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_cdma_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_cdma_values);
                    } else {
                        switch (settingsNetworkMode) {
                            case Phone.NT_MODE_CDMA:
                            case Phone.NT_MODE_CDMA_NO_EVDO:
                            case Phone.NT_MODE_EVDO_NO_CDMA:
                                mButtonEnabledNetworks.setEntries(
                                        R.array.enabled_networks_cdma_no_lte_choices);
                                mButtonEnabledNetworks.setEntryValues(
                                        R.array.enabled_networks_cdma_no_lte_values);
                                break;
                            case Phone.NT_MODE_GLOBAL:
                            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                            case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                            case Phone.NT_MODE_LTE_ONLY:
                                mButtonEnabledNetworks.setEntries(
                                        R.array.enabled_networks_cdma_only_lte_choices);
                                mButtonEnabledNetworks.setEntryValues(
                                        R.array.enabled_networks_cdma_only_lte_values);
                                break;
                            default:
                                mButtonEnabledNetworks.setEntries(
                                        R.array.enabled_networks_cdma_choices);
                                mButtonEnabledNetworks.setEntryValues(
                                        R.array.enabled_networks_cdma_values);
                                break;
                        }
                    }
                }
                mCdmaOptions = new CdmaOptions(this, prefSet, mPhone);

                // In World mode force a refresh of GSM Options.
                if (isWorldMode()) {
                    mGsmUmtsOptions = null;
                }
                /// M: support for cdma @{
                if (FeatureOption.isMtk3gDongleSupport()) {
                    PreferenceScreen activateDevice = (PreferenceScreen)
                            prefSet.findPreference(BUTTON_CDMA_ACTIVATE_DEVICE_KEY);
                    if (activateDevice != null) {
                        prefSet.removePreference(activateDevice);
                    }
                }
                /// @}
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                Log.i(LOG_TAG, "phoneType == PhoneConstants.PHONE_TYPE_GSM");
                if (!getResources().getBoolean(R.bool.config_prefer_2g)
                        && !FeatureOption.isMtkLteSupport()) {
                    mButtonEnabledNetworks.setEntries(
                            R.array.enabled_networks_except_gsm_lte_choices);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.enabled_networks_except_gsm_lte_values);
                } else if (!getResources().getBoolean(R.bool.config_prefer_2g)) {
                    int select = (mShow4GForLTE == true) ?
                        R.array.enabled_networks_except_gsm_4g_choices
                        : R.array.enabled_networks_except_gsm_choices;
                    mButtonEnabledNetworks.setEntries(select);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.enabled_networks_except_gsm_values);
                } else if (!FeatureOption.isMtkLteSupport()) {
                    mButtonEnabledNetworks.setEntries(
                            R.array.enabled_networks_except_lte_choices);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.enabled_networks_except_lte_values);
                } else if (mIsGlobalCdma) {
                    mButtonEnabledNetworks.setEntries(
                            R.array.enabled_networks_cdma_choices);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.enabled_networks_cdma_values);
                } else {
                    int select = (mShow4GForLTE == true) ? R.array.enabled_networks_4g_choices
                            : R.array.enabled_networks_choices;
                    mButtonEnabledNetworks.setEntries(select);
                    mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_values);
                }
                mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, phoneSubId);
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
            if (isWorldMode()) {
                mButtonEnabledNetworks.setEntries(
                        R.array.preferred_network_mode_choices_world_mode);
                mButtonEnabledNetworks.setEntryValues(
                        R.array.preferred_network_mode_values_world_mode);
            }*/
            mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
            if (DBG) log("settingsNetworkMode: " + settingsNetworkMode);
        }

        final boolean missingDataServiceUrl = TextUtils.isEmpty(
                android.provider.Settings.Global.getString(getContentResolver(),
                        android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL));
        if (!isLteOnCdma || missingDataServiceUrl) {
            prefSet.removePreference(mLteDataServicePref);
        } else {
            android.util.Log.d(LOG_TAG, "keep ltePref");
        }

        /// M: add mtk feature.
        onCreateMTK(prefSet);

        // Enable enhanced 4G LTE mode settings depending on whether exists on platform
        /** M: Add For [MTK_Enhanced4GLTE] @{
        if (!(ImsManager.isVolteEnabledByPlatform(this)
                && ImsManager.isVolteProvisionedOnDevice(this))) {
            Preference pref = prefSet.findPreference(BUTTON_4G_LTE_KEY);
            if (pref != null) {
                prefSet.removePreference(pref);
            }
        }
        @} */

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final boolean isSecondaryUser = UserHandle.myUserId() != UserHandle.USER_OWNER;
        // Enable link to CMAS app settings depending on the value in config.xml.
        final boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                com.android.internal.R.bool.config_cellBroadcastAppLinks);
        if (isSecondaryUser || !isCellBroadcastAppLinkEnabled
                || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
            if (ps != null) {
                root.removePreference(ps);
            }
        }

		//add by zhangjinqiang for HQ01827724 -start
		if(mActiveSubInfos!=null&&mActiveSubInfos.size()==2){
			final SubscriptionInfo sir1 = findRecordBySlotId(0);
			final SubscriptionInfo sir2 = findRecordBySlotId(1);
			if(sir1!=null&&sir2!=null){
				 mPhone1 = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir1.getSubscriptionId()));
				 mPhone2 = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir2.getSubscriptionId()));
			}
			if(mPhone1!=null&&mPhone1.getDataEnabled()
				&&mPhone2!=null&&mPhone2.getDataEnabled()
				&&mPhone.getSubId()!=SubscriptionManager.getDefaultDataSubId()){
				setMobileDataEnabled(mPhone.getSubId(),false);
				mButtonCellularData.setChecked(false);
			}else{
				mButtonCellularData.setChecked(mPhone.getDataEnabled());
			}
		}else{
				mButtonCellularData.setChecked(mPhone.getDataEnabled());
		}
		//add by zhangjinqiang for HQ01827724-end
				
        	//mButtonCellularData.setChecked(mPhone.getDataEnabled());
        // Get the networkMode from Settings.System and displays it
        mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
        // M: if is not 3/4G phone, init the preference with gsm only type @{
        if (!isCapabilityPhone(mPhone)) {
            settingsNetworkMode = Phone.NT_MODE_GSM_ONLY;
            log("init non-capable phone with gsm only");
        }
        // @}
        mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
        mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
        UpdatePreferredNetworkModeSummary(settingsNetworkMode);
        UpdateEnabledNetworksValueAndSummary(settingsNetworkMode);
        // Display preferred network type based on what modem returns b/18676277
        /// M: no need set mode here
        //mPhone.setPreferredNetworkType(settingsNetworkMode, mHandler
        //        .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));

        /**
         * Enable/disable depending upon if there are any active subscriptions.
         *
         * I've decided to put this enable/disable code at the bottom as the
         * code above works even when there are no active subscriptions, thus
         * putting it afterwards is a smaller change. This can be refined later,
         * but you do need to remember that this all needs to work when subscriptions
         * change dynamically such as when hot swapping sims.

        boolean hasActiveSubscriptions = mActiveSubInfos.size() > 0;
        mButtonDataRoam.setEnabled(hasActiveSubscriptions);
        mButtonPreferredNetworkMode.setEnabled(hasActiveSubscriptions);
        mButtonEnabledNetworks.setEnabled(hasActiveSubscriptions);
        mButton4glte.setEnabled(hasActiveSubscriptions);
        mLteDataServicePref.setEnabled(hasActiveSubscriptions);
        Preference ps;
        PreferenceScreen root = getPreferenceScreen();
        ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
        if (ps != null) {
            ps.setEnabled(hasActiveSubscriptions);
        }
        ps = findPreference(BUTTON_APN_EXPAND_KEY);
        if (ps != null) {
            ps.setEnabled(hasActiveSubscriptions);
        }
        ps = findPreference(BUTTON_OPERATOR_SELECTION_EXPAND_KEY);
        if (ps != null) {
            ps.setEnabled(hasActiveSubscriptions);
        }
        ps = findPreference(BUTTON_CARRIER_SETTINGS_KEY);
        if (ps != null) {
            ps.setEnabled(hasActiveSubscriptions);
        }
        ps = findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY);
        if (ps != null) {
            ps.setEnabled(hasActiveSubscriptions);
        }*/

        /// M: For C2K solution 1.5 @{
        if (!FeatureOption.isMtkSvlteSolution2Support() && TelephonyUtilsEx.isCGCardInserted()) {
            if (TelephonyUtilsEx.isCapabilityOnGCard()
                    && !TelephonyUtilsEx.isGCardInserted(mPhone.getPhoneId())) {
                PreferenceScreen prefScreen = getPreferenceScreen();
                for (int i = 0; i < prefScreen.getPreferenceCount(); i++) {
                    Preference pref = prefScreen.getPreference(i);
                    pref.setEnabled(false);
                }
            } else {
                if(mButtonDataRoam != null) {
                    mButtonDataRoam.setEnabled(true);
                }
            }
        }
        /// @}
    }

    /* M: move unregister Subscriptions Change Listener to onDestory
    @Override
    protected void onPause() {
        super.onPause();
        if (DBG) log("onPause:+");

        if (ImsManager.isVolteEnabledByPlatform(this)
                && ImsManager.isVolteProvisionedOnDevice(this)) {

        mSubscriptionManager
            .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        if (DBG) log("onPause:-");
    }*/

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes specifically on CLIR.
     *
     * @param preference is the preference to be changed, should be mButtonCLIR.
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final int phoneSubId = mPhone.getSubId();
        if (onPreferenceChangeMTK(preference, objValue)) {
            return true;
        }
        if (preference == mButtonPreferredNetworkMode) {
            //NOTE onPreferenceChange seems to be called even if there is no change
            //Check if the button value is changed from the System.Setting
            mButtonPreferredNetworkMode.setValue((String) objValue);
            int buttonNetworkMode;
            buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);
            log("onPreferenceChange buttonNetworkMode:"
                    + buttonNetworkMode + " settingsNetworkMode:" + settingsNetworkMode);
            if (buttonNetworkMode != settingsNetworkMode) {
                int modemNetworkMode;
                // if new mode is invalid ignore it
                switch (buttonNetworkMode) {
                    case Phone.NT_MODE_WCDMA_PREF:
                    case Phone.NT_MODE_GSM_ONLY:
                    case Phone.NT_MODE_WCDMA_ONLY:
                    case Phone.NT_MODE_GSM_UMTS:
                    case Phone.NT_MODE_CDMA:
                    case Phone.NT_MODE_CDMA_NO_EVDO:
                    case Phone.NT_MODE_EVDO_NO_CDMA:
                    case Phone.NT_MODE_GLOBAL:
                    case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    case Phone.NT_MODE_LTE_GSM_WCDMA:
                    case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    case Phone.NT_MODE_LTE_ONLY:
                    case Phone.NT_MODE_LTE_WCDMA:
                        // This is one of the modes we recognize
                        modemNetworkMode = buttonNetworkMode;
                        break;
                    default:
                        loge("Invalid Network Mode (" + buttonNetworkMode + ") chosen. Ignore.");
                        return true;
                }

                mButtonPreferredNetworkMode.setValue(Integer.toString(modemNetworkMode));
                mButtonPreferredNetworkMode.setSummary(mButtonPreferredNetworkMode.getEntry());
                log(":::onPreferenceChange: summary: " + mButtonPreferredNetworkMode.getEntry());
                /* M: For ALPS01911001 Don't set Network mode to DB, till return successfully,
                 * So the set DB action will move to "handleSetPreferredNetworkTypeResponse" @{
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        buttonNetworkMode );
                */
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.USER_PREFERRED_NETWORK_MODE + phoneSubId,
                        buttonNetworkMode);
                //Set the modem network mode
                mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                        .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
            }
        } else if (preference == mButtonEnabledNetworks) {
            mButtonEnabledNetworks.setValue((String) objValue);
            int buttonNetworkMode;
            buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
            if (DBG) log("buttonNetworkMode: " + buttonNetworkMode);
            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);
            if (buttonNetworkMode != settingsNetworkMode) {
                int modemNetworkMode;
                // if new mode is invalid ignore it
                switch (buttonNetworkMode) {
                    case Phone.NT_MODE_WCDMA_PREF:
                    case Phone.NT_MODE_GSM_ONLY:
                    case Phone.NT_MODE_WCDMA_ONLY:
                    case Phone.NT_MODE_GSM_UMTS:
                    case Phone.NT_MODE_LTE_GSM_WCDMA:
                    case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    case Phone.NT_MODE_CDMA:
                    case Phone.NT_MODE_CDMA_NO_EVDO:
                    case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    case Phone.NT_MODE_LTE_WCDMA:
                    case 11:

                        // This is one of the modes we recognize
                        modemNetworkMode = buttonNetworkMode;
                        break;
                    default:
                        loge("Invalid Network Mode (" + buttonNetworkMode + ") chosen. Ignore.");
                        return true;
                }

                UpdateEnabledNetworksValueAndSummary(buttonNetworkMode);
                /* M: For ALPS01911001 Don't set Network mode to DB, till return successfully,
                 * So the set DB action will move to "handleSetPreferredNetworkTypeResponse" @{
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        buttonNetworkMode );
                */
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.USER_PREFERRED_NETWORK_MODE + phoneSubId,
                        buttonNetworkMode);
                //Set the modem network mode
                mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                        .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
            }
        } else if (preference == mButton4glte) {
            SwitchPreference ltePref = (SwitchPreference)preference;
            ltePref.setChecked(!ltePref.isChecked());
            ImsManager.setEnhanced4gLteModeSetting(this, ltePref.isChecked());
        } else if (preference == mButtonDataAlwaysOn) {
            // mClickOnWhich = mButtonDataAlwaysOn;
            if (!mButtonDataAlwaysOn.isChecked()) {
                setMobileDataAlwaysOn(mPhone.getSubId(), true);
            } else {
                // First confirm with a warning dialog about charges
                mOkClicked = false;
                mButtonDataAlwaysOn.setChecked(false);
                showConfirmDataAlwaysOn();
            }
            return true;
        } else if (preference == mButtonCellularData) {
	        if(isProhibitDataServices() && isChildModeOn()) {
                Toast.makeText(this, R.string.childmode_network_forbid_alert,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
          
            final boolean dataEnabled = !mButtonCellularData.isChecked();
            if (DBG) log("onPreferenceChange: preference == mButtonCellularData. dataEnabled:"+dataEnabled);
            if (dataEnabled) {
                // If we are showing the Sim Card tile then we are a Multi-Sim device.
                if (showSimCardTile(this)) {
                    handleMultiSimDataDialog();
                } else {
                    setMobileDataEnabled(mPhone.getSubId(), true);
                }
                // mButtonDataAlwaysOn.setEnabled(true);
            } else {
                // old code : mExt.needToShowDialog()
                if (true) {
                    showConfirmDataDisableDlg();
                } else {
                    setMobileDataEnabled(mPhone.getSubId(), false);
                }
            }
            return true;
        } else if (preference == mButtonDataRoam) {
            if (DBG) log("onPreferenceChange: preference == mButtonDataRoam.");

            //normally called on the toggle click
            if (!mButtonDataRoam.isChecked()) {
                // First confirm with a warning dialog about charges
                mOkClicked = false;
                /// M:Add for plug-in @{
                /* Google Code, delete by MTK
                new AlertDialog.Builder(this).setMessage(
                        getResources().getString(R.string.roaming_warning))
                        .setTitle(android.R.string.dialog_alert_title)
                */
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getResources().getString(R.string.roaming_warning))
                        .setTitle(android.R.string.dialog_alert_title);
                mExt.customizeAlertDialog(mButtonDataRoam, builder);
                builder.setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(android.R.string.yes, this)
                        .setNegativeButton(android.R.string.no, this)
                        .show()
                        .setOnDismissListener(this);
                /// @}
            } else {
                mPhone.setDataRoamingEnabled(false);
                /*HQ_yulifeng for data roaming state HQ01311843, 20151103,b*/
                if(SystemProperties.get("ro.hq.roam.with.card.latin_om").equals("1")){
                    boolean saveSuccess = false;
                    saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE + phoneSubId, 1);
                    Log.d("MobileNetworkSettings","ylf[onPreferenceChange]saveSuccess = " + saveSuccess+"phoneSubId:"+phoneSubId);
                }
                /*HQ_yulifeng for data roaming state HQ01311843, 20151103,e*/
                /*HQ_yulifeng for data roaming state movistar HQ01508448, 20151205,begin*/
                if(SystemProperties.get("ro.hq.roam.with.card.movistar").equals("1")){
                    boolean saveSuccess = false;
                    saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE + phoneSubId, 1);
                    Log.d("MobileNetworkSettings","ylf[onPreferenceChange]saveSuccess = " + saveSuccess+"phoneSubId:"+phoneSubId);
                }
                /*HQ_yulifeng for data roaming state movistar HQ01508448, 20151205,end*/
                /*HQ_yulifeng for data roaming state SW_GLOBAL_EUROPE_009  HQ01591289, 20151228,begin*/
                if(SystemProperties.get("ro.hq.roam.with.card.westeur").equals("1")){
                    boolean saveSuccess = false;
                    saveSuccess = Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING_STATE + phoneSubId, 1);
                    Log.d("MobileNetworkSettings","ylf[onPreferenceChange]saveSuccess = " + saveSuccess+"phoneSubId:"+phoneSubId);
                }
                /*HQ_yulifeng for data roaming state movistar HQ01508448, 20151205,end*/
            }
            return true;
        }
        //add by wanghui for al812 begin  20150921
        else if( preference == mButtonWlanDataSwitch){
                      String switch_mode="";
                      CharSequence[] entries=mButtonWlanDataSwitch.getEntries();
                      int index=mButtonWlanDataSwitch.findIndexOfValue((String)objValue);
                      String strlate =entries[index].toString();
                      String strPre = mContext.getResources().getString(R.string.pre_wlan_state);
                      mButtonWlanDataSwitch.setSummary(strPre+strlate);
                      Log.d("wanghui","index=" + index);
                      switch_mode = (String)objValue;
                      /*if(0 == index) 	{
                      switch_mode ="2";}else if (1 == index){
                      switch_mode ="3";}else if(2 == index){
                                switch_mode ="4";}*/
                      Settings.System.putString(mContext.getContentResolver(),"switch_mode_key",switch_mode);
                        Log.d("wanghui","index=" + index);	
                        Log.d("wanghui","switch_mode=" + switch_mode);
                        Log.d("wanghui","entries[index]=" + entries[index]);
                        return true;
              }
                //add by wanghui for al812 end
     

        /// Add for Plug-in @{
        mExt.onPreferenceChange(preference, objValue);
        /// @}
        /// M: no need updateBody here
        //updateBody();
        // always let the preference setting proceed.
        return true;
    }

    private void showConfirmDataDisableDlg() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.data_usage_disable_mobile);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setMobileDataEnabled(mPhone.getSubId(), false);
                // mButtonDataAlwaysOn.setEnabled(false);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mButtonCellularData.setChecked(true);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
                mButtonCellularData.setChecked(true);
			}
		});

        builder.create().show();
    }

//yanqing
    private void showConfirmDataAlwaysOn() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.network_always_on_warning);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // todo add real code
                setMobileDataAlwaysOn(mPhone.getSubId(), false);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mButtonDataAlwaysOn.setChecked(true);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mButtonDataAlwaysOn.setChecked(true);
            }
        });

        builder.create().show();
    }

    private boolean isChildModeOn() {
        String isOn = ChildMode.getString(getContentResolver(),
            ChildMode.CHILD_MODE_ON);
        if(isOn != null && "1".equals(isOn)){
            return true;
        }else {
            return false;
        }
    }

    private boolean isProhibitDataServices() {
        String isOn = ChildMode.getString(getContentResolver(),
            ChildMode.FORBID_DATA );
        if(isOn != null && "1".equals(isOn)){
            return true;
        }else {
            return false;
        }	 
    }

    /**
     * Customize strings which contains 'SIM', replace 'SIM' by
     * 'UIM/SIM','UIM','card' etc.
     * @param simString sim String
     * @param subId sub id
     * @return new String
     */
    public String customizeSimDisplayString(String simString, int subId) {
        if (simString == null) {
            return null;
        } else if (simString != null) {
            if (SubscriptionManager.INVALID_SUBSCRIPTION_ID == subId) {
                return replaceSimToSimUim(simString.toString());
            }
            if (PhoneConstants.SIM_ID_1 == SubscriptionManager.getSlotId(subId)) {
                return replaceSimBySlotInner(simString.toString());
            }
        }
        return simString;
    }

      /**
       * replace Sim String by SlotInner.
       * @param simString which will be replaced
       * @return new String
      */
      public static String replaceSimBySlotInner(String simString) {
          if (simString.contains("SIM")) {
            simString = simString.replaceAll("SIM", "UIM");
          }
          if (simString.contains("sim")) {
              simString = simString.replaceAll("sim", "uim");
          }
          return simString;
      }

    private String replaceSimToSimUim(String simString) {
        if (simString.contains("SIM")) {
            simString = simString.replaceAll("SIM", "UIM/SIM");
        }
        if (simString.contains("Sim")) {
            simString = simString.replaceAll("Sim", "Uim/Sim");
        }
        return simString;
    }

    /**
     * Replace sim with uim.
     *
     * @param context the current context.
     * @param builder the builder object that need to change.
     */
    private void changeDialogContent(
                    Context context,
                    AlertDialog.Builder builder,
                    String title,
                    String message) {
        String str = customizeSimDisplayString(title,
                                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        builder.setTitle(str);
        str = customizeSimDisplayString(message,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        builder.setMessage(str);
    }

    private void disableDataForOtherSubscriptions(SubscriptionInfo currentSir) {
        if (mActiveSubInfos != null) {
            for (SubscriptionInfo subInfo : mActiveSubInfos) {
                if (subInfo.getSubscriptionId() != currentSir.getSubscriptionId()) {
                    setMobileDataEnabled(subInfo.getSubscriptionId(), false);
                }
            }
        }
    }

    private void setMobileDataEnabled(int subId, boolean enabled) {
        final TelephonyManager tm =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.setDataEnabled(subId, enabled);
    }

    private void setMobileDataAlwaysOn(int subId, boolean enabled) {
        int value = enabled ? 0 : 1;
        log ("setMobileDataAlwaysOn: value = " + value);
        try {
            android.provider.Settings.System.putInt(mPhone.getContext().
                    getContentResolver(), POWER_SAVING_ON, value);
        } catch (Exception e) {
            if (DBG) Log.v(LOG_TAG, "setMobileDataAlwaysOn Exception!");
        }
    }

    /**
     * Return whether or not the user should have a SIM Cards option in Settings.
     * TODO: Change back to returning true if count is greater than one after testing.
     * TODO: See bug 16533525.
     */
    public boolean showSimCardTile(Context context) {
        final TelephonyManager tm =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

  	if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            return tm.getSimCount() > 1;
    	} else {
            return false;
    	}
    }

    private void handleMultiSimDataDialog() {
        final Context context = this;
        final SubscriptionInfo currentSir = mSubscriptionManager.getActiveSubscriptionInfo(
                mPhone.getSubId());

        //If sim has not loaded after toggling data switch, return.
        if (currentSir == null) {
            return;
        }

        final SubscriptionInfo nextSir = mSubscriptionManager.getActiveSubscriptionInfo(
                mSubscriptionManager.getDefaultDataSubId());

        // If the device is single SIM or is enabling data on the active data SIM then forgo
        // the pop-up.
        if (!showSimCardTile(context) ||
                (nextSir != null && currentSir != null &&
                currentSir.getSubscriptionId() == nextSir.getSubscriptionId())) {
            setMobileDataEnabled(currentSir.getSubscriptionId(), true);
            if (nextSir != null && currentSir != null &&
                currentSir.getSubscriptionId() == nextSir.getSubscriptionId()) {
                disableDataForOtherSubscriptions(currentSir);
            }
            return;
        }

        final String previousName = (nextSir == null)
            ? context.getResources().getString(R.string.sim_selection_required_pref)
            : nextSir.getDisplayName().toString();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.sim_change_data_title);
        builder.setMessage(getResources().getString(R.string.sim_change_data_message,
                    currentSir.getDisplayName(), previousName));

        /// M: replace sim with uim.
        changeDialogContent(context, builder,
            getResources().getString(R.string.sim_change_data_title),
            getResources().getString(R.string.sim_change_data_message,
                    currentSir.getDisplayName(), previousName));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                if (TelecomManager.from(context).isInCall()) {
                    Toast.makeText(context, R.string.default_data_switch_err_msg1,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                mSubscriptionManager.setDefaultDataSubId(currentSir.getSubscriptionId());
                setMobileDataEnabled(currentSir.getSubscriptionId(), true);
                disableDataForOtherSubscriptions(currentSir);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mButtonCellularData.setChecked(false);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
                mButtonCellularData.setChecked(false);
			}
		});

        builder.create().show();
    }

    private class MyHandler extends Handler {

        static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
        static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;

                case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
            }
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            final int phoneSubId = mPhone.getSubId();
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int modemNetworkMode = ((int[])ar.result)[0];

                if (DBG) {
                    log ("handleGetPreferredNetworkTypeResponse: modemNetworkMode = " +
                            modemNetworkMode);
                }

                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);

                if (DBG) {
                    log("handleGetPreferredNetworkTypeReponse: settingsNetworkMode = " +
                            settingsNetworkMode);
                }

                //check that modemNetworkMode is from an accepted value
                if (modemNetworkMode == Phone.NT_MODE_WCDMA_PREF ||
                        modemNetworkMode == Phone.NT_MODE_GSM_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_WCDMA_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_GSM_UMTS ||
                        modemNetworkMode == Phone.NT_MODE_CDMA ||
                        modemNetworkMode == Phone.NT_MODE_CDMA_NO_EVDO ||
                        modemNetworkMode == Phone.NT_MODE_EVDO_NO_CDMA ||
                        modemNetworkMode == Phone.NT_MODE_GLOBAL ||
                        modemNetworkMode == Phone.NT_MODE_LTE_CDMA_AND_EVDO ||
                        modemNetworkMode == Phone.NT_MODE_LTE_GSM_WCDMA ||
                        modemNetworkMode == Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA ||
                        modemNetworkMode == Phone.NT_MODE_LTE_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_LTE_WCDMA) {
                    if (DBG) {
                        log("handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = " +
                                modemNetworkMode);
                    }

                    // Framework's Phone.NT_MODE_GSM_UMTS is same as app's
                    // NT_MODE_WCDMA_PREF, this is related with feature option
                    // MTK_RAT_WCDMA_PREFERRED. In app side, we should change
                    // the setting system's value to NT_MODE_WCDMA_PREF, and keep
                    // sync with Modem's value.
                    if (modemNetworkMode == Phone.NT_MODE_GSM_UMTS
                            && TelephonyUtils.isWCDMAPreferredSupport()) {
                        modemNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                        if (settingsNetworkMode != Phone.NT_MODE_WCDMA_PREF) {
                            settingsNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                            android.provider.Settings.Global.putInt(
                                    mPhone.getContext().getContentResolver(),
                                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                                    settingsNetworkMode);
                            if (DBG) {
                                log("handleGetPreferredNetworkTypeResponse: settingNetworkMode");
                            }
                        }
                    } else {
                        //check changes in modemNetworkMode
                        if (modemNetworkMode != settingsNetworkMode) {
                            if (DBG) {
                                log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                        "modemNetworkMode != settingsNetworkMode");
                            }

                            settingsNetworkMode = modemNetworkMode;

                            if (DBG) { log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                    "settingsNetworkMode = " + settingsNetworkMode);
                            }

                        }
                    }

                    UpdatePreferredNetworkModeSummary(modemNetworkMode);
                    UpdateEnabledNetworksValueAndSummary(modemNetworkMode);
                    // changes the mButtonPreferredNetworkMode accordingly to modemNetworkMode
                    mButtonPreferredNetworkMode.setValue(Integer.toString(modemNetworkMode));
                } else {
                    if (DBG) {
                        log("handleGetPreferredNetworkTypeResponse: else: reset to default");
                    }
                    resetNetworkModeToDefault();
                }
            } else {
                log("handleGetPreferredNetworkTypeResponse: exception" + ar.exception);
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            final int phoneSubId = mPhone.getSubId();

            if (ar.exception != null) {
                log("handleSetPreferredNetworkTypeResponse: exception" + ar.exception);
                mPhone.getPreferredNetworkType(obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE));
            } else if (isCapabilityPhone(mPhone)) {
                // For ALPS01911001 Follow Google default, Put DB when FW return success.
                int networkMode;
                if (findPreference(BUTTON_PREFERED_NETWORK_MODE) != null) {
                    networkMode = Integer.valueOf(
                            mButtonPreferredNetworkMode.getValue()).intValue();
                } else {
                    networkMode = Integer.valueOf(
                            mButtonEnabledNetworks.getValue()).intValue();
                }
                log("handleSetPreferredNetworkTypeResponse: No exception;"
                        + "Set DB to : " + networkMode);
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        networkMode );
            }
		/*liruihong 20150923 add for HWEMUI system.apk HQ01393421 start*/
		Log.i("liruihong","MobileNetworkSettings:sentBroadCast");
		Intent intent = new Intent("com.android.huawei.PREFERRED_NETWORK_MODE_DATABASE_CHANGED");
		mPhone.getContext().sendBroadcast(intent);
		/*liruihong 20150923 add for HWEMUI system.apk HQ01393421 start*/	
        }

        private void resetNetworkModeToDefault() {
            final int phoneSubId = mPhone.getSubId();
            //set the mButtonPreferredNetworkMode
            mButtonPreferredNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            mButtonEnabledNetworks.setValue(Integer.toString(preferredNetworkMode));
            //set the Settings.System
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode );
            //Set the Modem
            mPhone.setPreferredNetworkType(preferredNetworkMode,
                    this.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
        }
    }

    private void UpdatePreferredNetworkModeSummary(int NetworkMode) {
        switch(NetworkMode) {
            case Phone.NT_MODE_WCDMA_PREF:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_wcdma_perf_summary);
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_gsm_only_summary);
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_wcdma_only_summary);
                break;
            case Phone.NT_MODE_GSM_UMTS:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_CDMA:
                switch (mPhone.getLteOnCdmaMode()) {
                    case PhoneConstants.LTE_ON_CDMA_TRUE:
                        mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_summary);
                    break;
                    case PhoneConstants.LTE_ON_CDMA_FALSE:
                    default:
                        mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_evdo_summary);
                        break;
                }
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_cdma_only_summary);
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_evdo_only_summary);
                break;
            case Phone.NT_MODE_LTE_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_summary);
                break;
            case Phone.NT_MODE_LTE_GSM_WCDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_cdma_evdo_summary);
                break;
            case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_global_summary);
                } else {
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_summary);
                }
                break;
            case Phone.NT_MODE_GLOBAL:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_LTE_WCDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_wcdma_summary);
                break;
            default:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_global_summary);
        }
        /// Add for Plug-in @{
        log("Enter plug-in update updateNetworkTypeSummary - Preferred.");
        mExt.updateNetworkTypeSummary(mButtonPreferredNetworkMode);
        mExt.customizePreferredNetworkMode(mButtonPreferredNetworkMode, mPhone.getSubId());
        /// @}
    }

    private void UpdateEnabledNetworksValueAndSummary(int NetworkMode) {

        Log.d(LOG_TAG, "NetworkMode: " + NetworkMode);

        if (SystemProperties.get("ro.hq.movistar.network.select").equals("1")) {//HQ_hushunli 2015-12-05 add for HQ01543639
            mButtonEnabledNetworks.setSummary(mButtonEnabledNetworks.getEntry());
            Log.d(LOG_TAG, "UpdateEnabledNetworksValueAndSummary, mButtonEnabledNetworks.getEntry is " + mButtonEnabledNetworks.getEntry());
            return;
        }
        switch (NetworkMode) {//{"11","9","3","2","1"}
            //HQ_hushunli 2015-09-10 modify for HQ01300994 begin
            case Phone.NT_MODE_WCDMA_ONLY://WCDMA only   3G  only
            	if(europemode){
            		mButtonEnabledNetworks.setSummary(R.string.network_3G_only);
            	}else {
            		mButtonEnabledNetworks.setSummary(R.string.network_wcdma_only);
				}
                break;
            case Phone.NT_MODE_GSM_UMTS://GSM/WCDMA   auto
                if (SystemProperties.get("ro.hq.network.mode.claro").equals("1")) {//add by caohaolin for HQ01455083
                    mButtonEnabledNetworks.setSummary(R.string.network_wcdma_gsm_claro);
                } else 	if(europemode){
            		mButtonEnabledNetworks.setSummary(R.string.network_3G);// 3G/2G auto
            	}else{
                    mButtonEnabledNetworks.setSummary(R.string.network_wcdma_gsm_auto);
                }
                break;
            case Phone.NT_MODE_WCDMA_PREF:
            if (!mIsGlobalCdma) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_WCDMA_PREF));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
			} else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case Phone.NT_MODE_GSM_ONLY: /* GSM only */
                /**if (!mIsGlobalCdma) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_GSM_ONLY));
                    mButtonEnabledNetworks.setSummary(R.string.network_2G);
                } else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }*/
            	if(europemode){
            		mButtonEnabledNetworks.setSummary(R.string.network_2G);
            	}else {
            		mButtonEnabledNetworks.setSummary(R.string.network_gsm_only);
				}
                break;
            case Phone.NT_MODE_LTE_GSM_WCDMA:/* LTE, GSM/WCDMA */
                //if (isWorldMode()) {
                if (SystemProperties.get("ro.hq.network.mode.claro").equals("1")) {//add by caohaolin for HQ01455083
                    mButtonEnabledNetworks.setSummary(
                            R.string.network_automatic_claro);
                } else if(europemode){
                    mButtonEnabledNetworks.setSummary(
                            R.string.network_4G);
                }else{
                    mButtonEnabledNetworks.setSummary(
                            R.string.network_lte_wcdma_gsm_auto);
                }
                    controlCdmaOptions(false);
                    controlGsmOptions(true);
                    break;
                //}
            //HQ_hushunli 2015-09-10 modify for HQ01300994 end
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
                if (!mIsGlobalCdma&&!europemode) {
                    if (SystemProperties.get("ro.hq.network.mode.claro").equals("1")) {//add by caohaolin for HQ01455083
                        mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_wcdma_4G_claro);
                    } else {
                        mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                            ? R.string.network_4G : R.string.network_lte);
                    }
                }else if (europemode) {
                          mButtonEnabledNetworks.setSummary(R.string.network_4G_only);
                          mButtonEnabledNetworks.setValue(
                                  Integer.toString(Phone.NT_MODE_LTE_ONLY));
				} else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                if (isWorldMode()) {
                    mButtonEnabledNetworks.setSummary(
                            R.string.preferred_network_mode_lte_cdma_summary);
                    controlCdmaOptions(true);
                    controlGsmOptions(false);
                } else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CDMA_AND_EVDO));
                    mButtonEnabledNetworks.setSummary(R.string.network_lte);
                }
                break;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_CDMA));
                mButtonEnabledNetworks.setSummary(R.string.network_3G);
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_CDMA_NO_EVDO));
                mButtonEnabledNetworks.setSummary(R.string.network_1x);
                break;
            case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                if (isWorldMode()) {
                    controlCdmaOptions(true);
                    controlGsmOptions(false);
                }
                if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                } else {
                    /// M: ALPS02109662, Make sure the right value of the Network Mode @{
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_GSM_WCDMA));
                    /// @}
                    mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                            ? R.string.network_4G : R.string.network_lte);
                }
                break;
            default:
                String errMsg = "Invalid Network Mode (" + NetworkMode + "). Ignore.";
                loge(errMsg);
                mButtonEnabledNetworks.setSummary(errMsg);
        }

        /// M: ALPS02200643, for c2k 3m, any way make sure right summary of network type @{
        handleC2k3MCapalibity();
        /// @}

        /// M: ALPS02217238, for c2k 5m, any way make sure right summary of network type @{
        handleC2k5MCapalibity();
        /// @}

        /// Add for Plug-in @{
        if (mButtonEnabledNetworks != null) {
            log("Enter plug-in update updateNetworkTypeSummary - Enabled.");
            mExt.updateNetworkTypeSummary(mButtonEnabledNetworks);
            log("customizePreferredNetworkMode mButtonEnabledNetworks.");
            mExt.customizePreferredNetworkMode(mButtonEnabledNetworks, mPhone.getSubId());
        }
        /// @}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_CODE_EXIT_ECM:
            Boolean isChoiceYes =
                data.getBooleanExtra(EmergencyCallbackModeExitDialog.EXTRA_EXIT_ECM_RESULT, false);
            if (isChoiceYes) {
                // If the phone exits from ECM mode, show the CDMA Options
                mCdmaOptions.showDialog(mClickedPreference);
            } else {
                // do nothing
            }
            break;

        default:
            break;
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            // Commenting out "logical up" capability. This is a workaround for issue 5278083.
            //
            // Settings app may not launch this activity via UP_ACTIVITY_CLASS but the other
            // Activity that looks exactly same as UP_ACTIVITY_CLASS ("SubSettings" Activity).
            // At that moment, this Activity launches UP_ACTIVITY_CLASS on top of the Activity.
            // which confuses users.
            // TODO: introduce better mechanism for "up" capability here.
            /*Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(UP_ACTIVITY_PACKAGE, UP_ACTIVITY_CLASS);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);*/
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isWorldMode() {
        boolean worldModeOn = false;
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        final String configString = getResources().getString(R.string.config_world_mode);

        if (!TextUtils.isEmpty(configString)) {
            String[] configArray = configString.split(";");
            // Check if we have World mode configuration set to True only or config is set to True
            // and SIM GID value is also set and matches to the current SIM GID.
            if (configArray != null &&
                   ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true")) ||
                       (configArray.length == 2 && !TextUtils.isEmpty(configArray[1]) &&
                           tm != null && configArray[1].equalsIgnoreCase(tm.getGroupIdLevel1())))) {
                               worldModeOn = true;
            }
        }

        if (DBG) {
            log("isWorldMode=" + worldModeOn);
        }

        return worldModeOn;
    }

    private void controlGsmOptions(boolean enable) {
        PreferenceScreen prefSet = getPreferenceScreen();
        if (prefSet == null) {
            return;
        }

        if (mGsmUmtsOptions == null) {
            mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, mPhone.getSubId());
        }
        PreferenceScreen apnExpand =
                (PreferenceScreen) prefSet.findPreference(BUTTON_APN_EXPAND_KEY);
        PreferenceScreen operatorSelectionExpand =
                (PreferenceScreen) prefSet.findPreference(BUTTON_OPERATOR_SELECTION_EXPAND_KEY);
        PreferenceScreen carrierSettings =
                (PreferenceScreen) prefSet.findPreference(BUTTON_CARRIER_SETTINGS_KEY);
        if (apnExpand != null) {
            apnExpand.setEnabled(isWorldMode() || enable);
        }
        if (operatorSelectionExpand != null) {
            operatorSelectionExpand.setEnabled(enable);
        }
        if (carrierSettings != null) {
            prefSet.removePreference(carrierSettings);
        }
    }

    private void controlCdmaOptions(boolean enable) {
        PreferenceScreen prefSet = getPreferenceScreen();
        if (prefSet == null) {
            return;
        }
        if (enable && mCdmaOptions == null) {
            mCdmaOptions = new CdmaOptions(this, prefSet, mPhone);
        }
        CdmaSystemSelectListPreference systemSelect =
                (CdmaSystemSelectListPreference)prefSet.findPreference
                        (BUTTON_CDMA_SYSTEM_SELECT_KEY);
        if (systemSelect != null) {
            systemSelect.setEnabled(enable);
        }
    }

    /**
     * finds a record with slotId.
     * Since the number of SIMs are few, an array is fine.
     */
    public SubscriptionInfo findRecordBySlotId(final int slotId) {
        if (mActiveSubInfos != null) {
            final int subInfoLength = mActiveSubInfos.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = mActiveSubInfos.get(i);
                if (sir.getSimSlotIndex() == slotId) {
                    //Right now we take the first subscription on a SIM.
                    return sir;
                }
            }
        }

        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mCdmaNetworkSettings != null) {
            mCdmaNetworkSettings.resetState();
            mCdmaNetworkSettings.onDestroy();
            mCdmaNetworkSettings = null;
        }
        log("onDestroy " + this);
        unregisterReceiver(mReceiver);
        mSubscriptionManager
        .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        TelephonyManager.getDefault().listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        /// M: For plugin to unregister listener
        mExt.unRegister();
    }

    private void dissmissDialog(ListPreference preference) {
        Dialog dialog = null;
        if (preference != null) {
            dialog = preference.getDialog();
            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }

    // -------------------- Mediatek ---------------------
    // M: Add for plug-in
    private IMobileNetworkSettingsExt mExt;
    /// M: add for plmn list
    public static final String BUTTON_PLMN_LIST = "button_plmn_key";
    private static final String BUTTON_CDMA_ACTIVATE_DEVICE_KEY = "cdma_activate_device_key";
    /// M: c2k 4g data only
    private static final String SINGLE_LTE_DATA = "single_lte_data";
    /// M: for screen rotate @{
    private static final String CURRENT_TAB = "current_tab";
    private int mCurrentTab = 0;
    /// @}
    private Preference mPLMNPreference;
    private IntentFilter mIntentFilter;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("action: " + action);
            /// When receive aiplane mode, we would like to finish the activity, for
            //  we can't get the modem capability, and will show the user selected network
            //  mode as summary, this will make user misunderstand.(ALPS01971666)
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                finish();
            } else if (action.equals(Intent.ACTION_MSIM_MODE_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_MD_TYPE_CHANGE)
                    || action.equals(TelephonyIntents.ACTION_LOCATED_PLMN_CHANGED)) {
                updateScreenStatus();
            }
            /// Add for Sim Switch @{
            else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE)) {
                log("Siwthc done Action ACTION_SET_PHONE_RAT_FAMILY_DONE received ");
                mPhone = PhoneUtils.getPhoneUsingSubId(mPhone.getSubId());
                updateScreenStatus();
                /// M: For operator requirement {
                checkForVolteSettings(context);
                /// @}
            } else if (action.equals(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED)) {
                // When the radio changes (ex: CDMA->GSM), refresh all options.
                mGsmUmtsOptions = null;
                mCdmaOptions = null;
                updateBody();
            } else if (action.equals(TelephonyIntents.ACTION_RAT_CHANGED)) {
                handleRatChanged(intent);
            /// M: for 5M, add for sim loaded @{
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                if (FeatureOption.isMtkC2k5MSupport() || FeatureOption.isMtkC2k3MSupport()) {
                    String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    if (simStatus.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                        log("--- sim card loaded ---");

                        if (!mButtonEnabledNetworks.isEnabled()) {
                            if (FeatureOption.isMtkC2k5MSupport()) {
                                mButtonEnabledNetworks.setEnabled(true);
                            } else if (FeatureOption.isMtkC2k3MSupport()) {
                                if (!TelephonyUtils.isCmccCard(mPhone.getSubId())) {
                                    mButtonEnabledNetworks.setEnabled(true);
                                }
                            }
                        }
                        updateBody();
                    }
                }
                // / @}
                // / @}

            }else if(action.equals(myAction)){
                      if (getPreferenceScreen().findPreference(BUTTON_WLAN_DATA_SWITCH_KEY) != null)  {
                          String keyValue  = "3";
                          Log.d("LOG_TAG","keyValue"+keyValue);
                          mButtonWlanDataSwitch.setValue(keyValue);
                          String strAuto = mContext.getResources().getString(R.string.auto_wlan_state);
                          mButtonWlanDataSwitch.setSummary(strAuto);
                      }
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                mButtonCellularData.setChecked(mPhone.getDataEnabled());

                /// M: For operator requirement {
                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))) {
                    checkForVolteSettings(context);
                }
                /// @}
            }
        }
    };

    private void onCreateMTK(PreferenceScreen prefSet) {

        mExt = ExtensionManager.getMobileNetworkSettingsExt();
        /// M: Add For [MTK_Enhanced4GLTE] @{
        addEnhanced4GLteSwitchPreference(prefSet);
        /// @}

        /// M: For 2G only project remove select network mode item @{
        if (TelephonyUtils.is2GOnlyProject()) {
            log("[initPreferenceForMobileNetwork]only 2G");
            if (findPreference(BUTTON_PREFERED_NETWORK_MODE) != null) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
            }
            if (findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null) {
                prefSet.removePreference(mButtonEnabledNetworks);
            }
        }
        /// @}

        /// M: Add for plmn list @{
        if (!FeatureOption.isMtk3gDongleSupport()
                && !TelephonyUtilsEx.isCDMAPhone(mPhone)) {
            log("---addPLMNList---");
	     /*SW_GLOBAL_COMMON_045 ; yulifeng modify;remove menu Preferences;b*/
	     if(!(SystemProperties.get("ro.hq.network.remove.perf").equals("1"))){
			 /*HQ_xupeixin at 20151020 modified about remove the PLMN preference begin*/
			 //addPLMNList(prefSet);
			 /*HQ_xupeixin at 20151020 modified end*/
		 }
            /*SW_GLOBAL_COMMON_045 ; yulifeng modify;remove menu Preferences;e*/
        }
        /// @}

        /// M: Add For C2K OM, OP09 will implement its own cdma network setting @{
        if (FeatureOption.isMtkLteSupport() 
                && FeatureOption.isMtkSvlteSupport()
                && (TelephonyUtilsEx.isCDMAPhone(mPhone) ||
                        TelephonyUtilsEx.isCTRoaming(mPhone) ||
                        FeatureOption.isMtkCtTestCardSupport())
                && !mExt.isCtPlugin()) {
            if ((FeatureOption.isMtkC2k5MSupport() && FeatureOption.isLoadForHome())
                        || !FeatureOption.isMtkC2k5MSupport()) {
                if (mCdmaNetworkSettings != null) {
                    log("CdmaNetworkSettings destroy " + this);

                    mCdmaNetworkSettings.onDestroy();
                    mCdmaNetworkSettings = null;
                }
                mCdmaNetworkSettings = new CdmaNetworkSettings(this, prefSet, mPhone);
            }
        }
        /// @}

        /// Add for plug-in @{
        if (mPhone != null) {
			/*add by wanghui for al812_cl add feature begin */
		  PreferenceScreen prefSetTemp = getPreferenceScreen();
			if( SystemProperties.get("ro.operator.optr").equals("OP09")){
				Log.d("wlan_zhangjing","not op09");
				prefSetTemp.removeAll();
				prefSetTemp.addPreference(mButtonWlanDataSwitch);
			}			
			/*add by wanghui for al812_cl add feature end */
            mExt.initOtherMobileNetworkSettings(this, mPhone.getSubId());
        }
        mExt.initMobileNetworkSettings(this, convertTabToSlot(mCurrentTab));
        /// @}

        updateScreenStatus();

        /// M: for mtk 3m @{
        handleC2k3MScreen(prefSet);
        /// @}

        /// M: for mtk 5m @{
        handleC2k5MScreen(prefSet);
        /// @}
    }

    private void initIntentFilter() {
        /// M: for receivers sim lock gemini phone @{
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY);
        mIntentFilter.addAction(Intent.ACTION_MSIM_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_MD_TYPE_CHANGE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_LOCATED_PLMN_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);	        
	mIntentFilter.addAction(myAction);	
        ///@}
        /// M: Add for Sim Switch @{
        mIntentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_RAT_CHANGED);
        /// @}

        /// M: add for sim card loaded @{
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        /// @}
        mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    }

    private void addPLMNList(PreferenceScreen prefSet) {
        // add PLMNList, if c2k project the order should under the 4g data only
        int order = prefSet.findPreference(SINGLE_LTE_DATA) != null ?
                prefSet.findPreference(SINGLE_LTE_DATA).getOrder() : mButtonDataRoam.getOrder();
        mPLMNPreference = new Preference(this);
        mPLMNPreference.setKey(BUTTON_PLMN_LIST);
        mPLMNPreference.setTitle(R.string.plmn_list_setting_title);
        Intent intentPlmn = new Intent();
        intentPlmn.setClassName("com.android.phone", "com.mediatek.settings.PLMNListPreference");
        intentPlmn.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA, mPhone.getSubId());
        mPLMNPreference.setIntent(intentPlmn);
        mPLMNPreference.setOrder(order + 1);
        prefSet.addPreference(mPLMNPreference);
    }

    private void updateScreenStatus() {
        boolean isIdle = (TelephonyManager.getDefault().getCallState()
                == TelephonyManager.CALL_STATE_IDLE);
        boolean isShouldEnabled = isIdle && TelephonyUtils.isRadioOn(mPhone.getSubId());
        log("updateNetworkModePreference:isShouldEnabled = "
                + isShouldEnabled + ", isIdle = " + isIdle);
        getPreferenceScreen().setEnabled(isShouldEnabled || mExt.useCTTestcard()
                || FeatureOption.isMtkCtTestCardSupport());
        updateCapabilityRelatedPreference(isShouldEnabled);
    }

    /**
     * Add for update the display of network mode preference.
     * @param enable is the preference or not
     */
    private void updateCapabilityRelatedPreference(boolean enable) {
        // if airplane mode is on or all SIMs closed, should also dismiss dialog
        boolean isNWModeEnabled = enable && isCapabilityPhone(mPhone);
        log("updateNetworkModePreference:isNWModeEnabled = " + isNWModeEnabled);

        updateNetworkModePreference(mButtonPreferredNetworkMode, isNWModeEnabled);
        updateNetworkModePreference(mButtonEnabledNetworks, isNWModeEnabled);
        /// Add for [MTK_Enhanced4GLTE]
        updateEnhanced4GLteSwitchPreference();

        /// M: for mtk c2k 3m @{
        handleC2k3MCapalibity();
        /// @}

        /// M: for mtk c2k 5m @{
        handleC2k5MCapalibity();
        /// @}
    }

    /**
     * Add for update the display of network mode preference.
     * @param enable is the preference or not
     */
    private void updateNetworkModePreference(ListPreference preference, boolean enable) {
        // if airplane mode is on or all SIMs closed, should also dismiss dialog
        if (preference != null) {
            preference.setEnabled(enable);
            if (!enable) {
                dissmissDialog(preference);
            }
            if (getPreferenceScreen().findPreference(preference.getKey()) != null) {
                mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                        MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
            }
            /// Add for Plug-in @{
            mExt.customizePreferredNetworkMode(preference, mPhone.getSubId());
            log("Enter plug-in update updateLTEModeStatus.");
            mExt.updateLTEModeStatus(preference);
        }
    }

    /**
     * handle network mode change result by framework world phone sim switch logical.
     * @param intent which contains the info of network mode
     */
    private void handleRatChanged(Intent intent) {
        int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
        int modemMode = intent.getIntExtra(TelephonyIntents.EXTRA_RAT, -1);
        log("handleRatChanged phoneId: " + phoneId + " modemMode: " + modemMode);
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (modemMode != -1 && isCapabilityPhone(phone)) {
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE +
                    phone.getSubId(),
                    modemMode);
        }
        if (phoneId == mPhone.getPhoneId() && isCapabilityPhone(phone)) {
            log("handleRatChanged: updateBody");
            updateBody();
        }
    }

    /**
     * Is the phone has 3/4G capability or not.
     * @return true if phone has 3/4G capability
     */
    private boolean isCapabilityPhone(Phone phone) {
        boolean result = phone != null ? ((phone.getRadioAccessFamily()
                & (RadioAccessFamily.RAF_UMTS | RadioAccessFamily.RAF_LTE)) > 0) : false;
        log("isCapabilityPhone: " + result);
        return result;
    }

    // M: Add for [MTK_Enhanced4GLTE] @{
    // Use our own button instand of Google default one mButton4glte
    private Enhanced4GLteSwitchPreference mEnhancedButton4glte;

    /**
     * Add our switchPreference & Remove google default one.
     * @param preferenceScreen
     */
    private void addEnhanced4GLteSwitchPreference(PreferenceScreen preferenceScreen) {
        log("[addEnhanced4GLteSwitchPreference] ImsEnabled :"
                + ImsManager.isVolteEnabledByPlatform(this));
        if(mButton4glte != null) {
            log("[addEnhanced4GLteSwitchPreference] Remove mButton4glte!");
            preferenceScreen.removePreference(mButton4glte);
        }
        if(ImsManager.isVolteEnabledByPlatform(this) && ImsManager.isImsSupportByCarrier(this)
                && mExt.volteEnabledForOperator()) {
            int order = mButtonEnabledNetworks.getOrder() + 1;
            mEnhancedButton4glte = new Enhanced4GLteSwitchPreference(this, mPhone.getSubId());
            /// Still use Google's key, title, and summary.
            mEnhancedButton4glte.setKey(BUTTON_4G_LTE_KEY);
            if (ImsManager.isWfcEnabledByPlatform(this)) {
                mEnhancedButton4glte.setTitle(R.string.wfc_volte_switch_title);
            } else {
                mEnhancedButton4glte.setTitle(R.string.enhanced_4g_lte_mode_title);
            }
            mEnhancedButton4glte.setSummary(R.string.enhanced_4g_lte_mode_summary);
            mEnhancedButton4glte.setOnPreferenceChangeListener(this);
            mEnhancedButton4glte.setOrder(order);
            //preferenceScreen.addPreference(mEnhancedButton4glte);
        }
    }

    /**
     * Update the subId in mEnhancedButton4glte.
     */
    private void updateEnhanced4GLteSwitchPreference() {
        if (mEnhancedButton4glte != null) {
            if (isCapabilityPhone(mPhone) && findPreference(BUTTON_4G_LTE_KEY) == null) {
                getPreferenceScreen().addPreference(mEnhancedButton4glte);
            } else if (!isCapabilityPhone(mPhone) && findPreference(BUTTON_4G_LTE_KEY) != null) {
                getPreferenceScreen().removePreference(mEnhancedButton4glte);
            }
            if (findPreference(BUTTON_4G_LTE_KEY) != null) {
                log("[updateEnhanced4GLteSwitchPreference] SubId = " + mPhone.getSubId());
                mEnhancedButton4glte.setSubId(mPhone.getSubId());
                int isChecked = Settings.Global.getInt(getContentResolver(),
                        Settings.Global.ENHANCED_4G_MODE_ENABLED, 0);
                mEnhancedButton4glte.setChecked(isChecked == 1);
            }
        }
    }

    /**
     * For [MTK_Enhanced4GLTE]
     * We add our own SwitchPreference, and its own onPreferenceChange call backs.
     * @param preference
     * @param objValue
     * @return
     */
    private boolean onPreferenceChangeMTK(Preference preference, Object objValue) {
        log("[onPreferenceChangeMTK] preference = " + preference.getTitle());
        if (mEnhancedButton4glte == preference) {
            log("[onPreferenceChangeMTK] IsChecked = " + mEnhancedButton4glte.isChecked());
            Enhanced4GLteSwitchPreference ltePref = (Enhanced4GLteSwitchPreference)preference;
            ltePref.setChecked(!ltePref.isChecked());
            ImsManager.setEnhanced4gLteModeSetting(this, ltePref.isChecked());
            return true;
        }
        return false;
    }
    /// @}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_TAB, mCurrentTab);
    }

    /**
     * For [MTK_3SIM].
     * Convert Tab id to Slot id.
     * @param currentTab tab id
     * @return slotId
     */
    private int convertTabToSlot(int currentTab) {
        int slotId = mActiveSubInfos.size() > currentTab ?
                mActiveSubInfos.get(currentTab).getSimSlotIndex() : 0;
        if (DBG) {
            log("convertTabToSlot: info size=" + mActiveSubInfos.size() +
                    " currentTab=" + currentTab + " slotId=" + slotId);
        }
        return slotId;
    }

    /**
     * For C2k 5M
     */
    private void handleC2k5MCapalibity() {
        if (FeatureOption.isMtkLteSupport() && FeatureOption.isMtkC2k5MSupport()) {
            if (TelephonyUtils.isInvalidSimCard(mPhone.getSubId())
                    && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                log("--- go to C2k 5M capability ---");

                mButtonEnabledNetworks.setEnabled(false);
            }
        }
    }

    /**
     * For C2k 5M
     * Under 5M(CLLWG),CMCC card in home network is no need show 3G item.
     * @param prefSet
     */
    private void handleC2k5MScreen(PreferenceScreen prefSet) {
        if (FeatureOption.isMtkLteSupport() && FeatureOption.isMtkC2k5MSupport()) {

            handleC2kCommonScreen(prefSet);
            log("--- go to c2k 5M ---");

            if (!TelephonyUtilsEx.isCDMAPhone(mPhone)) {
                mButtonEnabledNetworks.setEntries(R.array.enabled_networks_4g_choices);
                mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_values);
                if (TelephonyUtils.isCmccCard(mPhone.getSubId())
                        && !mPhone.getServiceState().getRoaming()) {
                    mButtonEnabledNetworks.setEntries(R.array.enabled_networks_except_td_cdma_3g_choices);
                    mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_except_td_cdma_3g_values);
                }
            }
        }
    }

    /**
     * For C2k 3M
     * @param preset
     */
    private void handleC2k3MScreen(PreferenceScreen prefSet) {
        if (!FeatureOption.isMtkLteSupport() && FeatureOption.isMtkC2k3MSupport()) {
            log( "--- go to C2k 3M ---");

            handleC2kCommonScreen(prefSet);

            if (!TelephonyUtilsEx.isCDMAPhone(mPhone)) {
                mButtonEnabledNetworks.setEntries(R.array.enabled_networks_except_lte_choices);
                mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_except_lte_values);
            }
        }
    }

    /**
     * For C2k 3M
     */
    private void handleC2k3MCapalibity() {
        if (!FeatureOption.isMtkLteSupport() && FeatureOption.isMtkC2k3MSupport()) {
            if (TelephonyUtils.isCmccCard(mPhone.getSubId())
                    && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM
                    && !mPhone.getServiceState().getRoaming()
                    || TelephonyUtils.isInvalidSimCard(mPhone.getSubId())) {
                log("--- go to C2k 3M capability ---");

                mButtonEnabledNetworks.setSummary(R.string.network_2G);
                mButtonEnabledNetworks.setEnabled(false);
            }
        }
    }

    /**
     * For C2k Common screen, (3M, 5M)
     * @param preset
     */
    private void handleC2kCommonScreen(PreferenceScreen prefSet) {
        log("--- go to C2k Common (3M, 5M) screen ---");

        if (prefSet.findPreference(BUTTON_PLMN_LIST) != null) {
            prefSet.removePreference(prefSet.findPreference(BUTTON_PLMN_LIST));
        }
        if (prefSet.findPreference(BUTTON_4G_LTE_KEY) != null) {
            prefSet.removePreference(prefSet.findPreference(BUTTON_4G_LTE_KEY));
        }
        if (prefSet.findPreference(BUTTON_PREFERED_NETWORK_MODE) != null) {
            prefSet.removePreference(prefSet.findPreference(BUTTON_PREFERED_NETWORK_MODE));
        }
        if (TelephonyUtilsEx.isCDMAPhone(mPhone)) {
            if (prefSet.findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null) {
                prefSet.removePreference(prefSet.findPreference(BUTTON_ENABLED_NETWORKS_KEY));
            }
        }
    }

    /**
     * For operator requirement: Show Volte Settings only if RJIL present as master SIM
     * @param context
     */
    private void checkForVolteSettings(Context context) {
        if (!ImsManager.isImsSupportByCarrier(context)) {
            log("sim changed or Master sim changed, inappropriate, remove volte");
            if (mEnhancedButton4glte != null) {
                getPreferenceScreen().removePreference(mEnhancedButton4glte);
                log("mEnhancedButton4glte after removing:"+mEnhancedButton4glte);
                mEnhancedButton4glte = null;
            } else if (mButton4glte != null) {
                getPreferenceScreen().removePreference(mButton4glte);
                mButton4glte = null;
            }
        }
    }
}
