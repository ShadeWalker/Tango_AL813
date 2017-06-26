/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Telephony;

import android.text.InputFilter;
import android.view.inputmethod.EditorInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.MmsPluginManager;
import com.android.mms.R;
import com.android.mms.util.FeatureOption;
import com.android.mms.util.MmsLog;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.mms.ext.IMmsFailedNotifyExt;
import com.mediatek.mms.ext.IStringReplacementExt;
import com.mediatek.mms.ext.IMmsPreferenceExt;

import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telephony.TelephonyManagerEx;

import java.util.List;

import android.telephony.SubscriptionInfo;
import android.os.SystemProperties;//add by lipeng
import java.util.Locale;    //add by lipeng
import android.view.Gravity;    //add by lipeng
import android.view.View;    //add by lipeng
import android.util.Log;
/**
 * With this activity, users can set preferences for MMS and SMS and can access
 * and manipulate SMS messages stored on the SIM.
 */
public class SmsPreferenceActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SmsPreferenceActivity";

    private static final boolean DEBUG = false;

    public static final String SMS_DELIVERY_REPORT_MODE = "pref_key_sms_delivery_reports";

    public static final String SMS_QUICK_TEXT_EDITOR = "pref_key_quick_text_editor";

    public static final String SMS_SERVICE_CENTER = "pref_key_sms_service_center";

    public static final String SMS_MANAGE_SIM_MESSAGES = "pref_key_manage_sim_messages";

    public static final String SMS_SAVE_LOCATION = "pref_key_sms_save_location";

    public static final String SMS_INPUT_MODE = "pref_key_sms_input_mode";

    public static final String SMS_SETTINGS = "pref_key_sms_settings";

    public static final String PREFERENCE_KEY = "PREFERENCE_KEY";

    public static final String PREFERENCE_TITLE_ID = "PREFERENCE_TITLE";

    public static final String SETTING_SAVE_LOCATION = "Phone";
	/*HQ_zhangjing 2015-07-24 modified for sms forward begin*/
    public static final String SMS_FORWARD_WITH_SENDER = "pref_key_forward_with_sender";

    // / M: fix bug ALPS00437648, restoreDefaultPreferences in tablet
    public static final String SETTING_SAVE_LOCATION_TABLET = "Device";

    //public static final String SETTING_INPUT_MODE = "Automatic";
    public static final String SETTING_INPUT_MODE = MessageUtils.defaultEncodeType;//modify by lipeng for 7bit
    private static final String MMS_PREFERENCE = "com.android.contacts_preferences";

    public static final String SDCARD_DIR_PATH = "//sdcard//message//";

    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS = 1;

    private static final int MAX_EDITABLE_LENGTH = 20;

    private Preference mSmsQuickTextEditorPref;

    private Preference mManageSimPref;

    private Preference mSmsServiceCenterPref;

    // MTK_OP01_PROTECT_END
    // all preferences need change key for single sim card
    private CheckBoxPreference mSmsDeliveryReport;

    private CheckBoxPreference mSmsForwardWithSender;

    // all preferences need change key for multiple sim card
    private Preference mSmsDeliveryReportMultiSim;

    private Preference mSmsServiceCenterPrefMultiSim;

    private Preference mManageSimPrefMultiSim;

    private Preference mSmsSaveLoactionMultiSim;

    private ListPreference mSmsLocation;

    private ListPreference mSmsInputMode;

    private static final String LOCATION_PHONE = "Phone";

    private static final String LOCATION_SIM = "Sim";

    private EditText mNumberText;

    private List<SubscriptionInfo> mListSubInfo;

    private AlertDialog mNumberTextDialog;

    private int mCurrentSimCount = 0;

    // / M: fix bug ALPS00455172, add tablet "device" support
    private static final String DEVICE_TYPE = "pref_key_device_type";

    // / M: Plug-in for OP09.
    private IStringReplacementExt mStringReplacementPlugin;

    private IMmsFailedNotifyExt mMmsFailedNotifyPlugin;

    // /M: plug-in for OP01
    private IMmsPreferenceExt mMmsPreferencePlugin;

    @Override
    protected void onResume() {
        super.onResume();
        // / KK migration, for default MMS function. @{
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        MmsLog.d(TAG, "onResume sms enable? " + isSmsEnabled);
        if (!isSmsEnabled) {
            finish();
            return;
        }
        // / @}
        setListPrefSummary();
    }

    @Override
    protected void onDestroy() {
        if (mSimReceiver != null) {
            unregisterReceiver(mSimReceiver);
        }
        super.onDestroy();
    }

    private void setListPrefSummary() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // / M: fix bug ALPS00455172, add tablet "device" support
        SharedPreferences.Editor editor = sp.edit();
        if (!getResources().getBoolean(R.bool.isTablet)) {
            editor.putString(DEVICE_TYPE, "Phone");
        } else {
            editor.putString(DEVICE_TYPE, "Device");
        }
        editor.commit();

        // For mSmsLocation;
        String saveLocation = "Phone";

        List<SubscriptionInfo> subInfos = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (subInfos != null && subInfos.size() == 1) {
            int subId = subInfos.get(0).getSubscriptionId();
            saveLocation = sp.getString((Long.toString(subId) + "_" + SMS_SAVE_LOCATION), "Phone");
        }

        if (getResources().getBoolean(R.bool.isTablet)) {
            // /@ M: fix bug ALPS00823808, avoid empty of the sms stroe lactaion
            // at the first time.
            if ("Phone".equals(saveLocation)) {
                saveLocation = "Device";
            }
            mSmsLocation.setSummary(MessageUtils.getVisualTextName(this, saveLocation,
                    R.array.pref_tablet_sms_save_location_choices,
                    R.array.pref_tablet_sms_save_location_values));
            // /@
        } else {
            mSmsLocation.setSummary(MessageUtils.getVisualTextName(this, saveLocation,
                    R.array.pref_sms_save_location_choices, R.array.pref_sms_save_location_values));
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        MmsLog.d(TAG, "onCreate");

        // / M: For OP09 Feature.
        mStringReplacementPlugin = (IStringReplacementExt) MmsPluginManager
                .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_STRING_REPLACEMENT);
        mMmsFailedNotifyPlugin = (IMmsFailedNotifyExt) MmsPluginManager
                .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_FAILED_NOTIFY);
        // /M: for OP01 Feature
        mMmsPreferencePlugin = (IMmsPreferenceExt) MmsPluginManager
                .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_PREFERENCE);

        ActionBar actionBar = getActionBar();
        actionBar.setTitle(getResources().getString(R.string.actionbar_sms_setting));
        actionBar.setDisplayHomeAsUpEnabled(true);
        setMessagePreferences();
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        intentFilter.addAction(Telephony.Sms.Intents.SMS_STATE_CHANGED_ACTION);
        registerReceiver(mSimReceiver, intentFilter);

    }

    private void setMessagePreferences() {
        mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        MmsLog.d(TAG, "setMessagePreferences mListSubInfo :" + mListSubInfo);
        mCurrentSimCount = mListSubInfo == null ? 0 : mListSubInfo.size();
        MmsLog.d(TAG, "mCurrentSimCount is :" + mCurrentSimCount);

        if (mCurrentSimCount <= 1) {
            addPreferencesFromResource(R.xml.smspreferences);
        } else {
            addPreferencesFromResource(R.xml.smsmulticardpreferences);
        }
        mSmsLocation = (ListPreference) findPreference(SMS_SAVE_LOCATION);
        mSmsLocation.setOnPreferenceChangeListener(this);
        mSmsQuickTextEditorPref = findPreference(SMS_QUICK_TEXT_EDITOR);
        ///M: do not show sim messages when sim is off. @{
        PreferenceCategory smsCategory = (PreferenceCategory) findPreference(SMS_SETTINGS);
        mManageSimPref = findPreference(SMS_MANAGE_SIM_MESSAGES);
		
		/*HQ_zhangjing 2015-07-24 modified for sms forward begin*/		
		//CheckBoxPreference sp = (CheckBoxPreference)findPreference( SMS_FORWARD_WITH_SENDER );
		//if( sp == null ){
			//Log.d(TAG,"not op01,add forward sender ");
			//addForwardWithSenderPreference(SmsPreferenceActivity.this, smsCategory);
		//}
		/*HQ_zhangjing 2015-07-24 modified for sms forward end*/

		
        if (!MessageUtils.isSimMessageAccessable(this)) {
            // If there is no SIM, this item will be disabled and can not be
            // accessed.
            mManageSimPref.setEnabled(false);
        }
        /// @}

        if (mCurrentSimCount <= 1) {
            mSmsServiceCenterPref = findPreference(SMS_SERVICE_CENTER);
            // No SIM card, remove the SIM-related prefs
            // smsCategory.removePreference(mManageSimPref);
            // If there is no SIM, this item will be disabled and can
            // not be accessed.
            if (!MmsConfig.getSIMSmsAtSettingEnabled()) {
                smsCategory.removePreference(mManageSimPref);
            }
            // MTK_OP02_PROTECT_END
            if (mCurrentSimCount == 0) {
                mSmsDeliveryReport = (CheckBoxPreference) findPreference(SMS_DELIVERY_REPORT_MODE);
                mSmsDeliveryReport.setEnabled(false);
                mSmsServiceCenterPref.setEnabled(false);
                // / M: fix bug ALPS00455172, add tablet "device" support
                if (!getResources().getBoolean(R.bool.isTablet)) {
                    mSmsLocation.setValue("Phone");
                } else {
                    mSmsLocation.setValue("Device");
                }
                mSmsLocation.setEnabled(false);
            } else {
                MmsLog.d(TAG, "single sim");
                //HQ_wuruijun add for HQ01340494 start
                boolean isRadioOn = SubSelectAdapter.isRadioOn(mListSubInfo.get(0).getSubscriptionId());
                mSmsServiceCenterPref.setEnabled(isRadioOn);
                mManageSimPref.setEnabled(isRadioOn);
                //HQ_wuruijun add end
                changeSingleCardKeyToSimRelated();
                /// M: add for OP09 feature, replace string "SIM" with "UIM". @{
                String[] location = mStringReplacementPlugin.getSaveLocationString();
                if (MmsConfig.isStringReplaceEnable() && location != null/*&& MessageUtils.isUSimType(slotId)*/) {
                    mSmsLocation.setEntries(location);
                }
            }
        } else {
            setMultiCardPreference();
        }

        addSmsInputModePreference();
        mMmsPreferencePlugin.configSmsPreference(SmsPreferenceActivity.this, smsCategory,
                mCurrentSimCount);

    }
	/*HQ_zhangjing 2015-07-24 modified for sms forward begin*/
    private void addForwardWithSenderPreference(Context context, PreferenceCategory smsCategory) {
        Log.d(TAG, "Call addForwardWithSenderPref");
        CheckBoxPreference sp = new CheckBoxPreference(context);
        sp.setKey(SMS_FORWARD_WITH_SENDER);
        sp.setTitle(getString(R.string.sms_forward_setting));
        sp.setSummary(getString(R.string.sms_forward_setting_summary));
        smsCategory.addPreference(sp);
    }
	/*HQ_zhangjing 2015-07-24 modified for sms forward end*/
    // add input mode setting for op03 request, if not remove it.
    private void addSmsInputModePreference() {
        if (MmsConfig.isEnableSmsEncodingType()) {
            mSmsInputMode = (ListPreference) findPreference(SMS_INPUT_MODE);
           //add by lipeng for 7bit
            if(SystemProperties.get("ro.hq.mms.ap.sevenbit").equals("1")) {
               if(!MessageUtils.smsEncodeCanClick())
                       mSmsInputMode.setEnabled(false); 
            }
            //end by lipeng
        } else {
            PreferenceCategory smsCategory = (PreferenceCategory) findPreference("pref_key_sms_settings");
            mSmsInputMode = (ListPreference) findPreference(SMS_INPUT_MODE);
            if (mSmsInputMode != null) {
                smsCategory.removePreference(mSmsInputMode);
            }
        }
    }

    private void changeSingleCardKeyToSimRelated() {
        // get to know which one
        mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (mListSubInfo == null) {
            return;
        }
        SubscriptionInfo singleCardInfo = null;
        if (mListSubInfo.size() != 0) {
            singleCardInfo = mListSubInfo.get(0);
        }
        if (singleCardInfo == null) {
            return;
        }
        int subId = mListSubInfo.get(0).getSubscriptionId();
        MmsLog.d(TAG, "changeSingleCardKeyToSimRelated Got simId = " + subId);
        // translate all key to SIM-related key;
        mSmsDeliveryReport = (CheckBoxPreference) findPreference(SMS_DELIVERY_REPORT_MODE);
        mSmsServiceCenterPref = findPreference(SMS_SERVICE_CENTER);
        mManageSimPref = findPreference(SMS_MANAGE_SIM_MESSAGES);

        // / M: For OP09 Feature, replace "SIM" with "UIM". @{
        String ctString = mStringReplacementPlugin
                .getStrings(IStringReplacementExt.MANAGE_CARD_MSG_TITLE);
        if (MmsConfig.isStringReplaceEnable() && ctString != null) {
            mManageSimPref.setTitle(ctString);
            mManageSimPref.setSummary(mStringReplacementPlugin
                    .getStrings(IStringReplacementExt.MANAGE_CARD_MSG_SUMMARY));
        }
        // / @}

        mManageSimPrefMultiSim = null;
        PreferenceCategory smsCategory = (PreferenceCategory) findPreference("pref_key_sms_settings");

        mSmsLocation = (ListPreference) findPreference(SMS_SAVE_LOCATION);
        mSmsLocation.setKey(Long.toString(subId) + "_" + SMS_SAVE_LOCATION);
        SharedPreferences spr = getSharedPreferences("com.android.contacts_preferences",
                MODE_WORLD_READABLE);
        // /M fix bug ALPS00837998;
        if (!getResources().getBoolean(R.bool.isTablet)) {
            mSmsLocation.setValue(spr.getString((Long.toString(subId) + "_" + SMS_SAVE_LOCATION),
                    "Phone"));
        } else {
            mSmsLocation.setValue(spr.getString((Long.toString(subId) + "_" + SMS_SAVE_LOCATION),
                    "Device"));
        }
        // /@

        if (!MmsConfig.getSIMSmsAtSettingEnabled()) {
            if (mManageSimPref != null) {
                smsCategory.removePreference(mManageSimPref);
            }
        }
        // MTK_OP02_PROTECT_END
        mSmsDeliveryReport.setKey(Long.toString(subId) + "_" + SMS_DELIVERY_REPORT_MODE);
        // get the stored value
        SharedPreferences sp = getSharedPreferences(MMS_PREFERENCE, MODE_WORLD_READABLE);
        if (mSmsDeliveryReport != null) {
            mSmsDeliveryReport.setChecked(sp.getBoolean(mSmsDeliveryReport.getKey(), false));
        }
    }

    private void setMultiCardPreference() {
        mSmsDeliveryReportMultiSim = findPreference(SMS_DELIVERY_REPORT_MODE);
        mSmsServiceCenterPrefMultiSim = findPreference(SMS_SERVICE_CENTER);
        mManageSimPrefMultiSim = findPreference(SMS_MANAGE_SIM_MESSAGES);

        // / M: For OP09 Feature, replace "SIM" with "UIM". @{
        String ctString = mStringReplacementPlugin
                .getStrings(IStringReplacementExt.MANAGE_CARD_MSG_TITLE);
        if (MmsConfig.isStringReplaceEnable() && ctString != null) {
            mManageSimPrefMultiSim.setTitle(ctString);
            mManageSimPrefMultiSim.setSummary(mStringReplacementPlugin
                    .getStrings(IStringReplacementExt.MANAGE_CARD_MSG_SUMMARY));
        }
        // / @}

        mManageSimPref = null;
        PreferenceCategory smsCategory = (PreferenceCategory) findPreference(SMS_SETTINGS);

        if (mSmsLocation != null) {
            smsCategory.removePreference(mSmsLocation);
            Preference saveLocationMultiSim = new Preference(this);
            saveLocationMultiSim.setKey(SMS_SAVE_LOCATION);
            saveLocationMultiSim.setTitle(R.string.sms_save_location);
            saveLocationMultiSim.setSummary(R.string.sms_save_location);
            smsCategory.addPreference(saveLocationMultiSim);
            mSmsSaveLoactionMultiSim = findPreference(SMS_SAVE_LOCATION);
        }

        if (!MmsConfig.getSIMSmsAtSettingEnabled()) {
            if (mManageSimPrefMultiSim != null) {
                smsCategory.removePreference(mManageSimPrefMultiSim);
            }
        }
        mSmsDeliveryReportMultiSim.setKey(SMS_DELIVERY_REPORT_MODE);

    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string.restore_default);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar.
                // Take them back from
                // wherever they came from
                finish();
                return true;
            case MENU_RESTORE_DEFAULTS:
                restoreDefaultPreferences();
                return true;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mManageSimPref) {
            mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfoList();
            if (mListSubInfo != null) {
                int subId = mListSubInfo.get(0).getSubscriptionId();
                MmsLog.d(TAG, "slotId is : " + subId);
                if (subId != -1) {
                    Intent it = new Intent();
                    it.setClass(this, ManageSimMessages.class);
                    it.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
                    startActivity(it);
                }
            }
        } else if (preference == mSmsQuickTextEditorPref) {
            Intent intent = new Intent();
            intent.setClass(this, SmsTemplateEditActivity.class);
            startActivity(intent);
        } else if (preference == mSmsDeliveryReportMultiSim) {
            Intent it = new Intent();
            it.setClass(this, SubSelectActivity.class);
            it.putExtra(PREFERENCE_KEY, preference.getKey());
            it.putExtra(PREFERENCE_TITLE_ID, R.string.pref_title_sms_delivery_reports);
            startActivity(it);
        } else if (preference == mSmsDeliveryReport && MmsConfig.isDeliveryReportInRoamingEnable()) {
            // / M: for OP09 feature.
            mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfoList();
            if (mListSubInfo != null) {
                int id = mListSubInfo.get(0).getSimSlotIndex();
                if (mSmsDeliveryReport.isChecked() && !MmsConfig.isAllowDRWhenRoaming(this, id)) {
                    mSmsDeliveryReport.setChecked(false);
                    if (mMmsFailedNotifyPlugin != null) {
                        mMmsFailedNotifyPlugin.popupToast(getApplicationContext(),
                                IMmsFailedNotifyExt.DISABLE_DELIVERY_REPORT, null);
                    }
                }
            }
        } else if (preference == mSmsServiceCenterPref) {
            mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfoList();
            if (mListSubInfo == null || mListSubInfo.isEmpty()) {
                MmsLog.d(TAG, "there is no sim card");
                return true;
            }
            int id = mListSubInfo.get(0).getSubscriptionId();
            if (FeatureOption.MTK_C2K_SUPPORT && TelephonyManager.getDefault()
                    .getCurrentPhoneType(id) == PhoneConstants.PHONE_TYPE_CDMA) {
                showToast(R.string.cdma_not_support);
                return true;
            }

            Bundle result = TelephonyManagerEx.getDefault().getScAddressWithErroCode(id);
            if (result != null
                    && result.getByte(TelephonyManagerEx.GET_SC_ADDRESS_KEY_RESULT) == TelephonyManagerEx.ERROR_CODE_NO_ERROR) {
                // Means Success to get SC Address
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                mNumberText = new EditText(dialog.getContext());
                mNumberText.setHint(R.string.type_to_compose_text_enter_to_send);
				// add by lipeng for ar langruage
				String locale = Locale.getDefault().getLanguage();
				if (locale.equals("ar") || locale.equals("fa")) {
					mNumberText.setTextDirection(View.TEXT_DIRECTION_LTR);
					mNumberText.setGravity(Gravity.RIGHT);
				}// end lipeng
                mNumberText.computeScroll();
                mNumberText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(
                        MAX_EDITABLE_LENGTH) });
                mNumberText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_CLASS_PHONE);
                String gotScNumber = (String) result
                        .getCharSequence(TelephonyManagerEx.GET_SC_ADDRESS_KEY_ADDRESS);
                MmsLog.d(TAG, "gotScNumber is: " + gotScNumber);

                mNumberText.setText(gotScNumber);
                mNumberText.setTextColor(Color.GRAY);//HQ_wuruijun add for HQ01599781
                //add by lipeng for gotScNumber not editable HQ01453023
				if (SystemProperties.get("ro.hq.mms.scnumber.uneditable").equals("1")) {
					mNumberText.setFocusable(false);
				}//end by lipeng 
                /*HQ_zhangjing modified for CQ HQ01365396 */
                mNumberText.setSelection(gotScNumber.length());
                //add by wanghui for disable edittext
				if(getResources().getBoolean(R.bool.eidit_disable)){
				mNumberText.setEnabled(false);
			    }
                //mNumberText.setTextColor(R.color.black);
                mNumberTextDialog = dialog.setIcon(R.drawable.ic_dialog_info_holo_light).setTitle(
                        R.string.sms_service_center).setView(mNumberText).setPositiveButton(
                        R.string.OK, new PositiveButtonListener()).setNegativeButton(
                        R.string.Cancel, new NegativeButtonListener()).show();
            } else {
                MmsLog.d(TAG, "getScAddress error: " + result);
                showToast(R.string.sms_not_ready);
            }
        } else if (preference == mSmsServiceCenterPrefMultiSim
                || preference == mManageSimPrefMultiSim
                || (preference == mSmsSaveLoactionMultiSim && mCurrentSimCount > 1)) {
            Intent it = new Intent();
            it.setClass(this, SubSelectActivity.class);
            it.putExtra(PREFERENCE_KEY, preference.getKey());
            if (preference == mSmsServiceCenterPrefMultiSim) {
                it.putExtra(PREFERENCE_TITLE_ID, R.string.sms_service_center);
            } else if (preference == mManageSimPrefMultiSim) {
                it.putExtra(PREFERENCE_TITLE_ID, R.string.pref_title_manage_sim_messages);
            } else if (preference == mSmsSaveLoactionMultiSim) {
                it.putExtra(PREFERENCE_TITLE_ID, R.string.sms_save_location);
            }
            startActivity(it);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    // / M: added for bug ALPS00314789 begin
    private boolean isValidAddr(String address) {
        boolean ret = true;
        if (address.isEmpty()) {
            return ret;
        }
        if (address.charAt(0) == '+') {
            for (int i = 1, count = address.length(); i < count; i++) {
                if (address.charAt(i) < '0' || address.charAt(i) > '9') {
                    ret = false;
                    break;
                }
            }
        } else {
            for (int i = 0, count = address.length(); i < count; i++) {
                if (address.charAt(i) < '0' || address.charAt(i) > '9') {
                    ret = false;
                    break;
                }
            }
        }
        return ret;
    }

    // / M: added for bug ALPS00314789 end
    private class PositiveButtonListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // write to the SIM Card.
            // / M: added for bug ALPS00314789 begin
            if (!isValidAddr(mNumberText.getText().toString())) {
                String num = mNumberText.getText().toString();
                String strUnSpFormat = getResources().getString(R.string.unsupported_media_format,
                        "");
                Toast.makeText(getApplicationContext(), strUnSpFormat, Toast.LENGTH_SHORT).show();
                return;
            }
            // / M: added for bug ALPS00314789 end
            final int subId;
            subId = mListSubInfo.get(0).getSubscriptionId();
            final String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
            new Thread(new Runnable() {
                public void run() {
                	if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language
                		 TelephonyManagerEx.getDefault().setScAddress(subId, mNumberText.getText().toString().replaceAll("\u202D", "").replaceAll("\u202C", ""));
                	}else{
                		 TelephonyManagerEx.getDefault().setScAddress(subId, mNumberText.getText().toString());
                	}
                }
            }).start();
        }
    }

    private class NegativeButtonListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // cancel
            dialog.dismiss();
        }
    }

    private void restoreDefaultPreferences() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                SmsPreferenceActivity.this).edit();
        mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (mListSubInfo != null) {
            int subCount = mListSubInfo.size();
            if (subCount > 0) {
                for (int i = 0; i < subCount; i++) {
                    int subId = mListSubInfo.get(i).getSubscriptionId();
                    editor.putBoolean(Long.toString(subId) + "_" + SMS_DELIVERY_REPORT_MODE, false);
                    /// M: fix bug ALPS00437648, restoreDefaultPreferences
                    // in tablet
                    if (!getResources().getBoolean(R.bool.isTablet)) {
                        editor.putString(Long.toString(subId) + "_" + SMS_SAVE_LOCATION,
                                SETTING_SAVE_LOCATION);
                    } else {
                        editor.putString(Long.toString(subId) + "_" + SMS_SAVE_LOCATION,
                                SETTING_SAVE_LOCATION_TABLET);
                    }
                }
            }
        }
        if (MmsConfig.isEnableSmsEncodingType()) {
            editor.putString(SMS_INPUT_MODE, SETTING_INPUT_MODE);
        }
		/*HQ_zhangjing 2015-07-24 modified for sms forward */
        editor.putBoolean(SMS_FORWARD_WITH_SENDER, true);

        mMmsPreferencePlugin.configSmsPreferenceEditorWhenRestore(this, editor);
        editor.apply();
        setPreferenceScreen(null);
        setMessagePreferences();
        setListPrefSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object arg1) {
        if (preference == mSmsLocation && !(mCurrentSimCount > 1)) {
            if (!getResources().getBoolean(R.bool.isTablet)) {
                mSmsLocation.setSummary(MessageUtils.getVisualTextName(this, (String) arg1,
                        R.array.pref_sms_save_location_choices,
                        R.array.pref_sms_save_location_values));
            } else {
                mSmsLocation.setSummary(MessageUtils.getVisualTextName(this, (String) arg1,
                        R.array.pref_tablet_sms_save_location_choices,
                        R.array.pref_tablet_sms_save_location_values));
            }
        }
        return true;
    }

    private void showToast(int id) {
        Toast t = Toast.makeText(getApplicationContext(), getString(id), Toast.LENGTH_SHORT);
        t.show();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        MmsLog.d(TAG, "onConfigurationChanged: newConfig = " + newConfig + ",this = " + this);
        super.onConfigurationChanged(newConfig);
        // for Migration change,maybe need check
        (this.getListView()).clearScrapViewsIfNeeded();
    }

    // / M: update sim state dynamically. @{

    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            MmsLog.d(TAG, "mSimReceiver action = " + action);
            Dialog locationDialog = mSmsLocation.getDialog();
            if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED) ||
                    action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED) ||
                    action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED) ||
                    action.equals(Telephony.Sms.Intents.SMS_STATE_CHANGED_ACTION)) {
                mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                        .getActiveSubscriptionInfoList();
                int updateSimCount = mListSubInfo == null ? 0 : mListSubInfo.size();
                if (mNumberTextDialog != null && mNumberTextDialog.isShowing()) {
                    mNumberTextDialog.dismiss();
                }
                if (locationDialog != null && locationDialog.isShowing()) {
                    locationDialog.dismiss();
                }
                if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)
                        && (mCurrentSimCount == updateSimCount)) {
                    return;
                }
                mCurrentSimCount = updateSimCount;
                setPreferenceScreen(null);
                setMessagePreferences();
                setListPrefSummary();
            }
        }
    };

}
