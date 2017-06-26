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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.android.mms.data.WorkingMessage;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.MmsPluginManager;
import com.android.mms.R;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.util.FeatureOption;
import com.android.mms.util.MmsLog;

import com.mediatek.mms.ext.IMmsPreferenceExt;

import java.util.List;
import com.android.internal.telephony.PhoneConstants;
import android.os.SystemProperties;
/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MmsPreferenceActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "MmsPreferenceActivity";

    private static final boolean DEBUG = false;

    // Symbolic names for the keys used for preference lookup
    public static final String MMS_DELIVERY_REPORT_MODE = "pref_key_mms_delivery_reports";

    public static final String EXPIRY_TIME = "pref_key_mms_expiry";

    public static final String PRIORITY = "pref_key_mms_priority";

    public static final String READ_REPORT_MODE = "pref_key_mms_read_reports";

    public static final String MMS_SIZE_LIMIT = "pref_key_mms_size_limit";

    // M: add this for read report
    public static final String READ_REPORT_AUTO_REPLY = "pref_key_mms_auto_reply_read_reports";

    public static final String AUTO_RETRIEVAL = "pref_key_mms_auto_retrieval";

    public static final String RETRIEVAL_DURING_ROAMING = "pref_key_mms_retrieval_during_roaming";

    public static final String CREATION_MODE = "pref_key_mms_creation_mode";

    public static final String MMS_SETTINGS = "pref_key_mms_settings";
    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS = 1;

    /// M: google jb.mr1 patch, add for group mms
    public static final String GROUP_MMS_MODE = "pref_key_mms_group_mms";

    // all preferences need change key for single sim card
    private CheckBoxPreference mMmsDeliveryReport;

    private CheckBoxPreference mMmsReadReport;

    // M: add this for read report
    private CheckBoxPreference mMmsAutoReplyReadReport;

    private CheckBoxPreference mMmsAutoRetrieval;

    private CheckBoxPreference mMmsRetrievalDuringRoaming;

    // M: google jb.mr1 patch, add for group mms
    private CheckBoxPreference mMmsGroupMms;

    // all preferences need change key for multiple sim card
    private Preference mMmsDeliveryReportMultiSim;

    private Preference mMmsReadReportMultiSim;

    // M: add this for read report
    private Preference mMmsAutoReplyReadReportMultiSim;

    private Preference mMmsAutoRetrievalMultiSim;

    private Preference mMmsRetrievalDuringRoamingMultiSim;

    private ListPreference mMmsPriority;

    private ListPreference mMmsCreationMode;

	private ListPreference mMmsSizeLimit;
	
    private Preference mSmsSizeLimit; //modify by lipeng

    private static final String PRIORITY_HIGH = "High";

    private static final String PRIORITY_LOW = "Low";

    private static final String PRIORITY_NORMAL = "Normal";

    private static final String LOCATION_PHONE = "Phone";

    private static final String LOCATION_SIM = "Sim";

    private static final String CREATION_MODE_RESTRICTED = "RESTRICTED";

    private static final String CREATION_MODE_WARNING = "WARNING";

    private static final String CREATION_MODE_FREE = "FREE";

    private static final String SIZE_LIMIT_100 = "100";

    private static final String SIZE_LIMIT_200 = "200";

    private static final String SIZE_LIMIT_300 = "300";

    private Handler mSMSHandler = new Handler();

    private Handler mMMSHandler = new Handler();

    private EditText mNumberText;

    private AlertDialog mNumberTextDialog;

    private List<SubscriptionInfo> mListSubInfo;

    private int mCurrentSimCount = 0;

    private IMmsPreferenceExt mMmsPreferencePlugin;

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /// KK migration, for default MMS function. @{
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        MmsLog.d(TAG, "onResume sms enable? " + isSmsEnabled);
        if (!isSmsEnabled) {
            finish();
            return;
        }
        /// @}
        setListPrefSummary();
    }

    private void setListPrefSummary() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // For mMmsPriority;
        String stored = sp.getString(PRIORITY, getString(R.string.priority_normal));
        mMmsPriority.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_key_mms_priority_choices,
            R.array.pref_key_mms_priority_values));
        // For mMmsCreationMode
        stored = sp.getString(CREATION_MODE, CREATION_MODE_FREE);
        mMmsCreationMode.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_mms_creation_mode_choices,
            R.array.pref_mms_creation_mode_values));
        // For mMmsSizeLimit
        stored = sp.getString(MMS_SIZE_LIMIT, SIZE_LIMIT_300);
        //modify by lipeng
        if(FeatureOption.HQ_MMS_APP_MMSSIZE){
           mSmsSizeLimit.setSummary(MessageUtils.getMmsSizeForEuro()); 
        }else{
           mMmsSizeLimit.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_mms_size_limit_choices,
        			R.array.pref_mms_size_limit_values));
        }
        //end by lipeng
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        MmsLog.d(TAG, "onCreate");
        mCurrentSimCount = SubscriptionManager.from(this).getActiveSubscriptionInfoCount();
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(getResources().getString(R.string.actionbar_mms_setting));
        actionBar.setDisplayHomeAsUpEnabled(true);
        setMessagePreferences();
    }

    private void setMessagePreferences() {

        mCurrentSimCount = SubscriptionManager.from(this).getActiveSubscriptionInfoCount();
        MmsLog.d(TAG, "mCurrentSimCount is :" + mCurrentSimCount);
        // / M: fix bug ALPS00421364
        if (mCurrentSimCount == 0) {
            addPreferencesFromResource(R.xml.mmspreferences);
            mMmsDeliveryReport = (CheckBoxPreference) findPreference(MMS_DELIVERY_REPORT_MODE);
            mMmsDeliveryReport.setEnabled(false);
            mMmsReadReport = (CheckBoxPreference) findPreference(READ_REPORT_MODE);
            mMmsReadReport.setEnabled(false);
            mMmsAutoReplyReadReport = (CheckBoxPreference) findPreference(READ_REPORT_AUTO_REPLY);
            mMmsAutoReplyReadReport.setEnabled(false);
            mMmsAutoRetrieval = (CheckBoxPreference) findPreference(AUTO_RETRIEVAL);
            mMmsAutoRetrieval.setEnabled(false);
            mMmsRetrievalDuringRoaming = (CheckBoxPreference) findPreference(RETRIEVAL_DURING_ROAMING);
            mMmsRetrievalDuringRoaming.setEnabled(false);
            // / @}
        } else if (mCurrentSimCount == 1) {
            addPreferencesFromResource(R.xml.mmspreferences);
        } else {
            addPreferencesFromResource(R.xml.mmsmulticardpreferences);
        }

        PreferenceCategory mmsCategory = (PreferenceCategory) findPreference("pref_key_mms_settings");
        mMmsPreferencePlugin = (IMmsPreferenceExt) MmsPluginManager
            .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_PREFERENCE);
        mMmsPreferencePlugin.configMmsPreference(MmsPreferenceActivity.this, mmsCategory, mCurrentSimCount);

        // M: add for read report
        if (!FeatureOption.MTK_SEND_RR_SUPPORT) {
            // remove read report entry
            MmsLog.d(MmsApp.TXN_TAG, "remove the read report entry, it should be hidden.");
            PreferenceCategory mmOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
            mmOptions.removePreference(findPreference(READ_REPORT_AUTO_REPLY));
        }
        // M: google jb.mr1 patch, add for group mms
        if (!MmsConfig.getGroupMmsEnabled()) {
            // remove group mms entry
            MmsLog.d(MmsApp.TXN_TAG, "remove the group mms entry, it should be hidden.");
            PreferenceCategory mmOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
            mmOptions.removePreference(findPreference(GROUP_MMS_MODE));
        }
        mMmsPriority = (ListPreference) findPreference(PRIORITY);
        mMmsPriority.setOnPreferenceChangeListener(this);
        mMmsCreationMode = (ListPreference) findPreference(CREATION_MODE);
        mMmsCreationMode.setOnPreferenceChangeListener(this);
        
        //add by lipeng for mms size not allow to edit
        if(FeatureOption.HQ_MMS_APP_MMSSIZE){
            mSmsSizeLimit = (Preference) findPreference(MMS_SIZE_LIMIT);
        //this is al813 project, user should not edit mms size;
		  //mMmsSizeLimit.setEnabled(false);
		  mSmsSizeLimit.setEnabled(false);
        }else{
        	mMmsSizeLimit = (ListPreference) findPreference(MMS_SIZE_LIMIT);
            mMmsSizeLimit.setOnPreferenceChangeListener(this);
        }
        PreferenceCategory MMSOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
        MMSOptions.removePreference(findPreference(MMS_SIZE_LIMIT));
        //end by lipeng
        
/*
        if (MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
            mListSubInfo = SubscriptionManager.getActiveSubInfoList();
            mMmsReadReport = (CheckBoxPreference) findPreference(READ_REPORT_MODE);
            mMmsAutoReplyReadReport = (CheckBoxPreference) findPreference(READ_REPORT_AUTO_REPLY);*/
         //   if (FeatureOption.EVDO_DT_SUPPORT /*&& MessageUtils.isUSimType(mListSimInfo.get(0).getSlot())*/) {
/*
                mMmsAutoReplyReadReport.setEnabled(false);
                mMmsReadReport.setEnabled(false);
            }
        }*/

        if (!MmsConfig.getMmsEnabled()) {
            // No Mms, remove all the mms-related preferences
            PreferenceCategory mmsOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
            getPreferenceScreen().removePreference(mmsOptions);
        }
        // Change the key to the SIM-related key, if has one SIM card, else set default value.
        if (mCurrentSimCount == 1) {
            MmsLog.d(TAG, "single sim");
            changeSingleCardKeyToSimRelated();
        } else if (mCurrentSimCount > 1) {
            setMultiCardPreference();
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
        MmsLog.d(TAG, "changeSingleCardKeyToSimRelated Got subId = " + subId);
        // translate all key to SIM-related key;
        mMmsDeliveryReport = (CheckBoxPreference) findPreference(MMS_DELIVERY_REPORT_MODE);
        mMmsReadReport = (CheckBoxPreference) findPreference(READ_REPORT_MODE);
        // M: add this for read report
        mMmsAutoReplyReadReport = (CheckBoxPreference) findPreference(READ_REPORT_AUTO_REPLY);
        if (FeatureOption.MTK_C2K_SUPPORT && MessageUtils.isUSimType(subId)) {
            mMmsAutoReplyReadReport.setEnabled(false);
            mMmsReadReport.setEnabled(false);
        }
        mMmsAutoRetrieval = (CheckBoxPreference) findPreference(AUTO_RETRIEVAL);
        mMmsRetrievalDuringRoaming = (CheckBoxPreference) findPreference(RETRIEVAL_DURING_ROAMING);
        mMmsDeliveryReport.setKey(Long.toString(subId) + "_" + MMS_DELIVERY_REPORT_MODE);
        mMmsReadReport.setKey(Long.toString(subId) + "_" + READ_REPORT_MODE);
        // M: add this for read report
        if (mMmsAutoReplyReadReport != null) {
            mMmsAutoReplyReadReport.setKey(Long.toString(subId) + "_" + READ_REPORT_AUTO_REPLY);
        }
        // M: google jb.mr1 patch, add for group mms
        mMmsGroupMms = (CheckBoxPreference) findPreference(GROUP_MMS_MODE);
        if (mMmsGroupMms != null) {
            mMmsGroupMms.setKey(GROUP_MMS_MODE);
        }
        mMmsAutoRetrieval.setKey(Long.toString(subId) + "_" + AUTO_RETRIEVAL);
        mMmsRetrievalDuringRoaming.setDependency(Long.toString(subId) + "_" + AUTO_RETRIEVAL);
        mMmsRetrievalDuringRoaming.setKey(Long.toString(subId) + "_" + RETRIEVAL_DURING_ROAMING);
        // get the stored value
        SharedPreferences sp = getSharedPreferences("com.android.contacts_preferences", MODE_WORLD_READABLE);
        if (mMmsDeliveryReport != null) {
            mMmsDeliveryReport.setChecked(sp.getBoolean(mMmsDeliveryReport.getKey(), false));
        }
        if (mMmsReadReport != null) {
            mMmsReadReport.setChecked(sp.getBoolean(mMmsReadReport.getKey(), false));
        }
        // M: add for read report
        if (mMmsAutoReplyReadReport != null) {
            mMmsAutoReplyReadReport.setChecked(sp.getBoolean(mMmsAutoReplyReadReport.getKey(), false));
        }
        if (mMmsAutoRetrieval != null) {
            mMmsAutoRetrieval.setChecked(sp.getBoolean(mMmsAutoRetrieval.getKey(), true));
        }
        if (mMmsRetrievalDuringRoaming != null) {
            mMmsRetrievalDuringRoaming.setChecked(sp.getBoolean(mMmsRetrievalDuringRoaming.getKey(), false));
        }
        //HQ_wuruijun add for HQ01340494 start
        boolean isRadioOn = SubSelectAdapter.isRadioOn(singleCardInfo.getSubscriptionId());
        mMmsRetrievalDuringRoaming.setEnabled(isRadioOn);
        //HQ_wuruijun add end
        // M: google jb.mr1 patch, add for group mms
        if (mMmsGroupMms != null) {
            mMmsGroupMms.setChecked(sp.getBoolean(mMmsGroupMms.getKey(), false));
        }
    }

    private void setMultiCardPreference() {
        mMmsDeliveryReportMultiSim = findPreference(MMS_DELIVERY_REPORT_MODE);

        mMmsReadReportMultiSim = findPreference(READ_REPORT_MODE);
        // M: add this for read report
        mMmsAutoReplyReadReportMultiSim = findPreference(READ_REPORT_AUTO_REPLY);
        mMmsAutoRetrievalMultiSim = findPreference(AUTO_RETRIEVAL);
        mMmsRetrievalDuringRoamingMultiSim = findPreference(RETRIEVAL_DURING_ROAMING);
        // M: google jb.mr1 patch, add for group mms
        // get the stored value
        SharedPreferences sp = getSharedPreferences(MessageUtils.DEFAULT_MMS_PREFERENCE_NAME, MODE_WORLD_READABLE);
        mMmsGroupMms = (CheckBoxPreference) findPreference(GROUP_MMS_MODE);
        if (mMmsGroupMms != null) {
            mMmsGroupMms.setKey(GROUP_MMS_MODE);
        }
        if (mMmsGroupMms != null) {
            mMmsGroupMms.setChecked(sp.getBoolean(mMmsGroupMms.getKey(), false));
        }
        mMmsDeliveryReportMultiSim.setKey(MMS_DELIVERY_REPORT_MODE);
        mMmsReadReportMultiSim.setKey(READ_REPORT_MODE);
        mMmsAutoReplyReadReportMultiSim.setKey(READ_REPORT_AUTO_REPLY);
        mMmsAutoRetrievalMultiSim.setKey(AUTO_RETRIEVAL);
        mMmsRetrievalDuringRoamingMultiSim.setKey(RETRIEVAL_DURING_ROAMING);
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
            // The user clicked on the Messaging icon in the action bar. Take them back from
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
        if (preference == mMmsDeliveryReportMultiSim
            || preference == mMmsReadReportMultiSim
            // M: add this for read report
            || preference == mMmsAutoReplyReadReportMultiSim || preference == mMmsAutoRetrievalMultiSim
            || preference == mMmsRetrievalDuringRoamingMultiSim) {
            Intent it = new Intent();
            it.setClass(this, SubSelectActivity.class);
            it.putExtra(SmsPreferenceActivity.PREFERENCE_KEY, preference.getKey());
            if (preference == mMmsDeliveryReportMultiSim) {
                it.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID, R.string.pref_title_mms_delivery_reports);
            } else if (preference == mMmsReadReportMultiSim) {
                it.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID, R.string.pref_title_mms_read_reports);
            } else if (preference == mMmsAutoReplyReadReportMultiSim) {
                it.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID, R.string.pref_title_mms_auto_reply_read_reports);
            } else if (preference == mMmsAutoRetrievalMultiSim) {
                it.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID, R.string.pref_title_mms_auto_retrieval);
            } else if (preference == mMmsRetrievalDuringRoamingMultiSim) {
                it.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID, R.string.pref_title_mms_retrieval_during_roaming);
            }
            startActivity(it);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void restoreDefaultPreferences() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MmsPreferenceActivity.this)
                .edit();
        mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (mListSubInfo != null) {
            int simCount = mListSubInfo.size();
            if (simCount > 0) {
                for (int i = 0; i < simCount; i++) {
                    int subId = mListSubInfo.get(i).getSubscriptionId();
                    editor.putBoolean(Long.toString(subId) + "_" + MMS_DELIVERY_REPORT_MODE, false);
                    editor.putBoolean(Long.toString(subId) + "_" + READ_REPORT_MODE, false);
                    editor.putBoolean(Long.toString(subId) + "_" + READ_REPORT_AUTO_REPLY, false);
                    editor.putBoolean(Long.toString(subId) + "_" + AUTO_RETRIEVAL, true);
                    editor.putBoolean(Long.toString(subId) + "_" + RETRIEVAL_DURING_ROAMING, false);
                }
            }
        }

        editor.putString(CREATION_MODE, CREATION_MODE_FREE);
        editor.putString(MMS_SIZE_LIMIT, SIZE_LIMIT_300);
        editor.putString(PRIORITY, PRIORITY_NORMAL);
        /// M: fix bug ALPS00432361, restore default preferences
        /// about GroupMms and ShowEmailAddress @{
        editor.putBoolean(GROUP_MMS_MODE, false);
        mMmsPreferencePlugin.configMmsPreferenceEditorWhenRestore(this, editor);
        /// @}
        editor.apply();
        setPreferenceScreen(null);
        setMessagePreferences();
        setListPrefSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        final String key = arg0.getKey();
/*
        int slotId = 0;
        int simCount = SubscriptionManager.getActiveSubInfoCount();
        if (simCount == 1) {
            slotId = SubscriptionManager.getActiveSubInfoList().get(0).getSimSlotIndex();
        }
        */
        String stored = (String) arg1;
        if (PRIORITY.equals(key)) {
            mMmsPriority.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_key_mms_priority_choices,
                R.array.pref_key_mms_priority_values));
        } else if (MMS_SIZE_LIMIT.equals(key)) {
        	//modify by lipeng
            if(FeatureOption.HQ_MMS_APP_MMSSIZE){
                mSmsSizeLimit.setSummary(MessageUtils.getMmsSizeForEuro()); 
          }else{
        	   mMmsSizeLimit.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_mms_size_limit_choices,
                      R.array.pref_mms_size_limit_values));
          }
            //end by lipeng
            MmsConfig.setUserSetMmsSizeLimit(Integer.valueOf(stored));
        } else if (CREATION_MODE.equals(key)) {
            mMmsCreationMode.setSummary(MessageUtils.getVisualTextName(this, stored, R.array.pref_mms_creation_mode_choices,
                R.array.pref_mms_creation_mode_values));
            mMmsCreationMode.setValue(stored);
            WorkingMessage.updateCreationMode(this);
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
//        new EncapsulatedListView(this.getListView()).clearScrapViewsIfNeeded();//csw
    }
    // For the group mms feature to be enabled, the following must be true:
    //  1. the feature is enabled in mms_config.xml (currently on by default)
    //  2. the feature is enabled in the mms settings page
    //  3. the SIM knows its own phone number
    public static boolean getIsGroupMmsEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean groupMmsPrefOn = prefs.getBoolean(MmsPreferenceActivity.GROUP_MMS_MODE, false);
        boolean isKnowNumber = false;
            isKnowNumber =
               !TextUtils.isEmpty(MessageUtils.getLocalNumber(PhoneConstants.SIM_ID_1))
            || !TextUtils.isEmpty(MessageUtils.getLocalNumber(PhoneConstants.SIM_ID_2));
        return MmsConfig.getGroupMmsEnabled() && groupMmsPrefOn; // && isKnowNumber;
    }

    /// M: fix bug ALPS00421364, update sim state dynamically. @{
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mSimReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mSimReceiver != null) {
            unregisterReceiver(mSimReceiver);
        }
    }

    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                mListSubInfo = SubscriptionManager.from(MmsApp.getApplication())
                        .getActiveSubscriptionInfoList();
                if (mListSubInfo != null) {
                    int updateSimCount = mListSubInfo.isEmpty() ? 0 : mListSubInfo.size();
                    if (mCurrentSimCount == updateSimCount) {
                        return;
                    }
                    mCurrentSimCount = updateSimCount;
                    setPreferenceScreen(null);
                    setMessagePreferences();
                    setListPrefSummary();
                }
            }
        }
    };
    /// @}
}
