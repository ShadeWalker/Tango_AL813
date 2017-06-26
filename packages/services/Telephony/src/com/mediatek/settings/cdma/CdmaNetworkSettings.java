package com.mediatek.settings.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.phone.MobileNetworkSettings;
import com.android.phone.R;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants.CardType;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.IMobileNetworkSettingsExt;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.settings.cdma.TelephonyUtilsEx;
import com.mediatek.telephony.TelephonyManagerEx;

public class CdmaNetworkSettings {

    private static final String TAG = "CdmaNetworkSettings";
    private static final String SINGLE_LTE_DATA = "single_lte_data";
    /// The preference indicated which network mode does user wanted.
    private static final String ENABLE_4G_DATA = "enable_4g_data";
    private static final String ROAMING_KEY = "button_roaming_key";
    private SwitchPreference mDataOnlyPreference;
    private SwitchPreference mEnable4GDataPreference;
    private Phone mPhone;
    private PreferenceActivity mActivity;
    private int mSubId = -1;

    private boolean mIsLTECardType;

    private IntentFilter mIntentFilter;
    // Fix CR ALPS02054770
    private static boolean mIsSwitching = false;
    private static final String INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE =
        "com.mediatek.intent.action.FINISH_SWITCH_SVLTE_RAT_MODE";
    private static final String INTENT_ACTION_CARD_TYPE =
        "android.intent.action.CDMA_CARD_TYPE";

    private PhoneStateListener mPhoneStateListener;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "on receive broadcast action = " + action);

            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
                if (slotId == SvlteModeController.getActiveSvlteModeSlotId()) {
                    updateSwitch();
                }
                return;
            } else if (INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE.equals(action)) {
                int svlteMode = intent.getIntExtra(SvlteRatController.EXTRA_SVLTE_RAT_MODE, -1);
                Log.d(TAG,"svlteMode = " + svlteMode);
                //If finished broadcast extra svlte mode is the same with 
                // current database saved mode, it means switch done.
                if (svlteMode == getCDMARatMode()) {
                    mIsSwitching = false;
                }
            } else if (TelephonyIntents.ACTION_CDMA_CARD_TYPE.equals(intent.getAction())) {
                CardType cardType = (CardType)
                intent.getExtra(TelephonyIntents.INTENT_KEY_CDMA_CARD_TYPE);
                if (cardType.equals(IccCardConstants.CardType.CT_4G_UICC_CARD)) {
                    mIsLTECardType = true;
                } else {
                    mIsLTECardType = false;
                }
                Log.i(TAG, "intent cardType = " + cardType.toString()
                        + ", isLTECardType " + mIsLTECardType);
            }
            // If ACTION_AIRPLANE_MODE_CHANGED received, we just update the switch.
            updateSwitch();
        }
    };

    private ContentObserver mDataConnectionObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "onChange selfChange=" + selfChange);
            if (!selfChange) {
                updateSwitch();
            }
        }
    };


    public CdmaNetworkSettings(PreferenceActivity activity, PreferenceScreen prefSet, Phone phone) {
        mActivity = activity;
        mPhone = phone;
        mSubId = phone.getSubId();

        /// M: remove GSM items @{
        if (prefSet.findPreference(MobileNetworkSettings.BUTTON_ENABLED_NETWORKS_KEY) != null) {
            prefSet.removePreference(prefSet.findPreference(MobileNetworkSettings.BUTTON_ENABLED_NETWORKS_KEY));
        }
        if (prefSet.findPreference(MobileNetworkSettings.BUTTON_PREFERED_NETWORK_MODE) != null) {
            prefSet.removePreference(prefSet.findPreference(MobileNetworkSettings.BUTTON_PREFERED_NETWORK_MODE));
        }
        if (prefSet.findPreference(MobileNetworkSettings.BUTTON_PLMN_LIST) != null) {
            prefSet.removePreference(prefSet.findPreference(MobileNetworkSettings.BUTTON_PLMN_LIST));
        }
        /// @}

        addCdmaSettingsItem(activity, phone);
        register();
    }

    /**
     * Need to add two more items for CDMA OM project:
     * 1. Enable 4G network
     * 2. Enable 4G only
     */
    private void addCdmaSettingsItem(PreferenceActivity activity, Phone phone) {
        /// add TDD data only feature
        Log.d(TAG, "addCdmaSettingsItem");
        mIsLTECardType = TelephonyUtilsEx.isLTE(mPhone);
        addEnable4GNetworkItem(activity);
        addDataOnlyItem(activity);
        updateSwitch();
    }

    private void addDataOnlyItem(PreferenceActivity activity) {
        mDataOnlyPreference = new SwitchPreference(activity);
        mDataOnlyPreference.setTitle(activity.getString(R.string.only_use_LTE_data));
        mDataOnlyPreference.setKey(SINGLE_LTE_DATA);
        mDataOnlyPreference.setSummaryOn(activity.getString(R.string.only_use_LTE_data_summary));
        mDataOnlyPreference.setSummaryOff(activity.getString(R.string.only_use_LTE_data_summary));
        mDataOnlyPreference.setOrder(activity.getPreferenceScreen().findPreference(ENABLE_4G_DATA).getOrder() + 1);
        activity.getPreferenceScreen().addPreference(mDataOnlyPreference);
    }

    private void addEnable4GNetworkItem(PreferenceActivity activity) {
        IMobileNetworkSettingsExt ext = ExtensionManager.getMobileNetworkSettingsExt();
        if (mEnable4GDataPreference == null) {
            mEnable4GDataPreference = new SwitchPreference(activity);
            mEnable4GDataPreference.setTitle(R.string.enable_4G_data);
            mEnable4GDataPreference.setKey(ENABLE_4G_DATA);
            mEnable4GDataPreference.setSummary(R.string.enable_4G_data_summary);
            Preference pref = activity.getPreferenceScreen().findPreference(ROAMING_KEY);
            if (pref != null) {
                mEnable4GDataPreference.setOrder(pref.getOrder() + 1);
            }
        }
        activity.getPreferenceScreen().addPreference(mEnable4GDataPreference);
    }

    private void updateSwitch() {
        int ratMode = getCDMARatMode();
        boolean enable = (isLteCardReady() && !mIsSwitching && isCapabilityPhone())
                          || FeatureOption.isMtkCtTestCardSupport();
        Log.d(TAG, "mIsSwitching = " + mIsSwitching + " ratMode = " + ratMode
              + " enable = " + enable);
        mEnable4GDataPreference.setEnabled(enable);
        mEnable4GDataPreference.setChecked(
                enable && ratMode != TelephonyManagerEx.SVLTE_RAT_MODE_3G);
		//HQ_xionghaifeng 20151008 add for HQ01430024 start
		mEnable4GDataPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (preference.getKey().equals(ENABLE_4G_DATA)) {
					boolean checked = ((Boolean) newValue).booleanValue();
					//SwitchPreference switchPre = (SwitchPreference) preference;
					mEnable4GDataPreference.setChecked(checked);
		            handleEnable4GDataClick(preference);
		            return true;
		        }
		        return false;
            }
        });
		//HQ_xionghaifeng 20151008 add for HQ01430024 end
		
        /// if data close && default data is not sim one, tdd data only can not choose
        boolean dataEnable = TelephonyManager.getDefault().getDataEnabled();
        int slotId = SubscriptionManager.getSlotId(SubscriptionManager.getDefaultDataSubId());
        boolean isCTEnableData = dataEnable &&
                    (SvlteModeController.getActiveSvlteModeSlotId() == slotId);
        boolean dataOnlyCommon = enable && !TelephonyUtilsEx.isCTRoaming(mPhone) &&
                    ratMode != TelephonyManagerEx.SVLTE_RAT_MODE_3G;
        boolean dataOnlyEnabled = dataOnlyCommon && isCTEnableData;
        Log.d(TAG, "updateSwitch dataOnlyCommon = " + dataOnlyCommon
              + " dataEnable = " + dataEnable);
        mDataOnlyPreference.setEnabled(dataOnlyEnabled || FeatureOption.isMtkCtTestCardSupport());
        mDataOnlyPreference.setChecked(dataOnlyCommon &&
                    ratMode == TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY);
    }

    private boolean isLteCardReady() {
        boolean simInserted = TelephonyUtilsEx.isSvlteSlotInserted();
        boolean airPlaneMode = TelephonyUtilsEx.isAirPlaneMode();
        boolean callStateIdle = isCallStateIDLE();
        boolean simStateIsReady = isSimStateReady(
                mActivity, SvlteModeController.getActiveSvlteModeSlotId());
        boolean isReady = mIsLTECardType && simInserted && !airPlaneMode && callStateIdle && simStateIsReady;
        Log.d(TAG,"isLTECardType = " + mIsLTECardType + " simInserted = " + simInserted +
                  " airPlaneMode = " + airPlaneMode + " callStateIdle = " + callStateIdle +
                  " simStateIsReady = " + simStateIsReady + " isReady = " + isReady);
        return isReady;
    }

    private int getCDMARatMode() {
        int mode = Settings.Global.getInt(mActivity.getContentResolver(),
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId), TelephonyManagerEx.SVLTE_RAT_MODE_4G);
        Log.d(TAG, "getCDMARatMode mode = " + mode);
        return mode;
    }

    private boolean isCallStateIDLE() {
        TelephonyManager telephonyManager =
            (TelephonyManager) mActivity.getSystemService(Context.TELEPHONY_SERVICE);
        int currPhoneCallState = telephonyManager.getCallState();
        Log.i(TAG, "mobile isCallStateIDLE = " +
                (currPhoneCallState == TelephonyManager.CALL_STATE_IDLE));
        return currPhoneCallState == TelephonyManager.CALL_STATE_IDLE;
    }

    private void register() {
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntentFilter.addAction(INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE);
        mIntentFilter.addAction(INTENT_ACTION_CARD_TYPE);
        mActivity.registerReceiver(mReceiver, mIntentFilter);
        Log.d(TAG, "registerReceiver:" + mReceiver);
        mActivity.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId)),
                true, mDataConnectionObserver);
        TelephonyManager telephonyManager =
                (TelephonyManager) mActivity.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneStateListener = new PhoneStateListener(mSubId) {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                Log.d(TAG, "PhoneStateListener, onCallStateChanged new state=" + state);
                updateSwitch();
            }

            @Override
            public void onDataConnectionStateChanged(int state) {
                super.onDataConnectionStateChanged(state);
                Log.d(TAG, "PhoneStateListener, onDataConnectionStateChanged new state=" + state);
                updateSwitch();
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                // Add for bug fix ALPS01954204
                Log.d(TAG, "PhoneStateListener:onServiceStateChanged: state: " + state);
                if (mDataOnlyPreference != null && state != null) {
                    updateSwitch();
                }
            }
        };
        telephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_SERVICE_STATE);
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        updateSwitch();
    }

    public void onDestroy() {
        Log.d(TAG, "unregisterReceiver:" + mReceiver);
        mActivity.unregisterReceiver(mReceiver);
        mActivity.getContentResolver().unregisterContentObserver(mDataConnectionObserver);
        TelephonyManager.getDefault().listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

    }

    public void resetState() {
        //Reset the value in case quit the mobilenetwork settings
        mIsSwitching = false;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference.getKey().equals(SINGLE_LTE_DATA)) {
            handleDataOnlyClick(preference);
            return true;
        } else if (preference.getKey().equals(ENABLE_4G_DATA)) {
            handleEnable4GDataClick(preference);
            return true;
        }
        return false;
    }

    private void handleEnable4GDataClick(Preference preference) {
        SwitchPreference switchPre = (SwitchPreference) preference;
        boolean isChecked = switchPre.isChecked();
        int ratMode = isChecked ? TelephonyManagerEx.SVLTE_RAT_MODE_4G :
                               TelephonyManagerEx.SVLTE_RAT_MODE_3G;
        Log.d(TAG,"isChecked = " + isChecked + " ratMode = " + ratMode);
        Settings.Global.putInt(mActivity.getContentResolver(),
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId), ratMode);
        int networkType = isChecked ?
                (SvlteRatController.RAT_MODE_SVLTE_2G
                 | SvlteRatController.RAT_MODE_SVLTE_3G
                 | SvlteRatController.RAT_MODE_SVLTE_4G)
                 : (SvlteRatController.RAT_MODE_SVLTE_2G
                    | SvlteRatController.RAT_MODE_SVLTE_3G);
        switchSvlte(networkType);
        mIsSwitching = true;
        updateSwitch();
    }

    /*
     * Enable 4G the rat mode will be 2/3/4G, turn off enable 4G will be only 2/3G
     * 2/3/4G : 
     * 2/3G :
     */
    private void switchSvlte(int networkType) {
        Log.d(TAG, "value = " + networkType);
        LteDcPhoneProxy lteDcPhoneProxy = (LteDcPhoneProxy) mPhone;
        lteDcPhoneProxy.getSvlteRatController().setRadioTechnology(networkType, null);
	/*liruihong 20150923 add for HWEMUI system.apk HQ01393421 start*/
	Log.i("liruihong","CdmaNetworkSettings:sentBroadCast");
	Intent intent1 = new Intent("com.android.huawei.PREFERRED_NETWORK_MODE_DATABASE_CHANGED");
	mPhone.getContext().sendBroadcast(intent1);
	/*liruihong 20150923 add for HWEMUI system.apk HQ01393421 start*/
    }

    private void handleDataOnlyClick(Preference preference) {
        SwitchPreference swp = (SwitchPreference) preference;
        boolean isChecked = swp.isChecked();
        Log.i(TAG, "handleDataOnlyClick isChecked = " + isChecked);
        Settings.Global.putInt(mActivity.getContentResolver(),
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId),
                (isChecked ? TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY
                        : TelephonyManagerEx.SVLTE_RAT_MODE_4G));
        int networkType = isChecked ? SvlteRatController.RAT_MODE_SVLTE_4G
                : (SvlteRatController.RAT_MODE_SVLTE_2G
                   | SvlteRatController.RAT_MODE_SVLTE_3G
                   | SvlteRatController.RAT_MODE_SVLTE_4G);
        switchSvlte(networkType);
        mIsSwitching = true;
        updateSwitch();
    }

    /**
     * judge if sim state is ready.
     * sim state:SIM_STATE_UNKNOWN = 0;SIM_STATE_ABSENT = 1
     * SIM_STATE_PIN_REQUIRED = 2;SIM_STATE_PUK_REQUIRED = 3;
     * SIM_STATE_NETWORK_LOCKED = 4;SIM_STATE_READY = 5;
     * SIM_STATE_CARD_IO_ERROR = 6;
     * @param context Context
     * @param simId sim id
     * @return true if is SIM_STATE_READY
     */
    static boolean isSimStateReady(Context context, int simId) {
        int simState = TelephonyManager.from(context).getSimState(simId);
        Log.i(TAG, "isSimStateReady simState=" + simState);
        return simState == TelephonyManager.SIM_STATE_READY;
    }

    /**
     * Check if phone has 4G capability.
     */
    private boolean isCapabilityPhone() {
        boolean result = TelephonyUtilsEx.getMainPhoneId() == mPhone.getPhoneId();
        Log.d(TAG, "isCapabilityPhone result = " + result
                + " phoneId = " + mPhone.getPhoneId());
        return result;
    }
}
