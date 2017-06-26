/*
 * Copyright Statement:
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/*
 * MediaTek Inc. (C) 2010. All rights reserved.
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.mms.MmsApp;
import com.android.mms.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;/*HQ_sunli	20150918 HQ01390936 begin */
import android.media.ExifInterface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.util.Log;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.android.mms.MmsConfig;
import com.android.mms.util.Recycler;
import com.android.mms.data.WorkingMessage;
import com.android.mms.util.MmsLog;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;

import com.android.mms.util.FeatureOption;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/*HQ_caohaolin  add for cl812 to add sms signature begin */
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.DialogInterface.OnClickListener;
import android.text.InputFilter;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.os.Build;
/*HQ_caohaolin  add for cl812 to add sms signature end */

/**
 * This Message settings fragment is only for op09.
 */
public class ClassifyGeneralFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "ClassifyGeneralFragment";

    private Activity mActivity;

    // general sms settings.
    public static final String SMS_QUICK_TEXT_EDITOR = "pref_key_quick_text_editor";

    // general mms settings.
    public static final String GROUP_MMS_MODE = "pref_key_mms_group_mms";

    public static final String CREATION_MODE = "pref_key_mms_creation_mode";

    public static final String MMS_SIZE_LIMIT = "pref_key_mms_size_limit";

    public static final String PRIORITY = "pref_key_mms_priority";

    public static final String MMS_SETTINGS = "pref_key_mms_settings";

    /*HQ_caohaolin  add for cl812 to add sms signature begin */
    public static final String SIGNATURE = "pref_key_sms_signature";
    public static final String ENABLE_SIGNATURE = "pref_key_sms_enable_signature";
    public static final String PERSONAL_SIGNATURE = "pref_key_sms_personal_signature";
    public static final String KEY_SIGNATURE_INFO = "key_signature_info";
    public static final String KEY_SIGNATURE = "key_signature";
    public static boolean mSignatureChecked;
    private SharedPreferences mSignatureInfo;
    private Editor mEditor;
    private CheckBoxPreference mEnableSignatruePref;
    private Preference mPersonalSignatruePref;
    private EditText mSignatureText;
    private AlertDialog mSignatureTextDialog;
    /*HQ_caohaolin  add for cl812 to add sms signature end */

    // notification settings.
    public static final String NOTIFICATION_MUTE = "pref_key_mute";

    public static final String NOTIFICATION_VIBRATE = "pref_key_vibrate";

    public static final String POPUP_NOTIFICATION = "pref_key_popup_notification";

    public static final String NOTIFICATION_ENABLED = "pref_key_enable_notifications";

    public static final String NOTIFICATION_RINGTONE = "pref_key_ringtone";

    public static final String MUTE_START = "mute_start";

    // general settings.
    public static final String DISPLAY_PREFERENCE = "pref_key_display_preference_settings";

    public static final String GENERAL_CHAT_WALLPAPER = "pref_key_chat_wallpaper";

    public static final String FONT_SIZE_SETTING = "pref_key_message_font_size";

    public static final String SHOW_EMAIL_ADDRESS = "pref_key_show_email_address";

    public static final String STORAGE_SETTING = "pref_key_storage_settings";

    public static final String AUTO_DELETE = "pref_key_auto_delete";

    public static final String MMS_DELETE_LIMIT = "pref_key_mms_delete_limit";

    public static final String SMS_DELETE_LIMIT = "pref_key_sms_delete_limit";

    public static final String WAPPUSH_SETTING = "pref_key_wappush_settings";

    public static final String WAPPUSH_ENABLED = "pref_key_wappush_enable";

    public static final String DEFAULT_SMS = "pref_key_default_sms";

    public static final String MAX_SMS_PER_THREAD = "MaxSmsMessagesPerThread";

    public static final String MAX_MMS_PER_THREAD = "MaxMmsMessagesPerThread";

    private static final String IS_FOLLOW_NOTIFICATION = "is_follow_notification";
    // System ring tone path start with "content://media/internal/audio/media/".
    // If the ring tone file is added by user, like put music under storage/Notifications folder,
    // then the ring tone URI start with this.
    private static final String EXTERNAL_RINGTONE_PATH = "content://media/external/audio/media/";
	/*HQ_sunli	20150918 HQ01390936 begin */
	public static final String STORAGE_STATUS = "pref_title_storage_status";
	private Preference mStorageStatus;
	/*HQ_sunli	20150918 HQ01390936 end */

    // some variables.
    private static final int FONT_SIZE_DIALOG = 10;

    public static final String TEXT_SIZE = "message_font_size";

    public static final int TEXT_SIZE_DEFAULT = 18;

    private NumberPickerDialog mSmsDisplayLimitDialog;

    private NumberPickerDialog mMmsDisplayLimitDialog;

    private Handler mSMSHandler = new Handler();

    private Handler mMMSHandler = new Handler();

    private ProgressDialog mProgressDialog = null;

    private String mChatWallpaperUri = "";

    private String mWallpaperPathForCamera = "";

    private static final int PICK_WALLPAPER = 2;

    private static final int PICK_GALLERY = 3;

    private static final int PICK_PHOTO = 4;

    public static final int MMS_SIZE_LIMIT_DEFAULT = 50;

    public static final int SMS_SIZE_LIMIT_DEFAULT = 500;

    public static final String SIZE_LIMIT_300 = "300";

    public static final String CREATION_MODE_FREE = "FREE";

    public static final String DEFAULT_RINGTONE = "content://settings/system/notification_sound";

    public static final String PRIORITY_NORMAL = "Normal";

    // wallpaper chooser dialog.
    private int[] mWallpaperImage = new int[] {R.drawable.wallpaper_launcher_wallpaper,
        R.drawable.wallpaper_launcher_gallery, R.drawable.wallpaper_launcher_camera,
        R.drawable.wallpaper_launcher_default, };

    private int[] mWallpaperText = new int[] {R.string.dialog_wallpaper_chooser_wallpapers,
        R.string.dialog_wallpaper_chooser_gallery, R.string.dialog_wallpaper_chooser_take,
        R.string.dialog_wallpaper_chooser_default};

    // sms preference.
    private Preference mSmsQuickTextEditorPref;

    // mms preference.
    private CheckBoxPreference mMmsGroupMms;

    private ListPreference mMmsPriority;

    private ListPreference mMmsCreationMode;

    private ListPreference mMmsSizeLimit;

    // notification preference.
    private CheckBoxPreference mEnableNotificationsPref;

    private MmsRingtonePreference mRingtonePref;

    private CheckBoxPreference mVibratePref;

    private CheckBoxPreference mPopupNotificationPref;

    private ListPreference mNotificaitonMute;

    // general preference.
    private Preference mChatWallpaperPref;

    private Preference mSmsLimitPref;

    private Preference mMmsLimitPref;

    private Recycler mSmsRecycler;

    private Recycler mMmsRecycler;

    private Preference mFontSize;

    private CheckBoxPreference mShowEmailPref;

    private Preference mDefaultSms;

    private AsyncDialog mAsyncDialog;

    private String[] mFontSizeChoices;

    private String[] mFontSizeValues;

    private static final String MMS_PREFERENCE = "com.android.contacts_preferences";

    public static final String CHAT_SETTINGS_URI = "content://mms-sms/thread_settings";

    public static final String GENERAL_WALLPAPER_FOR_PROVIDER = "/data/data/com.android.providers.telephony/app_wallpaper/general_wallpaper.jpeg";

    private int mCurrentSubCount = 0;

    private static final String PICTURE_SUFFIX = ".jpeg";

    private boolean mIsSmsEnabled = true;

    private static final String SMS_SETTING_GENERAL = "pref_key_sms_settings";

    private static final String MMS_SETTING_GENERAL = "pref_key_mms_settings";

    private static final String NOTIFICATION_SETTING_GENERAL = "pref_key_notification_settings";

    private static final String DISPLAY_SETTING_GENERAL = "pref_key_display_preference_settings";

    private static final String STORAGE_SETTING_GENERAL = "pref_key_storage_settings";

    private static final String WAPPUSH_SETTING_GENERAL = "pref_key_wappush_settings";

    // public ClassifyGeneralFragment(Activity activity) {
    // mActivity = activity;
    // }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.v(TAG, "onCreate()");
        // save wallpaper path.
        if (icicle != null && icicle.containsKey("wallpaperCameraPath")) {
            mWallpaperPathForCamera = icicle.getString("wallpaperCameraPath", "");
        }
        addPreferencesFromResource(R.xml.generalslotpreference);
        setMessagePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        setEnabledNotificationsPref();
        setListPrefSummary();
        setDefaultSmsValue();
        mIsSmsEnabled = MmsConfig.isSmsEnabled(mActivity);
        if (mIsSmsEnabled) {
            setCategoryEnable();
        } else {
            setCategoryDisable();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        Log.d(TAG, "onAttach");
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach");
        mActivity = null;
        super.onDetach();
    }

    public static ClassifyGeneralFragment newInstance() {
        return new ClassifyGeneralFragment();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "mWallpaperPathForCamera: " + mWallpaperPathForCamera);
        outState.putString("wallpaperCameraPath", mWallpaperPathForCamera);
    }

    // @Override
    // public void onConfigurationChanged(Configuration newConfig) {
    // Log.d(TAG, "onConfigurationChanged: newConfig = " + newConfig);
    // super.onConfigurationChanged(newConfig);
    // new EncapsulatedListView(mActivity.getListView()).clearScrapViewsIfNeeded();
    // }
    private void setEnabledNotificationsPref() {
        mEnableNotificationsPref.setChecked(getNotificationEnabled(mActivity));
    }

    private void setListPrefSummary() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        // mms listpreference.
        String stored = sp.getString(PRIORITY, getString(R.string.priority_normal));
        mMmsPriority.setSummary(MessageUtils.getVisualTextName(mActivity, stored,
            R.array.pref_key_mms_priority_choices, R.array.pref_key_mms_priority_values));
        stored = sp.getString(CREATION_MODE, CREATION_MODE_FREE);
        mMmsCreationMode.setSummary(MessageUtils.getVisualTextName(mActivity, stored,
            R.array.pref_mms_creation_mode_choices, R.array.pref_mms_creation_mode_values));
        stored = sp.getString(MMS_SIZE_LIMIT, SIZE_LIMIT_300);
        mMmsSizeLimit.setSummary(MessageUtils.getVisualTextName(mActivity, stored, R.array.pref_mms_size_limit_choices,
            R.array.pref_mms_size_limit_values));
        // notification preference.
        long mMuteStart = sp.getLong(MUTE_START, 0);
        int mMuteOrigin = Integer.parseInt(sp.getString(NOTIFICATION_MUTE, "0"));
        if (mMuteStart > 0 && mMuteOrigin > 0) {
            Log.d(TAG, "thread mute timeout, reset to default.");
            int currentTime = (int) (System.currentTimeMillis() / 1000);
            if ((mMuteOrigin * 3600 + mMuteStart / 1000) <= currentTime) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mActivity).edit();
                editor.putLong(NotificationPreferenceActivity.MUTE_START, 0);
                editor.putString(NOTIFICATION_MUTE, "0");
                editor.commit();
            }
        }
        String notificationMute = sp.getString(NOTIFICATION_MUTE, "0");
        mNotificaitonMute.setSummary(MessageUtils.getVisualTextName(mActivity, notificationMute,
            R.array.pref_mute_choices, R.array.pref_mute_values));
    }

    public static boolean getNotificationEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean notificationsEnabled = prefs.getBoolean(NOTIFICATION_ENABLED, true);
        return notificationsEnabled;
    }

    private void enablePushSetting() {
        PreferenceCategory wapPushOptions = (PreferenceCategory) findPreference(WAPPUSH_SETTING);
        if (!FeatureOption.MTK_WAPPUSH_SUPPORT) {
            if (getPreferenceScreen() != null) {
                getPreferenceScreen().removePreference(wapPushOptions);
            }
        }
    }

    private void setMmsDisplayLimit() {
        mMmsLimitPref
                .setSummary(getString(R.string.pref_summary_delete_limit, mMmsRecycler.getMessageLimit(mActivity)));
    }

    private void setSmsDisplayLimit() {
        mSmsLimitPref
                .setSummary(getString(R.string.pref_summary_delete_limit, mSmsRecycler.getMessageLimit(mActivity)));
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        final String key = arg0.getKey();
        String preferenceChange = (String) arg1;
        // Mms change.
        if (PRIORITY.equals(key)) {
            mMmsPriority.setSummary(MessageUtils.getVisualTextName(mActivity, preferenceChange,
                R.array.pref_key_mms_priority_choices, R.array.pref_key_mms_priority_values));
        } else if (MMS_SIZE_LIMIT.equals(key)) {
            mMmsSizeLimit.setSummary(MessageUtils.getVisualTextName(mActivity, preferenceChange,
                R.array.pref_mms_size_limit_choices, R.array.pref_mms_size_limit_values));
            MmsConfig.setUserSetMmsSizeLimit(Integer.valueOf(preferenceChange));
        } else if (CREATION_MODE.equals(key)) {
            mMmsCreationMode.setSummary(MessageUtils.getVisualTextName(mActivity, preferenceChange,
                R.array.pref_mms_creation_mode_choices, R.array.pref_mms_creation_mode_values));
            mMmsCreationMode.setValue(preferenceChange);
            WorkingMessage.updateCreationMode(mActivity);
        }
        // notification change.
        if (NOTIFICATION_MUTE.equals(key)) {
            CharSequence mMute = MessageUtils.getVisualTextName(mActivity, preferenceChange, R.array.pref_mute_choices,
                R.array.pref_mute_values);
            mNotificaitonMute.setSummary(mMute);
            Log.d(TAG, "preference change: " + mMute.toString());
            if (preferenceChange.equals("0")) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = sp.edit();
                editor.putLong(MUTE_START, 0);
                editor.commit();
            } else {
                Long muteTime = System.currentTimeMillis();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = sp.edit();
                editor.putLong(MUTE_START, muteTime);
                editor.commit();
            }
        }
      /*HQ_sunli 20151104 HQ01479277 begin */
	if (NOTIFICATION_RINGTONE.equals(key)) {
        setRingtoneSummary(preferenceChange);
        }
      /*HQ_sunli 20151104 HQ01479277 end */
        return true;
    }

    private void setMessagePreferences() {
        mDefaultSms = findPreference(DEFAULT_SMS);
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01480645 begin*/
		if (getPreferenceScreen() != null && mDefaultSms != null ) {
			getPreferenceScreen().removePreference(mDefaultSms);
		}
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01480645 end*/
        mCurrentSubCount = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoCount();
        // sms preference.
        mSmsQuickTextEditorPref = findPreference(SMS_QUICK_TEXT_EDITOR);
        // google mr1 feature of group mms.
        mMmsGroupMms = (CheckBoxPreference) findPreference(GROUP_MMS_MODE);
        if (!MmsConfig.getGroupMmsEnabled()) {
            Log.d(TAG, "remove the group mms entry, it should be hidden.");
            PreferenceCategory mmOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
            mmOptions.removePreference(findPreference(GROUP_MMS_MODE));
        }
        if (mMmsGroupMms != null) {
            mMmsGroupMms.setKey(GROUP_MMS_MODE);
        }
        mMmsPriority = (ListPreference) findPreference(PRIORITY);
        mMmsPriority.setOnPreferenceChangeListener(this);
        mMmsCreationMode = (ListPreference) findPreference(CREATION_MODE);
        mMmsCreationMode.setOnPreferenceChangeListener(this);
        mMmsSizeLimit = (ListPreference) findPreference(MMS_SIZE_LIMIT);
        mMmsSizeLimit.setOnPreferenceChangeListener(this);

	/*HQ_sunli 20150918 HQ01390936 begin */
	mStorageStatus = findPreference(STORAGE_STATUS);
        mStorageStatus.setOnPreferenceChangeListener(this);
	/*HQ_sunli 20150918 HQ01390936 end */
        if (!MmsConfig.getMmsEnabled()) {
            PreferenceCategory mmsOptions = (PreferenceCategory) findPreference(MMS_SETTINGS);
            getPreferenceScreen().removePreference(mmsOptions);
        }
        // notification preference.
        mNotificaitonMute = (ListPreference) findPreference(NOTIFICATION_MUTE);
        mNotificaitonMute.setOnPreferenceChangeListener(this);
        mEnableNotificationsPref = (CheckBoxPreference) findPreference(NOTIFICATION_ENABLED);
        /*HQ_sunli 20151104 HQ01479277 begin */
        mRingtonePref = (MmsRingtonePreference) findPreference(NOTIFICATION_RINGTONE);
        mRingtonePref.setOnPreferenceChangeListener(this);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        String soundValue = sharedPreferences.getString(NOTIFICATION_RINGTONE, null);
        setRingtoneSummary(soundValue);
	/*HQ_sunli 20151104 HQ01479277 end */
        mVibratePref = (CheckBoxPreference) findPreference(NOTIFICATION_VIBRATE);
        mPopupNotificationPref = (CheckBoxPreference) findPreference(POPUP_NOTIFICATION);
        // general preference
        mShowEmailPref = (CheckBoxPreference) findPreference(SHOW_EMAIL_ADDRESS);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        if (mShowEmailPref != null) {
            mShowEmailPref.setChecked(sp.getBoolean(mShowEmailPref.getKey(), true));
        }
        mFontSize = (Preference) findPreference(FONT_SIZE_SETTING);
        mFontSizeChoices = getResourceArray(R.array.pref_message_font_size_choices);
        mFontSizeValues = getResourceArray(R.array.pref_message_font_size_values);
        mFontSize = (Preference) findPreference(FONT_SIZE_SETTING);
        mFontSize.setSummary(mFontSizeChoices[getPreferenceValueInt(FONT_SIZE_SETTING, 0)]);
        if (FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            Log.d(TAG, "remove wallpaper item");
            PreferenceCategory displaySettings =
                    (PreferenceCategory) findPreference(DISPLAY_SETTING_GENERAL);
            displaySettings.removePreference(findPreference(GENERAL_CHAT_WALLPAPER));
        } else {
            mChatWallpaperPref = findPreference(GENERAL_CHAT_WALLPAPER);
        }
        mSmsLimitPref = findPreference(SMS_DELETE_LIMIT);
        mMmsLimitPref = findPreference(MMS_DELETE_LIMIT);
        if (!MmsConfig.getMmsEnabled()) {
            PreferenceCategory storageOptions = (PreferenceCategory) findPreference(STORAGE_SETTING);
            storageOptions.removePreference(findPreference(MMS_DELETE_LIMIT));
        }
        enablePushSetting();
        mSmsRecycler = Recycler.getSmsRecycler();
        mMmsRecycler = Recycler.getMmsRecycler();
        // Fix up the recycler's summary with the correct values
        setSmsDisplayLimit();
        setMmsDisplayLimit();
        mChatWallpaperUri = sp.getString(GENERAL_CHAT_WALLPAPER, "");

       /*HQ_caohaolin  add for cl812 to add sms signature begin */
       mSignatureInfo = mActivity.getSharedPreferences(MMS_PREFERENCE, Context.MODE_WORLD_READABLE);
       mEditor = mSignatureInfo.edit();
       mEnableSignatruePref = (CheckBoxPreference)findPreference(ENABLE_SIGNATURE);
       mPersonalSignatruePref = findPreference(PERSONAL_SIGNATURE);
       mEnableSignatruePref.setChecked(mSignatureInfo.getBoolean(KEY_SIGNATURE, false));
       mPersonalSignatruePref.setEnabled(mEnableSignatruePref.isChecked());
       /*HQ_caohaolin  add for cl812 to add sms signature end */ 
    }
      /*HQ_sunli 20151104 HQ01479277 begin */
	   /**
     * Use to set DEFAULT_RINGTONE as ring tone.
     */
    private void restoreDefaultRingtone() {
        // Restore the value of ring tone in SharedPreferences
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mActivity)
                .edit();
        editor.putString(NotificationPreferenceActivity.NOTIFICATION_RINGTONE, DEFAULT_RINGTONE);
        editor.apply();
    }
    private void setRingtoneSummary(String soundValue) {
		  MmsLog.d(TAG, "setRingtoneSummary soundValue " + soundValue);
		  /// for ALPS01836799, set the ring tone as DEFAULT_RINGTONE if the ring tone not exist. @{
		  if (!TextUtils.isEmpty(soundValue) && soundValue.startsWith(EXTERNAL_RINGTONE_PATH)) {
			  boolean isRingtoneExist = RingtoneManager.isRingtoneExist(mActivity, Uri.parse(soundValue));
			  MmsLog.d(TAG, "Ring tone is exist: " + isRingtoneExist);
			  if (!isRingtoneExist) {
				  restoreDefaultRingtone();
				  soundValue = DEFAULT_RINGTONE;
			  }
		  }
		  /// @}
		  Uri soundUri = TextUtils.isEmpty(soundValue) ? null : Uri.parse(soundValue);
		  Ringtone tone = soundUri != null ? RingtoneManager.getRingtone(mActivity, soundUri) : null;
	  SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
		  boolean is_followe_notification = sharedPreferences.getBoolean(IS_FOLLOW_NOTIFICATION, true);
	  if(is_followe_notification|| (soundUri != null && soundUri.equals("content://settings/system/notification_sound"))){
		  restoreDefaultRingtone();
		  soundValue = DEFAULT_RINGTONE;
	  mRingtonePref.setSummary(getResources().getString(R.string.system_notification_tone));
		}
	  else{
		  mRingtonePref.setSummary(tone != null ? tone.getTitle(mActivity)
				  : getResources().getString(R.string.silent_ringtone));
	  }
	  }
    /*HQ_sunli	20151104 HQ01479277 end */
    /*HQ_caohaolin  add for cl812 to add sms signature begin */
    private class PositiveSignatureButtonListener implements OnClickListener{
       public void onClick(DialogInterface dialog, int which){
            mEditor.putString(KEY_SIGNATURE_INFO,mSignatureText.getText().toString());
            mEditor.commit();
       }
    }

    private class NegativeSignatureButtonListener implements OnClickListener{
       public void onClick(DialogInterface dialog, int which){
            dialog.dismiss();
       }
    }
    /*HQ_caohaolin  add for cl812 to add sms signature end */

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSmsQuickTextEditorPref) {
            Intent intent = new Intent();
            intent.setClass(mActivity, SmsTemplateEditActivity.class);
            startActivity(intent);
        } else if (preference == mSmsLimitPref) {
            mSmsDisplayLimitDialog = new NumberPickerDialog(mActivity, mSmsLimitListener, mSmsRecycler
                    .getMessageLimit(mActivity), mSmsRecycler.getMessageMinLimit(), mSmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_sms_delete);
            mSmsDisplayLimitDialog.show();
        } else if (preference == mMmsLimitPref) {
            mMmsDisplayLimitDialog = new NumberPickerDialog(mActivity, mMmsLimitListener, mMmsRecycler
                    .getMessageLimit(mActivity), mMmsRecycler.getMessageMinLimit(), mMmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_mms_delete);
            mMmsDisplayLimitDialog.show();
		}  else if (preference == mStorageStatus) { /*HQ_sunli HQ01393355 20150919 begin*/
			showStorageStatusDialog();
		/*HQ_sunli HQ01393355 20150919 end*/
        } else if (preference == mFontSize) {
            showFontDialog();
        } else if (preference == mChatWallpaperPref) {
            pickChatWallpaper();
        } else if (preference == mDefaultSms) {
            setDefaultMms();
        /*HQ_caohaolin  add for cl812 to add sms signature begin */
        } else if (preference == mEnableSignatruePref) {
            mEditor.putBoolean(KEY_SIGNATURE,mEnableSignatruePref.isChecked());
            mEditor.commit();
            mPersonalSignatruePref.setEnabled(mEnableSignatruePref.isChecked());
        } else if (preference == mPersonalSignatruePref) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(mActivity);
            String mSignatureStr;
            String defaultSignature = getResources().getString(R.string.default_signature);
            mSignatureStr = mSignatureInfo.getString(KEY_SIGNATURE_INFO,defaultSignature);
            mSignatureText = new EditText(dialog.getContext());
            mSignatureText.computeScroll();
            mSignatureText.setFilters(new  InputFilter[]{ new  InputFilter.LengthFilter(30)});      
            mSignatureText.setInputType(EditorInfo.TYPE_CLASS_TEXT );
            mSignatureText.setText(mSignatureStr);
            mSignatureTextDialog = dialog
              .setTitle(R.string.dialog_signatrue_title)
              .setView(mSignatureText)
              .setPositiveButton(R.string.OK, new PositiveSignatureButtonListener())
              .setNegativeButton(R.string.Cancel, new NegativeSignatureButtonListener())
              .show();
        /*HQ_caohaolin  add for cl812 to add sms signature begin */
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    protected void showFontDialog() {
        FontSizeDialogAdapter adapter = new FontSizeDialogAdapter(mActivity, mFontSizeChoices, mFontSizeValues);
        new AlertDialog.Builder(mActivity).setTitle(R.string.message_font_size_dialog_title).setNegativeButton(
            R.string.message_font_size_dialog_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            }).setSingleChoiceItems(adapter, getFontSizeCurrentPosition(), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mActivity).edit();
                editor.putInt(FONT_SIZE_SETTING, which);
                editor.putFloat(TEXT_SIZE, Float.parseFloat(mFontSizeValues[which]));
                editor.apply();
                dialog.dismiss();
                mFontSize.setSummary(mFontSizeChoices[which]);
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                dialog.dismiss();
            }
        }).show();
        return;
    }

    private int getFontSizeCurrentPosition() {
        SharedPreferences sp = mActivity.getSharedPreferences(MMS_PREFERENCE, Context.MODE_WORLD_READABLE);
        return sp.getInt(FONT_SIZE_SETTING, 0);
    }

    NumberPickerDialog.OnNumberSetListener mSmsLimitListener = new NumberPickerDialog.OnNumberSetListener() {
        public void onNumberSet(int limit) {
            if (limit <= mSmsRecycler.getMessageMinLimit()) {
                limit = mSmsRecycler.getMessageMinLimit();
            } else if (limit >= mSmsRecycler.getMessageMaxLimit()) {
                limit = mSmsRecycler.getMessageMaxLimit();
            }
            mSmsRecycler.setMessageLimit(mActivity, limit);
            setSmsDisplayLimit();
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(mActivity, "", getString(R.string.deleting), true);
            }
            mSMSHandler.post(new Runnable() {
                public void run() {
                    new Thread(new Runnable() {
                        public void run() {
                            Recycler.getSmsRecycler().deleteOldMessages(mActivity);
                            if (FeatureOption.MTK_WAPPUSH_SUPPORT) {
                                Recycler.getWapPushRecycler().deleteOldMessages(mActivity);
                            }
                            if (null != mProgressDialog && mProgressDialog.isShowing()) {
                                mProgressDialog.dismiss();
                            }
                        }
                    }).start();
                }
            });
        }
    };

    NumberPickerDialog.OnNumberSetListener mMmsLimitListener = new NumberPickerDialog.OnNumberSetListener() {
        public void onNumberSet(int limit) {
            if (limit <= mMmsRecycler.getMessageMinLimit()) {
                limit = mMmsRecycler.getMessageMinLimit();
            } else if (limit >= mMmsRecycler.getMessageMaxLimit()) {
                limit = mMmsRecycler.getMessageMaxLimit();
            }
            mMmsRecycler.setMessageLimit(mActivity, limit);
            setMmsDisplayLimit();
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(mActivity, "", getString(R.string.deleting), true);
            }
            mMMSHandler.post(new Runnable() {
                public void run() {
                    new Thread(new Runnable() {
                        public void run() {
                            Log.d(TAG, "mMmsLimitListener");
                            Recycler.getMmsRecycler().deleteOldMessages(mActivity);
                            if (null != mProgressDialog && mProgressDialog.isShowing()) {
                                mProgressDialog.dismiss();
                            }
                        }
                    }).start();
                }
            });
        }
    };

    private int getPreferenceValueInt(String key, int defaultValue) {
        SharedPreferences sp = mActivity.getSharedPreferences(MMS_PREFERENCE, Context.MODE_WORLD_READABLE);
        return sp.getInt(key, defaultValue);
    }

    private String[] getResourceArray(int resId) {
        return mActivity.getResources().getStringArray(resId);
    }

    protected AsyncDialog getAsyncDialog() {
        if (mAsyncDialog == null) {
            mAsyncDialog = new AsyncDialog(mActivity);
        }
        return mAsyncDialog;
    }

    public void pickChatWallpaper() {
        AlertDialog.Builder wallpaperDialog = new AlertDialog.Builder(mActivity);
        ArrayList<HashMap<String, Object>> wallpaper = new ArrayList<HashMap<String, Object>>();
        for (int i = 0; i < 4; i++) {
            HashMap<String, Object> hm = new HashMap<String, Object>();
            hm.put("ItemImage", mWallpaperImage[i]);
            hm.put("ItemText", mActivity.getResources().getString(mWallpaperText[i]));
            wallpaper.add(hm);
        }
        SimpleAdapter wallpaperDialogAdapter = new SimpleAdapter(mActivity, wallpaper, R.layout.wallpaper_item_each,
                new String[] {"ItemImage", "ItemText"}, new int[] {R.id.wallpaperitemeachimageview,
                    R.id.wallpaperitemeachtextview});
        LayoutInflater mInflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = mInflater.inflate(R.layout.wallpaper_chooser_gridview_dialog, (ViewGroup) mActivity
                .findViewById(R.id.forwallpaperchooser));
        GridView gv = (GridView) layout.findViewById(R.id.wallpaperchooserdialog);
        gv.setAdapter(wallpaperDialogAdapter);
        final AlertDialog wallpaperChooser = wallpaperDialog.setTitle(
            mActivity.getResources().getString(R.string.dialog_wallpaper_title)).setView(layout).create();
        wallpaperChooser.show();
        gv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                switch (arg2) {
                case 0:
                    Log.d(TAG, "system begin");
                    pickSysWallpaper();
                    wallpaperChooser.dismiss();
                    break;
                case 1:
                    pickWallpaperFromGallery();
                    wallpaperChooser.dismiss();
                    break;
                case 2:
                    pickWallpaperFromCam();
                    wallpaperChooser.dismiss();
                    break;
                case 3:
                    new Thread() {
                        public void run() {
                            boolean isClearAll = clearWallpaperAll(mActivity);
                            showSaveWallpaperResult(isClearAll);
                        }
                    } .start();
                    wallpaperChooser.dismiss();
                    break;
                default:
                    break;
                }
            }
        });
    }

    public boolean clearWallpaperAll(Context context) {
        ContentValues cv = new ContentValues();
        cv.put(Telephony.ThreadSettings.WALLPAPER, "");
        Uri uri = ContentUris.withAppendedId(Uri.parse(CHAT_SETTINGS_URI), 0);
        int i = context.getContentResolver().update(uri, cv, null, null);
        if (i > 0) {
            return true;
        }
        return false;
    }

    private void pickWallpaperFromCam() {
        if (getSDCardPath(mActivity) != null) {
            mWallpaperPathForCamera = getSDCardPath(mActivity) + File.separator + "Message_WallPaper" + File.separator
                + "general_wallpaper_" + System.currentTimeMillis() + PICTURE_SUFFIX;
            File out = new File(mWallpaperPathForCamera);
            if (!out.getParentFile().exists()) {
                out.getParentFile().mkdirs();
            }
            Uri mWallpaperTakeuri = Uri.fromFile(out);
            Intent imageCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            imageCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mWallpaperTakeuri);
            Log.d(TAG, "MediaStoreUri: " + mWallpaperTakeuri);
            try {
                startActivityForResult(imageCaptureIntent, PICK_PHOTO);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "pickWallpaperFromCam, ActivityNotFoundException.");
            }
        } else {
            Log.d(TAG, "SDcard not esisted ");
            Intent imageCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try {
                startActivityForResult(imageCaptureIntent, PICK_PHOTO);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "pickWallpaperFromCam, ActivityNotFoundException2.");
            }
        }
    }

    private void pickWallpaperFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(intent, "Gallery"), PICK_GALLERY);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "pickWallpaperFromGallery, ActivityNotFoundException.");
        }
    }

    public static String getSDCardPath(Context c) {
        File sdDir = null;
        String sdStatus = Environment.getExternalStorageState();
        if (sdStatus != null) {
            boolean sdCardExist = sdStatus.equals(android.os.Environment.MEDIA_MOUNTED);
            if (sdCardExist) {
                sdDir = Environment.getExternalStorageDirectory();
                return sdDir.toString();
            } else {
                return null;
            }
        }
        return null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == -1) {
            switch (requestCode) {
            case PICK_PHOTO:
                pickWallpaperFromCamResult();
                break;
            case PICK_WALLPAPER:
                pickWallpaperFromSys(data);
                Log.d(TAG, "sytem result");
                break;
            case PICK_GALLERY:
                pickWallpaperFromGalleryResult(data);
                break;
            default:
                break;
            }
            return;
        } else if (resultCode == 0) {
            Log.d(TAG, "nothing selected");
            return;
        }
    }

    private void pickWallpaperFromGalleryResult(final Intent data) {
        if (null == data) {
            return;
        }
        getAsyncDialog().runAsync(new Runnable() {
            public void run() {
                Uri mChatWallpaperGalleryUri = data.getData();
                if (mChatWallpaperGalleryUri == null) {
                    return;
                }
                Cursor c = mActivity.getContentResolver().query(mChatWallpaperGalleryUri,
                    new String[] {MediaStore.Images.Media.DATA}, null, null, null);
                if (c == null) {
                    return;
                }
                String wallpaperPathForGallery = "";
                try {
                    if (c.getCount() == 0) {
                        return;
                    } else {
                        c.moveToFirst();
                        wallpaperPathForGallery = c.getString(0);
                    }
                } finally {
                    c.close();
                }
                Log.d(TAG, "Save wallpaper Gallery Uri: " + wallpaperPathForGallery);
                String chatWallpaperCompressForGallery = compressAndRotateForMemory(wallpaperPathForGallery);
                boolean isSaveForGallery = saveWallpaperToMemory(chatWallpaperCompressForGallery);
                showSaveWallpaperResult(isSaveForGallery);
                return;
            }
        }, null, R.string.chat_setting_updating);
    }

    void showSaveWallpaperResult(boolean isShow) {
        if (isShow) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(mActivity, mActivity.getResources().getString(R.string.save_wallpaper_success),
                        Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(mActivity, mActivity.getResources().getString(R.string.save_wallpaper_fail),
                        Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void pickWallpaperFromCamResult() {
        getAsyncDialog().runAsync(new Runnable() {
            public void run() {
                String chatWallpaperCompressForCamera = compressAndRotateForMemory(mWallpaperPathForCamera);
                boolean isSaveForCamera = saveWallpaperToMemory(chatWallpaperCompressForCamera);
                showSaveWallpaperResult(isSaveForCamera);
            }
        }, null, R.string.chat_setting_updating);
    }

    private boolean saveWallpaperToMemory(String oldWallpaper) {
        if (oldWallpaper == null) {
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put(Telephony.ThreadSettings.WALLPAPER, GENERAL_WALLPAPER_FOR_PROVIDER);
        boolean isSaveSuccess = false;
        Uri uri = ContentUris.withAppendedId(Uri.parse(CHAT_SETTINGS_URI), 0);
        mActivity.getContentResolver().update(uri, cv, null, null);
        OutputStream o = null;
        Bitmap bm = null;
        try {
            o = mActivity.getContentResolver().openOutputStream(uri);
            bm = BitmapFactory.decodeFile(oldWallpaper);
            if (bm != null) {
                isSaveSuccess = bm.compress(Bitmap.CompressFormat.JPEG, 100, o);
            }
            Log.d(TAG, "decodeFile over");
            File tempFile = new File(oldWallpaper);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException", e);
        //} catch (IOException e) {
        //    Log.d(TAG, "IOException", e);
        } finally {
            if (o != null) {
                try {
                    o.close();
                } catch (IOException e) {
                    Log.d(TAG, "o.close() exception");
                }
            }
            if (bm != null) {
                bm.recycle();
            }
            return isSaveSuccess;
        }
    }

    private String compressAndRotateForMemory(String wallpaperCache) {
        // if wallpaperCache is null, do nothing.
        if (wallpaperCache == null) {
            return null;
        }
        File mChatWallpaperPStore = new File(wallpaperCache);
        String chatWallpaperUri = mChatWallpaperUri;
        if (mChatWallpaperPStore.exists()) {
            File mChatWallpaperMemory = new File(mChatWallpaperPStore.getParent(), "general_wallpaper_"
                + System.currentTimeMillis() + PICTURE_SUFFIX);
            try {
                mChatWallpaperMemory.createNewFile();
            } catch (IOException e) {
            }
            Log.d(TAG, "mChatWallpapterMemory " + mChatWallpaperMemory.getName());
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(mChatWallpaperMemory);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "compressAndRotateForMemory, FileNotFoundException");
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(wallpaperCache, options);
            options.inJustDecodeBounds = false;
            int wallpaperHeight = options.outHeight;
            int wallpaperWidth = options.outWidth;
            Log.d(TAG, "wallpaperHeight = " + wallpaperHeight + " wallpaperWidth = " + wallpaperWidth);
            int ratio = MessageUtils.calculateWallpaperSize(mActivity, wallpaperHeight, wallpaperWidth);
            Log.d(TAG, "ratio: " + ratio);
            options.inSampleSize = ratio;
            int orientation = 0;
            int degree = 0;
            boolean isCopyed = false;
            try {
                ExifInterface exif = new ExifInterface(wallpaperCache);
                if (exif != null) {
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                    degree = UriImage.getExifRotation(orientation);
                }
            } catch (IOException e) {
                Log.d(TAG, "compressAndRotateForMemory, FileNotFoundException1");
            }
            Bitmap bm = BitmapFactory.decodeFile(wallpaperCache, options);
            if (bm != null) {
                bm = UriImage.rotate(bm, degree);
                isCopyed = bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            }
            Log.d(TAG, "isCopyed: " + isCopyed);
            try {
                if (!isCopyed) {
                    return null;
                }
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.d(TAG, "fos.close() exception");
                    }
                }
                if (bm != null) {
                    bm.recycle();
                }
            }
            if (!isCopyed) {
                chatWallpaperUri = mChatWallpaperPStore.getPath();
                return chatWallpaperUri;
            }
            chatWallpaperUri = mChatWallpaperMemory.getPath();
        }
        return chatWallpaperUri;
    }

    private void pickSysWallpaper() {
        Intent intent = new Intent(mActivity, WallpaperChooser.class);
        startActivityForResult(intent, PICK_WALLPAPER);
    }

    private void pickWallpaperFromSys(Intent data) {
        final int sourceId = data.getIntExtra("wallpaper_index", -1);
        Log.d(TAG, "sourceId: " + sourceId);
        getAsyncDialog().runAsync(new Runnable() {
            public void run() {
                boolean isSaveForSystem = saveResourceWallpaperToMemory(sourceId);
                showSaveWallpaperResult(isSaveForSystem);
            }
        }, null, R.string.chat_setting_updating);
    }

    private boolean saveResourceWallpaperToMemory(int resourceId) {
        Resources r = mActivity.getResources();
        InputStream is = null;
        try {
            is = r.openRawResource(resourceId);
        } catch (NotFoundException e) {
            Log.d(TAG, "NotFoundException", e);
        }
        Bitmap bm = BitmapFactory.decodeStream(is);
        ContentValues cv = new ContentValues();
        cv.put(Telephony.ThreadSettings.WALLPAPER, GENERAL_WALLPAPER_FOR_PROVIDER);
        boolean isSaveSuccessed = false;
        Uri uri = ContentUris.withAppendedId(Uri.parse(CHAT_SETTINGS_URI), 0);
        mActivity.getContentResolver().update(uri, cv, null, null);
        OutputStream o = null;
        try {
            o = mActivity.getContentResolver().openOutputStream(uri);
            if (bm != null) {
                isSaveSuccessed = bm.compress(Bitmap.CompressFormat.JPEG, 100, o);
            }
            Log.d(TAG, "decodeFile over");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException", e);
        //} catch (IOException e) {
        //    Log.d(TAG, "IOException", e);
        } finally {
            if (o != null) {
                try {
                    o.close();
                } catch (IOException e) {
                    Log.d(TAG, "fos.close() exception");
                }
            }
            if (bm != null) {
                bm.recycle();
            }
            return isSaveSuccessed;
        }
    }

    private void setDefaultMms() {
        Log.d(TAG, "setDefaultMms mIsSmsEnabled: " + mIsSmsEnabled);
        Intent intent = new Intent();
        if (mIsSmsEnabled) {
            intent.setAction("android.settings.WIRELESS_SETTINGS");
            intent.setPackage("com.android.settings");
        } else {
            intent.setAction("android.provider.Telephony.ACTION_CHANGE_DEFAULT");
            intent.setPackage("com.android.settings");
            intent.putExtra("package", "com.android.mms");
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.d(TAG, "No Respond Activity to receive intent for default sms");
        }
    }

    private void setDefaultSmsValue() {
        if (mIsSmsEnabled) {
            mDefaultSms.setTitle(mActivity.getString(R.string.pref_title_sms_enabled));
            mDefaultSms.setSummary(mActivity.getString(R.string.pref_summary_sms_enabled));
        } else {
            mDefaultSms.setTitle(mActivity.getString(R.string.pref_title_sms_disabled));
            mDefaultSms.setSummary(mActivity.getString(R.string.pref_summary_sms_disabled));
        }
    }

    public void setCategoryDisable() {
        PreferenceCategory smsSettings = (PreferenceCategory) findPreference(SMS_SETTING_GENERAL);
        Log.d(TAG, "setCategoryDisable");
        if (smsSettings != null) {
            smsSettings.setEnabled(false);
        }
        PreferenceCategory mmsSettings = (PreferenceCategory) findPreference(MMS_SETTING_GENERAL);
        if (mmsSettings != null) {
            mmsSettings.setEnabled(false);
        }
        PreferenceCategory notificationSettins = (PreferenceCategory) findPreference(NOTIFICATION_SETTING_GENERAL);
        if (notificationSettins != null) {
            notificationSettins.setEnabled(false);
        }
        PreferenceCategory displaySettings = (PreferenceCategory) findPreference(DISPLAY_SETTING_GENERAL);
        if (displaySettings != null) {
            displaySettings.setEnabled(false);
        }
        PreferenceCategory storageSettings = (PreferenceCategory) findPreference(STORAGE_SETTING_GENERAL);
        if (storageSettings != null) {
            storageSettings.setEnabled(false);
        }
        PreferenceCategory wappushSettings = (PreferenceCategory) findPreference(WAPPUSH_SETTING_GENERAL);
        if (wappushSettings != null) {
            wappushSettings.setEnabled(false);
        }
    }

    public void setCategoryEnable() {
        PreferenceCategory smsSettings = (PreferenceCategory) findPreference(SMS_SETTING_GENERAL);
        if (smsSettings != null) {
            smsSettings.setEnabled(true);
        }
        PreferenceCategory mmsSettings = (PreferenceCategory) findPreference(MMS_SETTING_GENERAL);
        if (mmsSettings != null) {
            mmsSettings.setEnabled(true);
        }
        PreferenceCategory notificationSettins = (PreferenceCategory) findPreference(NOTIFICATION_SETTING_GENERAL);
        if (notificationSettins != null) {
            notificationSettins.setEnabled(true);
        }
        PreferenceCategory displaySettings = (PreferenceCategory) findPreference(DISPLAY_SETTING_GENERAL);
        if (displaySettings != null) {
            displaySettings.setEnabled(true);
        }
        PreferenceCategory storageSettings = (PreferenceCategory) findPreference(STORAGE_SETTING_GENERAL);
        if (storageSettings != null) {
            storageSettings.setEnabled(true);
        }
        PreferenceCategory wappushSettings = (PreferenceCategory) findPreference(WAPPUSH_SETTING_GENERAL);
        if (wappushSettings != null) {
            wappushSettings.setEnabled(true);
        }
    }

	/*HQ_sunli HQ01393355 20150919 begin*/
	public void showStorageStatusDialog() {
		 final String memoryStatus = MessageUtils.getStorageStatus( mActivity );
		 String status = mActivity.getResources().getString(R.string.pref_title_storage_status);
		 Drawable icon = mActivity.getResources().getDrawable(R.drawable.ic_dialog_info_holo_light);
		 new AlertDialog.Builder(mActivity)
		 .setTitle(status)
		 .setIcon(icon)
		 .setMessage(memoryStatus)
		 .setPositiveButton(android.R.string.ok, null)
		 .setCancelable(true)
		 .show();
	}
	/*HQ_sunli HQ01393355 20150919 end*/
}
