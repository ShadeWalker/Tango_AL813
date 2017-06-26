package com.android.phone.settings;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
//add by zhangjinqiang for HQ01332140
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
//end
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.android.services.telephony.sip.SipAccountRegistry;
import com.android.services.telephony.sip.SipSharedPreferences;
import com.android.services.telephony.sip.SipUtil;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.ICallFeaturesSettingExt;
import com.mediatek.settings.CallFeaturesSettingExt;
import com.mediatek.settings.TelephonyUtils;
import com.mediatek.settings.cdma.TelephonyUtilsEx;

import java.util.List;

// / Added by guofeiyao for HQ01307563
import android.preference.SwitchPreference;
import android.provider.Settings;

import com.android.phone.common.widget.ArrowPreference;
// / End

public class PhoneAccountSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                Preference.OnPreferenceClickListener,
                AccountSelectionPreference.AccountSelectionListener {

    private static final String ACCOUNTS_LIST_CATEGORY_KEY =
            "phone_accounts_accounts_list_category_key";

    private static final String DEFAULT_OUTGOING_ACCOUNT_KEY = "default_outgoing_account";

    private static final String CONFIGURE_CALL_ASSISTANT_PREF_KEY =
            "wifi_calling_configure_call_assistant_preference";
    private static final String CALL_ASSISTANT_CATEGORY_PREF_KEY =
            "phone_accounts_call_assistant_settings_category_key";
    private static final String SELECT_CALL_ASSISTANT_PREF_KEY =
            "wifi_calling_call_assistant_preference";

    private static final String SIP_SETTINGS_CATEGORY_PREF_KEY =
            "phone_accounts_sip_settings_category_key";
    private static final String USE_SIP_PREF_KEY = "use_sip_calling_options_key";
    private static final String SIP_RECEIVE_CALLS_PREF_KEY = "sip_receive_calls_key";

    private String LOG_TAG = PhoneAccountSettingsFragment.class.getSimpleName();

    private TelecomManager mTelecomManager;
    private SubscriptionManager mSubscriptionManager;

    private PreferenceCategory mAccountList;

    private AccountSelectionPreference mDefaultOutgoingAccount;
    private AccountSelectionPreference mSelectCallAssistant;
    private Preference mConfigureCallAssistant;
    private Preference sdn;//added by zhaizhanfeng for SDN at151103
    private ListPreference mUseSipCalling;
    private CheckBoxPreference mSipReceiveCallsPreference;
    private SipSharedPreferences mSipSharedPreferences;

	// /added by guofeiyao for HQ01307563
	private PreferenceCategory callSettings;
	//private SharedPreference mSp;
    // /end
    //add by zhangjiqiang for HQ01332140
    private PreferenceScreen smartIdentifyNumber;
    //end

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mTelecomManager = TelecomManager.from(getActivity());
        mSubscriptionManager = SubscriptionManager.from(getActivity());
        ///M: Add MTK feature
        onCreateMTK();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
        }

        addPreferencesFromResource(R.xml.phone_account_settings);

        // / Added by guofeiyao
		callSettings = (PreferenceCategory)findPreference("hw_call_settings_key");
		// / End

        TelephonyManager telephonyManager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        mAccountList = (PreferenceCategory) getPreferenceScreen().findPreference(
                ACCOUNTS_LIST_CATEGORY_KEY);
        TtyModeListPreference ttyModeListPreference =
                (TtyModeListPreference) getPreferenceScreen().findPreference(
                        getResources().getString(R.string.tty_mode_key));
        if (telephonyManager.isMultiSimEnabled()) {
            initAccountList();
            Log.d(LOG_TAG, "onResume isTtySupported: " + mTelecomManager.isTtySupported());

            /// guoxiaolong for apr @{
            if (mTelecomManager.isTtySupported()) {
            	if(null != ttyModeListPreference) {
            		ttyModeListPreference.init();
            	}


            } else {
                if(null != ttyModeListPreference) {
					// / Modified by guofeiyao
                	//getPreferenceScreen().removePreference(ttyModeListPreference);
                	callSettings.removePreference(ttyModeListPreference);
                	// / End
                }
            }
        } else {
            getPreferenceScreen().removePreference(mAccountList);
            if(null != ttyModeListPreference) {
				// / Modified by guofeiyao
            	//getPreferenceScreen().removePreference(ttyModeListPreference);
            	callSettings.removePreference(ttyModeListPreference);
				// / End
            }
        }
        /// @}

        mDefaultOutgoingAccount = (AccountSelectionPreference)
                getPreferenceScreen().findPreference(DEFAULT_OUTGOING_ACCOUNT_KEY);
        if (mTelecomManager.hasMultipleCallCapableAccounts()) {
            mDefaultOutgoingAccount.setListener(this);
            updateDefaultOutgoingAccountsModel();
        } else {
            // / Modified by guofeiyao
            //getPreferenceScreen().removePreference(mDefaultOutgoingAccount);
            callSettings.removePreference(mDefaultOutgoingAccount);
			// / End
        }

        List<PhoneAccountHandle> simCallManagers = mTelecomManager.getSimCallManagers();
        PreferenceCategory callAssistantCategory = (PreferenceCategory)
                getPreferenceScreen().findPreference(CALL_ASSISTANT_CATEGORY_PREF_KEY);
        if (simCallManagers.isEmpty()) {
            getPreferenceScreen().removePreference(callAssistantCategory);
        } else {
            // Display a list of call assistants. Choosing an item from the list enables the
            // corresponding call assistant.
            mSelectCallAssistant = (AccountSelectionPreference)
                    getPreferenceScreen().findPreference(SELECT_CALL_ASSISTANT_PREF_KEY);
            mSelectCallAssistant.setListener(this);
            mSelectCallAssistant.setDialogTitle(
                    R.string.wifi_calling_select_call_assistant_summary);
            updateCallAssistantModel();

            mConfigureCallAssistant =
                    getPreferenceScreen().findPreference(CONFIGURE_CALL_ASSISTANT_PREF_KEY);
            mConfigureCallAssistant.setOnPreferenceClickListener(this);
            updateConfigureCallAssistant();
        }

        if (SipUtil.isVoipSupported(getActivity())) {
            mSipSharedPreferences = new SipSharedPreferences(getActivity());

            mUseSipCalling = (ListPreference)
                    getPreferenceScreen().findPreference(USE_SIP_PREF_KEY);
            mUseSipCalling.setEntries(!SipManager.isSipWifiOnly(getActivity())
                    ? R.array.sip_call_options_wifi_only_entries
                    : R.array.sip_call_options_entries);
            mUseSipCalling.setOnPreferenceChangeListener(this);

            int optionsValueIndex =
                    mUseSipCalling.findIndexOfValue(mSipSharedPreferences.getSipCallOption());
            if (optionsValueIndex == -1) {
                // If the option is invalid (eg. deprecated value), default to SIP_ADDRESS_ONLY.
                mSipSharedPreferences.setSipCallOption(
                        getResources().getString(R.string.sip_address_only));
                optionsValueIndex =
                        mUseSipCalling.findIndexOfValue(mSipSharedPreferences.getSipCallOption());
            }
            mUseSipCalling.setValueIndex(optionsValueIndex);
            mUseSipCalling.setSummary(mUseSipCalling.getEntry());

            mSipReceiveCallsPreference = (CheckBoxPreference)
                    getPreferenceScreen().findPreference(SIP_RECEIVE_CALLS_PREF_KEY);
            mSipReceiveCallsPreference.setEnabled(SipUtil.isPhoneIdle(getActivity()));
            mSipReceiveCallsPreference.setChecked(
                    mSipSharedPreferences.isReceivingCallsEnabled());
            mSipReceiveCallsPreference.setOnPreferenceChangeListener(this);
        } else {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(SIP_SETTINGS_CATEGORY_PREF_KEY));
        }

		// Added by guofeiyao for HQ01237592
		PreferenceCategory internetDial = (PreferenceCategory)getPreferenceScreen().findPreference("phone_accounts_sip_settings_category_key");
		if ( null != internetDial){
    		getPreferenceScreen().removePreference(internetDial);
		}
        if ( null != callSettings) {
		     callSettings.removePreference(mDefaultOutgoingAccount);
        }
		
        Preference anc = findPreference("button_anc_key");
		if ( null != anc) {
             callSettings.removePreference(anc);
		}
		// End
		/* HQ_fengsimin 2016-2-25 modified for HQ01767680 */
Preference magi_conference = findPreference("button_magi_conference_key");
callSettings.removePreference(magi_conference);
        ///M: Init MTK features
        onResumeMTK();

		//added by zhaizhanfeng for SDN at151103 start
		sdn = findPreference("button_sdn_key");
		if(!SystemProperties.get("ro.hq.call.setting.double.sdn").equals("1")){//modify by wangmingyue for SDN HQ01617063
           callSettings.removePreference(sdn);
        	}
        sdn.setOnPreferenceClickListener(this);
		//added by zhaizhanfeng for SDN at151103 end

    }

    /**
     * Handles changes to the preferences.
     *
     * @param pref The preference changed.
     * @param objValue The changed value.
     * @return True if the preference change has been handled, and false otherwise.
     */
    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        /// M: Add MTK features @{
        if (mCallFeaturesSettingExt != null
                && mCallFeaturesSettingExt.onPreferenceChange(pref)) {
            return true;
        } else
        /// @}
            if (pref == mUseSipCalling) {
            String option = objValue.toString();
            mSipSharedPreferences.setSipCallOption(option);
            mUseSipCalling.setValueIndex(mUseSipCalling.findIndexOfValue(option));
            mUseSipCalling.setSummary(mUseSipCalling.getEntry());
            return true;
        } else if (pref == mSipReceiveCallsPreference) {
            final boolean isEnabled = !mSipReceiveCallsPreference.isChecked();
            new Thread(new Runnable() {
                public void run() {
                    handleSipReceiveCallsOption(isEnabled);
                }
            }).start();
            return true;
        }
		
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mConfigureCallAssistant) {
            Intent intent = getConfigureCallAssistantIntent();
            if (intent != null) {
                PhoneAccountHandle handle = mTelecomManager.getSimCallManager();
                UserHandle userHandle = handle.getUserHandle();
                try {
                    if (userHandle != null) {
                        getActivity().startActivityAsUser(intent, userHandle);
                    } else {
                        startActivity(intent);
                    }
                } catch (ActivityNotFoundException e) {
                    Log.d(LOG_TAG, "Could not resolve call assistant configure intent: " + intent);
                }
            }
            return true;
        }
		//added by zhaizhanfeng for SDN at151103 start
		if(pref == sdn){
			final Intent intent = new Intent();
            intent.setAction("com.android.contacts.action.checkSDN");
            startActivity(intent);  
            return true;
		}
		//added by zhaizhanfeng for SDN at151103 end
        return false;
    }

    /**
     * Handles a phone account selection, namely when a call assistant has been selected.
     *
     * @param pref The account selection preference which triggered the account selected event.
     * @param account The account selected.
     * @return True if the account selection has been handled, and false otherwise.
     */
    @Override
    public boolean onAccountSelected(AccountSelectionPreference pref, PhoneAccountHandle account) {
        if (pref == mDefaultOutgoingAccount) {
            mTelecomManager.setUserSelectedOutgoingPhoneAccount(account);
            Log.d(LOG_TAG, "onAccountSelected updateDefaultOutgoingAccountPreference");
            updateDefaultOutgoingAccountPreference();
            return true;
        } else if (pref == mSelectCallAssistant) {
            mTelecomManager.setSimCallManager(account);
            return true;
        }
        return false;
    }

    /**
     * Repopulate the dialog to pick up changes before showing.
     *
     * @param pref The account selection preference dialog being shown.
     */
    @Override
    public void onAccountSelectionDialogShow(AccountSelectionPreference pref) {
        if (pref == mDefaultOutgoingAccount) {
            updateDefaultOutgoingAccountsModel();
        } else if (pref == mSelectCallAssistant) {
            updateCallAssistantModel();
            updateConfigureCallAssistant();
        }
    }

    /**
     * Update the configure preference summary when the call assistant changes.
     */
    @Override
    public void onAccountChanged(AccountSelectionPreference pref) {
        if (pref == mSelectCallAssistant) {
            updateConfigureCallAssistant();
        /// M: Add for C2K solution 2 & 1.5 @{
        } else if (pref == mDefaultOutgoingAccount) {
            updateDefaultOutgoingAccountPreference();
        /// @}
        }
    }

    private synchronized void handleSipReceiveCallsOption(boolean isEnabled) {
        Context context = getActivity();
        if (context == null) {
            // Return if the fragment is detached from parent activity before executed by thread.
            return;
        }

        mSipSharedPreferences.setReceivingCallsEnabled(isEnabled);

        SipUtil.useSipToReceiveIncomingCalls(context, isEnabled);

        // Restart all Sip services to ensure we reflect whether we are receiving calls.
        SipAccountRegistry sipAccountRegistry = SipAccountRegistry.getInstance();
        sipAccountRegistry.restartSipService(context);
    }

    /**
     * Queries the telcomm manager to update the default outgoing account selection preference
     * with the list of outgoing accounts and the current default outgoing account.
     */
    private void updateDefaultOutgoingAccountsModel() {
        mDefaultOutgoingAccount.setModel(
                mTelecomManager,
                mTelecomManager.getCallCapablePhoneAccounts(),
                mTelecomManager.getUserSelectedOutgoingPhoneAccount(),
                getString(R.string.phone_accounts_ask_every_time));
    }

    /**
     * Queries the telecomm manager to update the account selection preference with the list of
     * call assistants, and the currently selected call assistant.
     */
    public void updateCallAssistantModel() {
        mSelectCallAssistant.setModel(
                mTelecomManager,
                mTelecomManager.getSimCallManagers(),
                mTelecomManager.getSimCallManager(),
                getString(R.string.wifi_calling_call_assistant_none));
    }

    /**
     * Shows or hides the "configure call assistant" preference.
     */
    private void updateConfigureCallAssistant() {
        Intent intent = getConfigureCallAssistantIntent();
        boolean shouldShow = intent != null && !getActivity().getPackageManager()
            .queryIntentActivities(intent, 0).isEmpty();

        PreferenceCategory callAssistantCategory = (PreferenceCategory)
                getPreferenceScreen().findPreference(CALL_ASSISTANT_CATEGORY_PREF_KEY);
        if (shouldShow) {
            callAssistantCategory.addPreference(mConfigureCallAssistant);
        } else {
            callAssistantCategory.removePreference(mConfigureCallAssistant);
        }
    }

    private void initAccountList() {
        List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (sil == null) {
            return;
        }
        ///M: Add for C2K solution2
        ///   When there are two CDMA cards, and is in home network.
        ///   There must be one SIM card(not the default data one) can't use.
        boolean shouldCheckDisable = TelephonyUtilsEx.isMultiCdmaCardInsertedInHomeNetwork();
        Log.d(LOG_TAG, "[initAccountList] shouldCheckDisable = " + shouldCheckDisable);

        /// M: Add for C2K solution 1.5 @{
        boolean shouldCheckCCardDisable = false;
        if (!FeatureOption.isMtkSvlteSolution2Support()
                && TelephonyUtilsEx.isCGCardInserted()
                && TelephonyUtilsEx.isCapabilityOnGCard()) {

            shouldCheckCCardDisable = true;
        }

        Log.d(LOG_TAG, "shouldCheckCCardDisable: " + shouldCheckCCardDisable);
        /// @}

        for (SubscriptionInfo subscription : sil) {
            CharSequence label = subscription.getDisplayName();
            Intent intent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            SubscriptionInfoHelper.addExtrasToIntent(intent, subscription);

            // / Modified by guofeiyao for EMUI style
            Preference accountPreference = new ArrowPreference(getActivity());
			// / End
            if(label.equals("CARD 1")){
            	accountPreference.setTitle(getResources().getString(R.string.slot_1));
            }else if(label.equals("CARD 2")){
            	accountPreference.setTitle(getResources().getString(R.string.slot_2));
			}else {
				accountPreference.setTitle(label);
			}
            accountPreference.setIntent(intent);
            mAccountList.addPreference(accountPreference);
            /// M: Add for C2K solution2 @{
            if (shouldCheckDisable && TelephonyUtilsEx.getMainPhoneId()
                    != SubscriptionManager.getPhoneId(subscription.getSubscriptionId())) {
                accountPreference.setEnabled(false);
                Log.d(LOG_TAG, "[initAccountList] disable " + subscription.getSubscriptionId());
            }
            /// @}

            /// M: Add for C2K solution 1.5 @{
            if (shouldCheckCCardDisable) {
                int phoneId = SubscriptionManager.from(getActivity()).getPhoneId(subscription.getSubscriptionId());
                if (!TelephonyUtilsEx.isGCardInserted(phoneId)) {
                    accountPreference.setEnabled(false);
                }
            }
        }
        /// @}
    }

    private Intent getConfigureCallAssistantIntent() {
        PhoneAccountHandle handle = mTelecomManager.getSimCallManager();
        if (handle != null) {
            String packageName = handle.getComponentName().getPackageName();
            if (packageName != null) {
                return new Intent(TelecomManager.ACTION_CONNECTION_SERVICE_CONFIGURE)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setPackage(packageName);
            }
        }
        return null;
    }

    //----------------------------MTK------------------------
    private CallFeaturesSettingExt mCallFeaturesSettingExt;
    private void onCreateMTK() {
        mCallFeaturesSettingExt = new CallFeaturesSettingExt(
                this, getPreferenceScreen());
        mCallFeaturesSettingExt.registerCallback();
        /// M: Add for C2K solution 2
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED);
        getActivity().registerReceiver(mReceiver, intentFilter);
    }

    private void onResumeMTK() {
        Log.d(LOG_TAG, "onResumeMTK");
        mCallFeaturesSettingExt.updatePrefScreen(getPreferenceScreen());
        mCallFeaturesSettingExt.init();
        mCallFeaturesSettingExt.updateScreenStatus();
        /// M: Add for plug-in
        ExtensionManager.getCallFeaturesSettingExt().initOtherCallFeaturesSetting(this);
        /// M: Add for C2K solution 2 & 1.5
        updateDefaultOutgoingAccountPreference();
    }

    @Override
    public void onDestroy() {
        mCallFeaturesSettingExt.unRegisterCallback();
        getActivity().unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    /**
     *  Add for C2K solution 2 improvement & 1.5
     *  Should disable mDefaultOutgoingAccount when:
     *  change account may cause SIM switch & airplane mode is on.
     */
    private void updateDefaultOutgoingAccountPreference() {
        if (mDefaultOutgoingAccount != null) {
            boolean mayCauseSimSwitch =
                    TelephonyUtilsEx.isMultiCdmaCardInsertedInHomeNetwork() ? true : false;

            if (!FeatureOption.isMtkSvlteSolution2Support()) {
                if (TelephonyUtilsEx.isCGCardInserted()) {
                    mayCauseSimSwitch = true;
                }
            } 

            Log.d(LOG_TAG, "updateDefaultOutgoingAccount mayCauseSimSiwtch:" + mayCauseSimSwitch);
            if (mayCauseSimSwitch) {
                boolean isAirplaneModeEnabled = TelephonyUtils.isAirplaneModeOn(getActivity());
                boolean isInSwitching = TelephonyUtilsEx.isCapabilitySwitching();
                Log.d(LOG_TAG, "updateDefaultOutgoingAccountPreference mAirplaneModeEnabled : "
                        + isAirplaneModeEnabled + "isInSwitching : " + isInSwitching);
                mDefaultOutgoingAccount.setEnabled(!isAirplaneModeEnabled && !isInSwitching);
            }
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "onReceive action = " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED) ||
                    action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE) ||
                    action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED)) {
                updateDefaultOutgoingAccountPreference();
            }
        }
    };

}
