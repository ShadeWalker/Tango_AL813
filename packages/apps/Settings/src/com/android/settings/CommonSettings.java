 /*   
  * Copyright (C) 2008 The Android Open Source Project  
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
import java.util.List;

import com.android.settings.accessibility.ChildModeSettings;
import com.android.settings.R;
import com.android.settings.Settings.BatterySaverSettingsActivity;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.SettingsPreferenceFragment;

import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.app.Fragment;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.app.ActivityManagerNative;
import android.content.res.Configuration;
import android.os.RemoteException;

import com.mediatek.xlog.Xlog;

import android.preference.ListPreference;

import com.mediatek.settings.DisplaySettingsExt;

import android.app.Dialog;

/**
 * Top-level settings activity to handle single pane and double pane UI layout.
 */
public class CommonSettings extends SettingsPreferenceFragment implements
		Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
	private static final String TAG = "CommonSettings";

	private static final String KEY_WIFI_SETTINGS = "wifiSettings";//chenwenshuai add for HQ01454819
	private static final String KEY_BLUETOOTH_SETTINGS = "bluetooth_settings";
	private static final String KEY_FONT_SIZE = "font_size";
	private Activity mActivity;
	private WarnedListPreference mFontSizePref;
	private BluetoothPreference mBTPref;//chenwenshuai add for HQ01454819
	private WifiPreference mWifiPref;//chenwenshuai add for HQ01454819
	private final Configuration mCurConfig = new Configuration();
	private DisplaySettingsExt mDisplaySettingsExt;
	private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		addPreferencesFromResource(R.xml.common_settings_headers);

		mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
		mBTPref = (BluetoothPreference)findPreference(KEY_BLUETOOTH_SETTINGS);//chenwenshuai add for HQ01454819
		mWifiPref = (WifiPreference)findPreference(KEY_WIFI_SETTINGS);//chenwenshuai add for HQ01454819
		if(mFontSizePref != null){
			mFontSizePref.setOnPreferenceChangeListener(this);
			mFontSizePref.setOnPreferenceClickListener(this);
		}
		
		mDisplaySettingsExt = new DisplaySettingsExt(getActivity());
	}

	@Override
	public void onAttach(Activity paramActivity) {
		super.onAttach(paramActivity);
		this.mActivity = paramActivity;
		Log.d(TAG, "mActivity:" + mActivity);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause");

	}

	@Override
	public void onResume() {
		super.onResume();
	}

	/* HQ_ChenWenshuai 2015-11-05 modified for HQ01454819 begin */
	@Override
    public void onDetach() {
    	super.onDetach();
		mBTPref.unregisterReceiver();
		mWifiPref.unregisterReceiver();
	}
	/*HQ_ChenWenshuai 2015-11-05 modified end */

	@Override
	public Dialog onCreateDialog(int dialogId) {
		if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
			return Utils.buildGlobalChangeWarningDialog(getActivity(),
					R.string.global_font_change_title, new Runnable() {
						public void run() {
							mFontSizePref.click();
						}
					});
		}
		return null;
	}

	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {
		Log.d(TAG,
				"onPreferenceTreeClick() + fragment:"
						+ preference.getFragment());
		int titleRes = preference.getTitleRes();
		
        if (preference.getKey().equals("battery_saving_settings")){
            start3rdPartyActivity("com.huawei.systemmanager", 
                    "com.huawei.systemmanager.power.ui.HwPowerManagerActivity");
            return true;
        }
       //modified by maolikui at 2015-12-29 start 
       if(preference.getKey().equals("system_update_settings")){
            start3rdPartyActivity("com.huawei.android.hwouc", 
                    "com.huawei.android.hwouc.ui.activities.MainEntranceActivity");
            return true;
	}
       //modified by maolikui at 2015-12-29 end
		if (preference.getKey().equals("wifiSettings")
				|| preference.getKey().equals("bluetooth_settings")
				|| preference.getKey().equals("wallpaper")
				|| preference.getKey().equals("notification_settings")
				|| preference.getKey().equals("screenlock_password_settings")
				|| preference.getKey().equals("display_settings")
				|| preference.getKey().equals("fingerprint_settings")) {
			startPreferencePanel(preference.getFragment(),
					preference.getExtras(), titleRes, preference.getTitle(),
					null, 0);
			return true;
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

    private void start3rdPartyActivity(String packageName, String className) {
        Intent intent = new Intent();
        ComponentName cn = new ComponentName(packageName,className);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(cn);
        List<ResolveInfo> apps = mActivity.getPackageManager()
                                   .queryIntentActivities(intent, 0);
        if (apps == null || apps.size() == 0) {
            Toast.makeText(mActivity, "Huawei application is not installed",
            Toast.LENGTH_SHORT).show();
        } else {
            mActivity.startActivity(intent);
        }
    }

	/**
	 * Start a new fragment containing a preference panel. If the preferences
	 * are being displayed in multi-pane mode, the given fragment class will be
	 * instantiated and placed in the appropriate pane. If running in
	 * single-pane mode, a new activity will be launched in which to show the
	 * fragment.
	 * 
	 * @param fragmentClass
	 *            Full name of the class implementing the fragment.
	 * @param args
	 *            Any desired arguments to supply to the fragment.
	 * @param titleRes
	 *            Optional resource identifier of the title of this fragment.
	 * @param titleText
	 *            Optional text of the title of this fragment.
	 * @param resultTo
	 *            Optional fragment that result data should be sent to. If
	 *            non-null, resultTo.onActivityResult() will be called when this
	 *            preference panel is done. The launched panel must use
	 *            {@link #finishPreferencePanel(Fragment, int, Intent)} when
	 *            done.
	 * @param resultRequestCode
	 *            If resultTo is non-null, this is the caller's request code to
	 *            be received with the result.
	 */
	public void startPreferencePanel(String fragmentClass, Bundle args,
			int titleRes, CharSequence titleText, Fragment resultTo,
			int resultRequestCode) {
		Log.d(TAG, "startPreferencePanel() + fragmentClass:" + fragmentClass);
		String title = null;
		if (titleRes < 0) {
			if (titleText != null) {
				title = titleText.toString();
			} else {
				// There not much we can do in that case
				title = "";
			}
		}
		Utils.startWithFragment(mActivity, fragmentClass, args, resultTo,
				resultRequestCode, titleRes, title);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Xlog.d(TAG, "onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
		mCurConfig.updateFrom(newConfig);
	}

	public void readFontSizePreference(ListPreference pref) {
		try {
			mCurConfig.updateFrom(ActivityManagerNative.getDefault()
					.getConfiguration());
		} catch (RemoteException e) {
			Log.w(TAG, "Unable to retrieve font size");
		}

		// mark the appropriate item in the preferences list
		int index = floatToIndex(mCurConfig.fontScale);
		Xlog.d(TAG, "readFontSizePreference index = " + index);
		pref.setValueIndex(index);

		// report the current size in the summary text
		final Resources res = getResources();
		String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
		pref.setSummary(String.format(
				res.getString(R.string.summary_font_size), fontSizeNames[index]));
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object objValue) {
		final String key = preference.getKey();
		if (KEY_FONT_SIZE.equals(key)) {
			writeFontSizePreference(objValue);
		}
		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mFontSizePref) {
			if (Utils.hasMultipleUsers(getActivity())) {
				showDialog(DLG_GLOBAL_CHANGE_WARNING);
				return true;
			} else {
				mFontSizePref.click();
			}
		}
		return false;
	}

	public void writeFontSizePreference(Object objValue) {
		try {
			mCurConfig.fontScale = Float.parseFloat(objValue.toString());
			Xlog.d(TAG,
					"writeFontSizePreference font size =  "
							+ Float.parseFloat(objValue.toString()));
			ActivityManagerNative.getDefault().updatePersistentConfiguration(
					mCurConfig);
		} catch (RemoteException e) {
			Log.w(TAG, "Unable to save font size");
		}
	}

	int floatToIndex(float val) {
		Xlog.w(TAG, "floatToIndex enter val = " + val);
		// /M: modify by MTK for EM @{
		int res = mDisplaySettingsExt.floatToIndex(mFontSizePref, val);
		if (res != -1) {
			return res;
		}
		// / @}

		String[] indices = getResources().getStringArray(
				R.array.entryvalues_font_size);
		float lastVal = Float.parseFloat(indices[0]);
		for (int i = 1; i < indices.length; i++) {
			float thisVal = Float.parseFloat(indices[i]);
			if (val < (lastVal + (thisVal - lastVal) * .5f)) {
				return i - 1;
			}
			lastVal = thisVal;
		}
		return indices.length - 1;
	}
}
