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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.IBackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;

import java.util.ArrayList;
import java.util.List;
import android.content.ComponentName;//wuhuihui add for huawei backup
import android.widget.Toast;
import android.content.pm.ResolveInfo;

import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.xlog.Xlog;

import android.os.BatteryManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.widget.Toast;
import android.util.Log;

import android.os.SystemProperties;
/**
 * Gesture lock pattern settings.
 */
public class PrivacySettings extends SettingsPreferenceFragment implements
        DialogInterface.OnClickListener, Indexable {

    // Vendor specific
    private static final String GSETTINGS_PROVIDER = "com.google.settings";
    private static final String BACKUP_CATEGORY = "backup_category";
    private static final String BACKUP_DATA = "backup_data";
    private static final String AUTO_RESTORE = "auto_restore";
    ///M: Add for DRM settings
    private static final String DRM_RESET = "drm_settings";

    private static final String CONFIGURE_ACCOUNT = "configure_account";
    private static final String BACKUP_INACTIVE = "backup_inactive";
    private static final String PERSONAL_DATA_CATEGORY = "personal_data_category";
    private static final String TAG = "PrivacySettings";
    private IBackupManager mBackupManager;
    private SwitchPreference mBackup;
    private SwitchPreference mAutoRestore;
    private Dialog mConfirmDialog;
    private PreferenceScreen mConfigure;
    private boolean mEnabled;

    private static final int DIALOG_ERASE_BACKUP = 2;
    private int mDialogType;

    ///M: change backup reset title
    private ISettingsMiscExt mExt;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Don't allow any access if this is a secondary user
        mEnabled = Process.myUserHandle().isOwner();
        if (!mEnabled) {
            return;
        }

        addPreferencesFromResource(R.xml.privacy_settings);
        final PreferenceScreen screen = getPreferenceScreen();
        mBackupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));

        mBackup = (SwitchPreference) screen.findPreference(BACKUP_DATA);
        mBackup.setOnPreferenceChangeListener(preferenceChangeListener);

		String productName = SystemProperties.get("ro.product.name", "");

		/*HQ_liugang add for HQ01386751*/
		if (productName.equalsIgnoreCase("TAG-TL00") ||
			productName.equalsIgnoreCase("TAG-AL00") ||
			productName.equalsIgnoreCase("TAG-CL00")){
			mBackup.setEnabled(false);
		}
		/*HQ_liugang add end*/
		
        mAutoRestore = (SwitchPreference) screen.findPreference(AUTO_RESTORE);
        mAutoRestore.setOnPreferenceChangeListener(preferenceChangeListener);

        mConfigure = (PreferenceScreen) screen.findPreference(CONFIGURE_ACCOUNT);
        ///M: change backup reset title @{
        mExt = UtilsExt.getMiscPlugin(getActivity());
        //mExt.setFactoryResetTitle(getActivity()); //HQ_jiazaizheng 20150721 modify for HQ01266898
        /// @}

        ArrayList<String> keysToRemove = getNonVisibleKeys(getActivity());
        final int screenPreferenceCount = screen.getPreferenceCount();
        for (int i = screenPreferenceCount - 1; i >= 0; --i) {
            Preference preference = screen.getPreference(i);
            if (keysToRemove.contains(preference.getKey())) {
                screen.removePreference(preference);
            }
        }
        PreferenceCategory backupCategory = (PreferenceCategory) findPreference(BACKUP_CATEGORY);
        if (backupCategory != null) {
            final int backupCategoryPreferenceCount = backupCategory.getPreferenceCount();
            for (int i = backupCategoryPreferenceCount - 1; i >= 0; --i) {
                Preference preference = backupCategory.getPreference(i);
                if (keysToRemove.contains(preference.getKey())) {
                    backupCategory.removePreference(preference);
                }
            }
        }
        //HQ_wuhuihui_20150811 add for huawei all backup start
        PreferenceCategory allBackupCategory = (PreferenceCategory) findPreference("hw_backup_category");
        Intent intent = new Intent();
        ComponentName cn = new ComponentName("com.huawei.KoBackup",
                                              "com.huawei.KoBackup.InitializeActivity");
        intent.setAction(Intent.ACTION_MAIN);
               intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(cn);
        List<ResolveInfo> apps = getPackageManager()
                            .queryIntentActivities(intent, 0);
        if (apps == null || apps.size() == 0) {
                       Log.d(TAG,"huawei all backup app is not installed!");
                       screen.removePreference(allBackupCategory);
         }

        //HQ_wuhuihui_20150811 add for huawei all backup end
        updateToggles();
        ///M: Check Drm compile option for DRM settings @{
        //HQ_jiazaizheng 20150721 modify for HQ01249293 start
        //if (!FeatureOption.MTK_DRM_APP) {
            PreferenceCategory perDataCategory =
                       (PreferenceCategory) findPreference(PERSONAL_DATA_CATEGORY);
            if (perDataCategory != null) {
                perDataCategory.removePreference(findPreference(DRM_RESET));
            }
        //}
        //HQ_jiazaizheng 20150721 modify for HQ01249293 end
        ///@}
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh UI
        if (mEnabled) {
            updateToggles();
        }
    }

    @Override
    public void onStop() {
        if (mConfirmDialog != null && mConfirmDialog.isShowing()) {
            mConfirmDialog.dismiss();
        }
        mConfirmDialog = null;
        mDialogType = 0;
        super.onStop();
    }


    //HQ_wuhuihui_20150707 add for huawei cloud backup start
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            Log.d(TAG, "onPreferenceTreeClick() + key:" + preference.getKey());
			String prefKey = preference.getKey();
            if ("all_backup".equals(prefKey)) {
                Log.d(TAG, "start huawei backup app");
                Intent intent = new Intent();
                ComponentName cn = new ComponentName("com.huawei.KoBackup",
                                                     "com.huawei.KoBackup.InitializeActivity");
                intent.setAction(Intent.ACTION_MAIN);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(cn);
                List<ResolveInfo> apps = getPackageManager()
                            .queryIntentActivities(intent, 0);
                if (apps == null || apps.size() == 0) {
                    Toast.makeText(getActivity(), "Huawei cloud backup application is not installed",
                    Toast.LENGTH_SHORT).show();
                } else {
                    getActivity().startActivity(intent);
                }
                return true;

            }else {
                return super.onPreferenceTreeClick(preferenceScreen,preference);
            }
    }
   //HQ_wuhuihui_20150707 add for huawei cloud backup end
    private OnPreferenceChangeListener preferenceChangeListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (!(preference instanceof SwitchPreference)) {
                return true;
            }
            boolean nextValue = (Boolean) newValue;
            boolean result = false;
            if (preference == mBackup) {
                if (nextValue == false) {
                    // Don't change Switch status until user makes choice in dialog
                    // so return false here.
                    showEraseBackupDialog();
                } else {
                    setBackupEnabled(true);
                    result = true;
                }
            } else if (preference == mAutoRestore) {
                try {
                    mBackupManager.setAutoRestore(nextValue);
                    result = true;
                } catch (RemoteException e) {
                    mAutoRestore.setChecked(!nextValue);
                }
            }
            return result;
        }
    };

    private void showEraseBackupDialog() {
        mDialogType = DIALOG_ERASE_BACKUP;
        CharSequence msg = getResources().getText(R.string.backup_erase_dialog_message);
        // TODO: DialogFragment?
        mConfirmDialog = new AlertDialog.Builder(getActivity()).setMessage(msg)
                .setTitle(R.string.backup_erase_dialog_title)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .show();
    }

    /*
     * Creates toggles for each available location provider
     */
    private void updateToggles() {
        ContentResolver res = getContentResolver();

        boolean backupEnabled = false;
        Intent configIntent = null;
        String configSummary = null;
        try {
            backupEnabled = mBackupManager.isBackupEnabled();
            String transport = mBackupManager.getCurrentTransport();
            configIntent = mBackupManager.getConfigurationIntent(transport);
            configSummary = mBackupManager.getDestinationString(transport);
        } catch (RemoteException e) {
            // leave it 'false' and disable the UI; there's no backup manager
            mBackup.setEnabled(false);
        }
        mBackup.setChecked(backupEnabled);

        if (backupEnabled) {
            // provision the backup manager.
            IBackupManager bm = IBackupManager.Stub.asInterface(ServiceManager
                    .getService(Context.BACKUP_SERVICE));
            if (bm != null) {
                try {
                    bm.setBackupProvisioned(true);
                } catch (RemoteException e) {
                    Xlog.e(TAG, "set backup provisioned false!");
                }
            }
        }

		/*HQ_liugang add for HQ01456868*/		
		String productName = SystemProperties.get("ro.product.name", "");
		if (productName.equalsIgnoreCase("TAG-TL00") ||
			productName.equalsIgnoreCase("TAG-AL00") ||
			productName.equalsIgnoreCase("TAG-CL00")){
			mAutoRestore.setChecked(false);
			mAutoRestore.setEnabled(false);
		} else {
	        mAutoRestore.setChecked(Settings.Secure.getInt(res,
            	Settings.Secure.BACKUP_AUTO_RESTORE, 1) == 1);
        	mAutoRestore.setEnabled(backupEnabled);
		}
		/*HQ_liugang add end*/
		
        final boolean configureEnabled = (configIntent != null) && backupEnabled;
        mConfigure.setEnabled(configureEnabled);
        mConfigure.setIntent(configIntent);
        setConfigureSummary(configSummary);
    }

    private void setConfigureSummary(String summary) {
        if (summary != null) {
        	if("Need to set the backup account".equals(summary)){
        		mConfigure.setSummary(R.string.backup_configure_account_summary);
        	}else{        	
        		mConfigure.setSummary(summary);
        	}
        } else {
            mConfigure.setSummary(R.string.backup_configure_account_default_summary);
        }
    }

    private void updateConfigureSummary() {
        try {
            String transport = mBackupManager.getCurrentTransport();
            String summary = mBackupManager.getDestinationString(transport);
            setConfigureSummary(summary);
        } catch (RemoteException e) {
            // Not much we can do here
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // Dialog is triggered before Switch status change, that means marking the Switch to
        // true in showEraseBackupDialog() method will be override by following status change.
        // So we do manual switching here due to users' response.
        if (mDialogType == DIALOG_ERASE_BACKUP) {
            // Accept turning off backup
            if (which == DialogInterface.BUTTON_POSITIVE) {
                setBackupEnabled(false);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                // Reject turning off backup
                setBackupEnabled(true);
            }
            updateConfigureSummary();
        }
        mDialogType = 0;
    }

    /**
     * Informs the BackupManager of a change in backup state - if backup is disabled,
     * the data on the server will be erased.
     * @param enable whether to enable backup
     */
    private void setBackupEnabled(boolean enable) {
        if (mBackupManager != null) {
            try {
                mBackupManager.setBackupEnabled(enable);
                mBackupManager.setBackupProvisioned(enable);
            } catch (RemoteException e) {
                mBackup.setChecked(!enable);
                mAutoRestore.setEnabled(!enable);
                return;
            }
        }
        mBackup.setChecked(enable);
        mAutoRestore.setEnabled(enable);
        mConfigure.setEnabled(enable);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_backup_reset;
    }

    /**
     * For Search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new PrivacySearchIndexProvider();

    private static class PrivacySearchIndexProvider extends BaseSearchIndexProvider {

        boolean mIsPrimary;

        public PrivacySearchIndexProvider() {
            super();

            mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;
        }

        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {

            List<SearchIndexableResource> result = new ArrayList<SearchIndexableResource>();

            // For non-primary user, no backup or reset is available
            if (!mIsPrimary) {
                return result;
            }

            SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.privacy_settings;
            result.add(sir);

            return result;
        }

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            return getNonVisibleKeys(context);
        }
    }

    private static ArrayList<String> getNonVisibleKeys(Context context) {
        final ArrayList<String> nonVisibleKeys = new ArrayList<String>();
        final IBackupManager backupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));
        boolean isServiceActive = false;
        try {
            isServiceActive = backupManager.isBackupServiceActive(UserHandle.myUserId());
        } catch (RemoteException e) {
            Log.w(TAG, "Failed querying backup manager service activity status. " +
                    "Assuming it is inactive.");
        }
        if (isServiceActive) {
            nonVisibleKeys.add(BACKUP_INACTIVE);
        } else {
            nonVisibleKeys.add(AUTO_RESTORE);
            nonVisibleKeys.add(CONFIGURE_ACCOUNT);
            nonVisibleKeys.add(BACKUP_DATA);
        }
        if (UserManager.get(context).hasUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET)) {
            nonVisibleKeys.add(PERSONAL_DATA_CATEGORY);
        }
        // Vendor specific
        if (context.getPackageManager().
                resolveContentProvider(GSETTINGS_PROVIDER, 0) == null) {
            nonVisibleKeys.add(BACKUP_CATEGORY);
        } 
        return nonVisibleKeys;
    }
}
