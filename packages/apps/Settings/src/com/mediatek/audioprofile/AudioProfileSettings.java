/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
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
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.audioprofile;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;

import com.mediatek.audioprofile.AudioProfileManager.Scenario;
import com.mediatek.common.audioprofile.AudioProfileListener;
import com.mediatek.settings.ext.IAudioProfileExt;
import com.mediatek.settings.UtilsExt;
import com.mediatek.xlog.Xlog;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.List;

import android.os.SystemProperties;

public class AudioProfileSettings extends SettingsPreferenceFragment implements
        DialogInterface.OnClickListener, Indexable{

    private static final String XLOGTAG = "Settings/AudioP";
    private static final String TAG = "AudioProfileSettings:";

    private static final String GENERAL_PREF_KEY = "mtk_audioprofile_general";
    private static final String SILENT_PREF_KEY = "mtk_audioprofile_silent";
    private static final String MEETING_PREF_KEY = "mtk_audioprofile_meeting";
    private static final String OUTDOOR_PREF_KEY = "mtk_audioprofile_outdoor";

    private static final int MENUID_ADD = Menu.FIRST;
    private static final int MENUID_RESET = Menu.FIRST + 1;

    private static final int MENUID_ENABLE = 2;
    private static final int MENUID_RENAME = 3;
    private static final int MENUID_DELETE = 4;

    private static final int DIALOG_NAME = 0;
    private static final int DIALOG_ERROR = 1;
    private static final int DIALOG_RESET = 2;
    private static final int DIALOG_DELETE = 3;

    private static final int ERROR_NAME_EXIST = 0;
    private static final int ERROR_NAME_LENGTH = 1;
    private static final int ERROR_COUNT_OVERFLOW = 2;

    private static final int H_RESET_SUCCESS = 11;

    private static final String PREDEFINEDCATEGORY = "predefine";

    private static int mMenuId;
    public int mCurrentDialogId = -1;
    private static int mErrorType;
    private Handler mHandler = null;

    private Context mContext;
    private AudioProfileManager mProfileManager;
    //private PreferenceCategory mCustomParent;
    private boolean mCustomerExist = true;
    private PreferenceCategory mPredefineParent;
    private static AudioProfilePreference mPref;
    private static String mEditProfileKey;
    private String[] mProfileTitle;
    private String mDefaultKey;
    private EditText mEditText = null;
    private String mRenameDialogtext;

    // the customer preference list that should update the summary
    private List<AudioProfilePreference> mCustomerProfilePrefList;
    private AudioProfilePreference mGeneralPref;

    private boolean mIsSwitchUser = false;

    private IAudioProfileExt mExt;
    private final AudioProfileListener mListener = new AudioProfileListener() {
        @Override
        public void onProfileChanged(String profileKey) {
            super.onProfileChanged(profileKey);
            Xlog.d(XLOGTAG, TAG + "onPerfileChanged:key " + profileKey);
            AudioProfilePreference activePreference =
                (AudioProfilePreference) findPreference(profileKey == null ? mDefaultKey
                    : profileKey);
            if (activePreference != null) {
                activePreference.setChecked();
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                Xlog.d(XLOGTAG, "User switched");
                mIsSwitchUser = true;
            }
        }
    };

    /**
     * called to do the initial creation of a fragment
     *
     * @param icicle
     */
    public void onCreate(Bundle icicle) {
        Xlog.d(XLOGTAG, "onCreate");
        super.onCreate(icicle);
        mContext = getActivity();

        mExt = UtilsExt.getAudioProfilePlgin(getActivity());

        mCustomerProfilePrefList = new ArrayList<AudioProfilePreference>();
        mProfileManager = (AudioProfileManager) getSystemService(Context.AUDIO_PROFILE_SERVICE);

        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.audioprofile_settings);
        /// M: Add customized prefernce in plug-in
        mExt.addCustomizedPreference(getPreferenceScreen());
        mPredefineParent = (PreferenceCategory) findPreference(PREDEFINEDCATEGORY);

        AudioProfilePreference pref = (AudioProfilePreference) findPreference(GENERAL_PREF_KEY);
        pref.setOnSettingsClickListener(mProfileSettingListener);
        pref = (AudioProfilePreference) findPreference(SILENT_PREF_KEY);
        pref.setOnSettingsClickListener(mProfileSettingListener);
        pref = (AudioProfilePreference) findPreference(MEETING_PREF_KEY);
        pref.setOnSettingsClickListener(mProfileSettingListener);
        pref = (AudioProfilePreference) findPreference(OUTDOOR_PREF_KEY);
        pref.setOnSettingsClickListener(mProfileSettingListener);

        mDefaultKey = AudioProfileManager.PROFILE_PREFIX
                + Scenario.GENERAL.toString().toLowerCase();
        mGeneralPref = (AudioProfilePreference) findPreference(mDefaultKey);

        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == H_RESET_SUCCESS) {
                    mPredefineParent.setEnabled(true);
                    mGeneralPref.dynamicShowSummary();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        setHasOptionsMenu(true);
    }

    /**
     * Update the AudioProfile main UI, including get all the profile from
     * framework
     */
    private void updatePreferenceHierarchy() {

        PreferenceScreen root = getPreferenceScreen();

        List<String> profileKeys = mProfileManager.getAllProfileKeys();
        if (profileKeys == null) {
            Xlog.d(XLOGTAG, TAG + "profileKey size is 0");
            return;
        }
        Xlog.d(XLOGTAG, TAG + "profileKey size" + profileKeys.size());

        if (profileKeys.size() == AudioProfileManager.PREDEFINED_PROFILES_COUNT) {
            for (int i = 0; i < mCustomerProfilePrefList.size(); i++) {
                addPreference(root, mCustomerProfilePrefList.get(i).getKey());
            }
            mCustomerExist = false;
        } else {
            for (int i = 0; i < mCustomerProfilePrefList.size(); i++) {
                mPredefineParent.removePreference(mCustomerProfilePrefList.get(i));
            }
            mCustomerProfilePrefList.clear();
            for (String profileKey : profileKeys) {
                addPreference(root, profileKey);
            }
        }
    }

    /**
     * Add a preference to the fragment
     *
     * @param root
     *            the place which the preference will be added to
     * @param key
     *            the key of the preference(also the profile) which will be
     *            added
     * @return the added preference
     */
    private AudioProfilePreference addPreference(PreferenceScreen root,
            String key) {
        Scenario scenario = AudioProfileManager.getScenario(key);
        AudioProfilePreference preference = null;
        if (Scenario.CUSTOM.equals(scenario)) {
            preference = new AudioProfilePreference(getActivity());
            preference.setOnSettingsClickListener(mProfileSettingListener);
            preference.setProfileKey(key);
            mCustomerProfilePrefList.add(preference);
            Xlog.d(XLOGTAG, TAG + "Add into profile list " + preference.getKey());

            if (preference.getKey().equals(mEditProfileKey) && mPref != null)
            {
                 Xlog.d(XLOGTAG, TAG + "resume mPref: rename profile, key = " + mEditProfileKey);
                 mPref = preference;
            }

            if (!mCustomerExist) {
                mCustomerExist = true;
            }
            mPredefineParent.addPreference(preference);
            Xlog.d(XLOGTAG, TAG + "Add into profile preference " + preference.getKey());

            String name = mProfileManager.getProfileName(key);

            if (name != null) {
                preference.setTitle(name, false);
                Xlog.d(XLOGTAG, TAG + String.valueOf(preference.getTitle()));
            }
        }
        return preference;
    }

    private final View.OnClickListener mProfileSettingListener = new View.OnClickListener() {
        public void onClick(View v) {
            String key = (String) (v.getTag());
            Xlog.d(XLOGTAG, "on Click ImageView: " + key);
            Bundle args = new Bundle();
            args.putString("profileKey", key);
            ((SettingsActivity) getActivity()).startPreferencePanel(
                    Editprofile.class.getName(), args, 0, null, null, 0);
        }
    };

    /**
     * Get the active profile from framework and update UI
     */
    private void updateActivePreference() {
        String key = mProfileManager.getActiveProfileKey();
        Xlog.d(XLOGTAG, TAG + "key " + key);
        AudioProfilePreference activePreference = (AudioProfilePreference) findPreference(key == null ? mDefaultKey
                : key);
        if (activePreference != null) {
            activePreference.setChecked();
        }
    }

    /**
     * Update the general and customer profile preference summary
     */
    private void dynamicshowSummary() {

        mGeneralPref.dynamicShowSummary();
        for (AudioProfilePreference pref : mCustomerProfilePrefList) {
            pref.dynamicShowSummary();
        }
    }

    /**
     * called when the fragment is visible to the user Need to update summary
     * and active profile, register for the profile change
     */
    public void onResume() {
        Xlog.d(XLOGTAG, TAG + "onResume");
        super.onResume();
        if (dismissFrag()) {
            return;
        }

        updatePreferenceHierarchy();

        dynamicshowSummary();
        updateActivePreference();

        registerForContextMenu(getListView());
        mProfileManager.listenAudioProfie(mListener,
                AudioProfileListener.LISTEN_PROFILE_CHANGE);
        /// M: Give plug-in a chance to resume and update UI
        mExt.onAudioProfileSettingResumed(this);
    }

    /**
     * called when the fragment is unvisible to the user unregister the profile
     * change listener
     */
    public void onPause() {
        super.onPause();
        mProfileManager.listenAudioProfie(mListener,
                AudioProfileListener.STOP_LISTEN);
        /// M: Notify plug-in that Fragment is paused
        mExt.onAudioProfileSettingPaused(this);
    }

    /**
     * Click the preference and enter into the EditProfile
     *
     * @param preferenceScreen
     * @param preference
     *            the clicked preference
     * @return set success or fail
     */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference instanceof AudioProfilePreference) {
            AudioProfilePreference pref = (AudioProfilePreference) preference;
            String key = pref.getKey();
            if (mExt.isPrefEditable()) {
                Bundle args = new Bundle();
                args.putString("profileKey", key);
                ((SettingsActivity) getActivity()).startPreferencePanel(
                        Editprofile.class.getName(), args, 0, null, null, 0);
                //startFragment(this, Editprofile.class.getName(), R.string.usb_sharing_title, 0, args);
            } else {
                mProfileManager.setActiveProfile(key);
                pref.setChecked();
            }
        }

        if (mExt.onPreferenceTreeClick(preferenceScreen, preference)) {
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    /**
     * Create option menu, add a profile or reset
     *
     * @param menu
     *            include "add" and "reset"
     * @param inflater
     *            which the menu inflate
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(Menu.NONE, MENUID_ADD, 0, R.string.audio_profile_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENUID_RESET, 0, R.string.audio_profile_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    /**
     * Called when the option menu selected
     *
     * @param item
     *            the selected option menu item
     * @return true , selected success
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENUID_ADD) {
            if (mProfileManager.getProfileCount() >= AudioProfileManager.MAX_PROFILES_COUNT) {
                mErrorType = ERROR_COUNT_OVERFLOW;
                showDialog(DIALOG_ERROR);
                return true;
            }
            mMenuId = MENUID_ADD;
            showDialog(DIALOG_NAME);
            return true;
        } else if (item.getItemId() == MENUID_RESET) {
            showDialog(DIALOG_RESET);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create context menu, set title icon and menu message
     *
     * @param menu
     *            the menu which will be added
     * @param view
     *            should be poppulated
     * @param menuInfo
     *            the menu info
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Preference pref = (Preference) getPreferenceScreen().getRootAdapter()
                .getItem(info.position);
        if (pref instanceof AudioProfilePreference) {
            String key = pref.getKey();
            mRenameDialogtext = ((AudioProfilePreference) pref).getTitle();
            menu.setHeaderTitle(mRenameDialogtext);
            menu.add(Menu.NONE, MENUID_ENABLE, 0, R.string.audio_profile_enable);
            Scenario senario = mProfileManager.getScenario(key);
            if (Scenario.CUSTOM.equals(senario)) {
                menu.add(Menu.NONE, MENUID_RENAME, 0,
                        R.string.audio_profile_rename);
                menu.add(Menu.NONE, MENUID_DELETE, 0,
                        R.string.audio_profile_delete);
            }
        }
    }

    /**
     * For General profile , long click to enable it For customer profile , log
     * click to enable/rename/delete it
     *
     * @param item
     *            the item which the user select
     * @return true, select success
     */
    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
        Preference pref = (Preference) getPreferenceScreen().getRootAdapter()
                .getItem(info.position);
        if (pref instanceof AudioProfilePreference) {
            mPref = (AudioProfilePreference) pref;
            mEditProfileKey = mPref.getKey();
            Xlog.d(XLOGTAG, "onContextItemSelected  mPref = " + mPref.toString() + " mEditProfileKey = " + mEditProfileKey);
            switch (item.getItemId()) {
            case MENUID_DELETE:
                showDialog(DIALOG_DELETE);
                return true;
            case MENUID_RENAME:
                mMenuId = MENUID_RENAME;
                showDialog(DIALOG_NAME);
                return true;
            case MENUID_ENABLE:
                mProfileManager.setActiveProfile(mPref.getKey());
                mPref.setChecked();
                return true;
            default:
                return false;
            }
        } else {
            return false;
        }

    }

    /**
     * Called when creating the dialog, including the adding profile dialog
     * rename dialog , error dialog, reset confirm dialog and delete profile
     * dialog.
     *
     * @param id
     *            the dialog id which need to be shown
     * @return created dialog object
     */
    @Override
    public Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        mCurrentDialogId = id;
        if (id == DIALOG_NAME) {
            View content = getActivity().getLayoutInflater().inflate(
                    R.layout.dialog_edittext, null);
            mEditText = (EditText) content.findViewById(R.id.edittext);
            if (mEditText != null) {
                mEditText.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
                mEditText.setText(mMenuId == MENUID_ADD ? ""
                        : mRenameDialogtext);
				/* HQ_liukai3 2015-8-10 modified for al812 the default cursor displayed on top */
				mEditText.setSelection(mEditText.getText().length());
            }
            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(
                            mMenuId == MENUID_ADD ? R.string.audio_profile_add
                                    : R.string.audio_profile_rename)
                    .setMessage(R.string.audio_profile_message_rename)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok, this)
                    .setNegativeButton(android.R.string.cancel, null).create();
            dialog.getWindow()
                    .setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else if (id == DIALOG_ERROR) {
            int stringId = 0;
            switch (mErrorType) {
            case ERROR_COUNT_OVERFLOW:
                stringId = R.string.audio_profile_message_overflow;
                break;
            case ERROR_NAME_EXIST:
                stringId = R.string.audio_profile_message_name_error;
                break;
            case ERROR_NAME_LENGTH:
                stringId = R.string.audio_profile_message_name_length_wrong;
                break;
            default:
                break;
            }
            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.audio_profile_error)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(stringId)
                    .setPositiveButton(android.R.string.ok, this).create();
        } else if (id == DIALOG_RESET) {
            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.audio_profile_reset)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.audio_profile_message_reset)
                    .setPositiveButton(android.R.string.ok, this)
                    .setNegativeButton(android.R.string.cancel, null).create();
        } else if (id == DIALOG_DELETE) {
            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.audio_profile_delete)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(
                            getString(R.string.audio_profile_message_delete,
                                    mPref.getTitle()))
                    .setPositiveButton(android.R.string.ok, this)
                    .setNegativeButton(android.R.string.cancel, null).create();
        }
        return dialog;
    }

    /**
     * Called when click the dialog positive and negative button
     *
     * @param dialogInterface
     *            the clicked dialog interface
     * @param button
     *            the positive or negative button
     */
    public void onClick(DialogInterface dialogInterface, int button) {
        Xlog.d(XLOGTAG, "onClick");
        Xlog.d(XLOGTAG, "" + button);
        if (button != DialogInterface.BUTTON_POSITIVE) {
            Xlog.d(XLOGTAG, "return");
            return;
        }
        switch (mCurrentDialogId) {
        case DIALOG_NAME:
            String title = mEditText == null ? "" : String.valueOf(mEditText
                    .getText());
            if (title.length() == 0) {
                mErrorType = ERROR_NAME_LENGTH;
                showDialog(DIALOG_ERROR);
            } else if (mProfileManager.isNameExist(title)
                /*HQ_yuankangbo 2015-07-28 modify for new custom scenario's name same as predefined scenario's name start*/
                    || title.equals(getString(R.string.general_settings_title))
                    || title.equals(getString(R.string.silent_settings_title))
                    || title.equals(getString(R.string.meeting_settings_title))
                    || title.equals(getString(R.string.outdoor_settings_title))) {
                /*HQ_yuankangbo 2015-07-28 modify for new custom scenario's name same as predefined scenario's name end*/
                mErrorType = ERROR_NAME_EXIST;
                showDialog(DIALOG_ERROR);
            } else {
                if (mMenuId == MENUID_ADD) {
                    String profileKey = mProfileManager.addProfile();
                    Xlog.d(XLOGTAG, TAG + "add profile Key" + profileKey);
                    mProfileManager.setProfileName(profileKey, title);
                    AudioProfilePreference activePreference = addPreference(
                            getPreferenceScreen(), profileKey);

                    if (activePreference == null) {
                        mProfileManager.setActiveProfile(mDefaultKey);
                        mGeneralPref.setChecked();
                    } else {
                        mProfileManager.setActiveProfile(profileKey);
                        activePreference.setChecked();
                        activePreference.dynamicShowSummary();
                    }
                } else {
                    Xlog.d(XLOGTAG, "onClick  mPref.setTitle = " + title);
                    mPref.setTitle(title, true);
                }
            }
            break;
        case DIALOG_ERROR:
            if (mErrorType != ERROR_COUNT_OVERFLOW) {
                showDialog(DIALOG_NAME);
            }
            break;
        case DIALOG_DELETE:
            if (mPref.isChecked()) {
                mProfileManager.setActiveProfile(mDefaultKey);
                mGeneralPref.setChecked();
            }
            mProfileManager.deleteProfile(mPref.getKey());
            mPredefineParent.removePreference(mPref);
            mCustomerProfilePrefList.remove(mPref);
            if (mCustomerProfilePrefList.size() == 0) {
                //getPreferenceScreen().removePreference(mCustomParent);
                mCustomerExist = false;
            }
            break;
        case DIALOG_RESET:
            if (mCustomerProfilePrefList != null && mCustomerProfilePrefList.size() != 0) {
                for (int i = 0; i < mCustomerProfilePrefList.size(); i++) {
                    mPredefineParent.removePreference(mCustomerProfilePrefList.get(i));
                }
                mCustomerProfilePrefList.clear();
                mCustomerExist = false;
            }
            mPredefineParent.setEnabled(false);
            new ResetTask().execute();
            break;
        default:
            Xlog.d(XLOGTAG, TAG + "unrecongnized dialog id is"
                    + mCurrentDialogId);
            break;
        }
    }

    private boolean dismissFrag() {
        Xlog.d(XLOGTAG, "Dismiss fragment.");
        if (mIsSwitchUser == true) {
            mIsSwitchUser = false;
            Xlog.d(XLOGTAG, "AudioProfileSettings will be finished.");
            finish();
            return true;
        }
        return false;
    }

    private class ResetTask extends AsyncTask<String, Void, Integer> {
        private static final int RESET_SUCCESS = 0;
        private static final int RESET_ONGOING = 1;

        /**
         * call frmework to reset the profile
         *
         * @param arg
         * @return the reset result
         */
        @Override
        protected Integer doInBackground(String... arg) {
            int result = RESET_ONGOING;
            mProfileManager.resetProfiles();
            result = RESET_SUCCESS;
            return result;
        }

        /**
         * When the result is the "RESET_SUCCESS", enable the preference
         *
         * @param result
         *            the reset result passed from doInBackground
         */
        @Override
        protected void onPostExecute(Integer result) {
            if (result == RESET_SUCCESS) {
                mHandler.sendEmptyMessage(H_RESET_SUCCESS);
            }
        }
    }
    
    // === Indexing ===
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
    new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();
            final Resources res = context.getResources();

            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.notification_settings);
            data.screenTitle = res.getString(R.string.notification_settings);
            data.keywords = res.getString(R.string.notification_settings);
            result.add(data);
 
            return result;
        }
    };
}
