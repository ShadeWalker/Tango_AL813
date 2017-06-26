/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.internal.view.RotationPolicy;
import com.android.internal.view.RotationPolicy.RotationPolicyListener;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.notification.DropDownPreference.Callback;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import static android.provider.Settings.Secure.DOZE_ENABLED;
import static android.provider.Settings.Secure.WAKE_GESTURE_ENABLED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
import static android.provider.Settings.System.LED_LIGHT_MODE;
import static android.provider.Settings.System.LED_LIGHT_MODE_MANUAL;
import static android.provider.Settings.System.LED_LIGHT_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
import static android.provider.Settings.System.AUTO_ROTATE_SCREEN;
import android.R.bool;
import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ListView;

import com.mediatek.settings.DisplaySettingsExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;
import android.provider.Settings.SettingNotFoundException;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener, Indexable {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_LIFT_TO_WAKE = "lift_to_wake";
    private static final String KEY_DOZE = "doze";
    private static final String KEY_AUTO_BRIGHTNESS = "auto_brightness";
    //add xuqian4 HQ01216879 Display operator name
    private static final String KEY_DISPLAY_OPERATORNAME = "display_operatorname";
    private static final String KEY_HW_STATUS_BAR_OPERATORS= "hw_status_bar_operators";
    private static final String KEY_AUTO_ROTATE = "auto_rotate";
    private static final String KEY_LED_LIGHT = "led_light";
    private static final String KEY_BOOT_TIP = "boot_tip";
    
    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

    private WarnedListPreference mFontSizePref;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;
    private SwitchPreference mLiftToWakePreference;
    private SwitchPreference mDozePreference;
    private SwitchPreference mAutoBrightnessPreference;
    //add xuqian4 HQ01216879 Display operator name
    private SwitchPreference mDisplayOperatorNamePreference;
    
    //add by HQ_caoxuhao at 20150828 HQ01350351 begin
    private SwitchPreference mRotatePreference;
    //add by HQ_caoxuhao at 20150828 HQ01350351 end

    ///M: MTK feature
    private DisplaySettingsExt mDisplaySettingsExt;
	//add by HQ_zhouguo at 20150720
	private SwitchPreference ledLightPreference;
	private SwitchPreference mBootTipPreference;

    private ContentObserver mScreenTimeoutObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Xlog.d(TAG, "mScreenTimeoutObserver omChanged");
                int value = Settings.System.getInt(
                        getContentResolver(), SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
                updateTimeoutPreference(value);
            }

        };

     //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness start
     private ContentObserver mAutoBrightnessObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Xlog.d(TAG, "mAutoBrightnessObserver omChanged");
                int value = Settings.System.getInt(
                        getContentResolver(), SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
                updateAutoBrightnessState();
            }
        };
     //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness end

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //HQ_wuhuihui_20151015 add for UI display enlarge start
        final Activity activity = getActivity();
        int themeID = activity.getResources().getIdentifier("androidhwext:style/Theme.Emui", null, null);
        activity.setTheme(themeID);
        //HQ_wuhuihui_20151015 add for UI display enlarge end
        final ContentResolver resolver = activity.getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        ///M: MTK feature @{
        mDisplaySettingsExt = new DisplaySettingsExt(getActivity());
        mDisplaySettingsExt.onCreate(getPreferenceScreen());
        /// @}

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            mDisplaySettingsExt.removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        //HQ_hushunli 2015-11-05 add 10s for HQ01453041 begin
        if (SystemProperties.get("ro.hq.tigo.screen.timeout").equals("1")) {
            mScreenTimeoutPreference.setEntries(R.array.tigo_screen_timeout_entries);
            mScreenTimeoutPreference.setEntryValues(R.array.tigo_screen_timeout_values);
        } else {
            mScreenTimeoutPreference.setEntries(R.array.screen_timeout_entries);
            mScreenTimeoutPreference.setEntryValues(R.array.screen_timeout_values);
        }
        //HQ_hushunli 2015-11-05 add 10s for HQ01453041 end
        /**M: for fix bug ALPS00266723 @{*/
        final long currentTimeout = getTimoutValue();
        Xlog.d(TAG, "currentTimeout=" + currentTimeout);
        /**@}*/
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);
        //add xuqian4 HQ01216879 Display operator name
        mDisplayOperatorNamePreference = (SwitchPreference) findPreference(KEY_DISPLAY_OPERATORNAME);
        mDisplayOperatorNamePreference.setOnPreferenceChangeListener(this);

        if (isAutomaticBrightnessAvailable(getResources())) {
            mAutoBrightnessPreference = (SwitchPreference) findPreference(KEY_AUTO_BRIGHTNESS);
            mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
            
            //add by HQ_caoxuhao at 20150828 HQ01350351 begin
            mDisplaySettingsExt.removePreference(findPreference(KEY_AUTO_BRIGHTNESS));
            //add by HQ_caoxuhao at 20150828 HQ01350351 end
        } else {
            // removePreference(KEY_AUTO_BRIGHTNESS);
            mDisplaySettingsExt.removePreference(findPreference(KEY_AUTO_BRIGHTNESS));
        }

        if (isLiftToWakeAvailable(activity)) {
            mLiftToWakePreference = (SwitchPreference) findPreference(KEY_LIFT_TO_WAKE);
            mLiftToWakePreference.setOnPreferenceChangeListener(this);
        } else {
            // removePreference(KEY_LIFT_TO_WAKE);
            mDisplaySettingsExt.removePreference(findPreference(KEY_LIFT_TO_WAKE));
        }

        if (isDozeAvailable(activity)) {
            mDozePreference = (SwitchPreference) findPreference(KEY_DOZE);
            mDozePreference.setOnPreferenceChangeListener(this);
        } else {
            // removePreference(KEY_DOZE);
            mDisplaySettingsExt.removePreference(findPreference(KEY_DOZE));
        }

        //if (RotationPolicy.isRotationLockToggleVisible(activity)) {
        	//add by HQ_caoxuhao at 20150828 HQ01350351 begin
            mRotatePreference = (SwitchPreference)findPreference(KEY_AUTO_ROTATE);
            mRotatePreference.setOnPreferenceChangeListener(this);
        //add by HQ_caoxuhao at 20150828 HQ01350351 end
        //} else {
            // removePreference(KEY_AUTO_ROTATE);
        //    mDisplaySettingsExt.removePreference(findPreference(KEY_AUTO_ROTATE));
        //}
		//add by HQ_zhouguo at 20150720 start
        ledLightPreference = (SwitchPreference) findPreference(KEY_LED_LIGHT);
		ledLightPreference.setOnPreferenceChangeListener(this);
		//add by HQ_zhouguo at 20150720 end
		//HQ_hushunli 2015-11-30 add for HQ01454910 begin
		mBootTipPreference = (SwitchPreference) findPreference(KEY_BOOT_TIP);
		if (mBootTipPreference != null) {
		    mBootTipPreference.setOnPreferenceChangeListener(this);
		    try {
		        boolean tipSwitch = false;
                if (Settings.System.getInt(getContentResolver(), Settings.System.DATA_COST_TIP_SWITCH) == 1) {
                    tipSwitch = true;
                }
                mBootTipPreference.setChecked(tipSwitch);
            } catch (SettingNotFoundException snfe) {
                Log.d(TAG, "not found DATA_COST_TIP_SWITCH");
            }
	        if (!SystemProperties.get("ro.hq.show.datacostdip").equals("1")) {
	            mDisplaySettingsExt.removePreference(mBootTipPreference);
	        }
		}
		//HQ_hushunli 2015-11-30 add for HQ01454910 end
    }

    private static boolean allowAllRotations(Context context) {
        return Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_allowAllRotations);
    }

    private static boolean isLiftToWakeAvailable(Context context) {
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_WAKE_GESTURE) != null;
    }

    private static boolean isDozeAvailable(Context context) {
        String name = Build.IS_DEBUGGABLE ? SystemProperties.get("debug.doze.component") : null;
        if (TextUtils.isEmpty(name)) {
            name = context.getResources().getString(
                    com.android.internal.R.string.config_dozeComponent);
        }
        return !TextUtils.isEmpty(name);
    }

    private static boolean isAutomaticBrightnessAvailable(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_automatic_brightness_available);
    }

 @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Xlog.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        mCurConfig.updateFrom(newConfig);
    }

    private int getTimoutValue() {
        int currentValue = Settings.System.getInt(getActivity()
                .getContentResolver(), SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        Xlog.d(TAG, "getTimoutValue()---currentValue=" + currentValue);
        int bestMatch = 0;
        int timeout = 0;
        final CharSequence[] valuesTimeout = mScreenTimeoutPreference
                .getEntryValues();
        for (int i = 0; i < valuesTimeout.length; i++) {
            timeout = Integer.parseInt(valuesTimeout[i].toString());
            if (currentValue == timeout) {
                return currentValue;
            } else {
                if (currentValue > timeout) {
                    bestMatch = i;
                }
            }
        }
        Xlog.d(TAG, "getTimoutValue()---bestMatch=" + bestMatch);
        return Integer.parseInt(valuesTimeout[bestMatch].toString());

    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
            ///M: to prevent index out of bounds @{
            if (entries.length != 0) {
                summary = preference.getContext().getString(
                        R.string.screen_timeout_summary, entries[best]);
            } else {
                summary = "";
            }
           ///M: @}

            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        Xlog.w(TAG, "floatToIndex enter val = " + val);
        ///M: modify by MTK for EM @{
        int res = mDisplaySettingsExt.floatToIndex(mFontSizePref, val);
        if (res != -1) {
            return res;
        }
        /// @}

        String[] indices = null;
        try {
        	indices = getResources().getStringArray(R.array.entryvalues_font_size);
		} catch (Exception e) {
			//do nothing
			Xlog.w(TAG, "floatToIndex getResources failed");
		}
        
        if (indices != null) {
        	float lastVal = Float.parseFloat(indices[0]);
            for (int i=1; i<indices.length; i++) {
                float thisVal = Float.parseFloat(indices[i]);
                if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                    return i-1;
                }
                lastVal = thisVal;
            }
            return indices.length-1;
		}else {
			return 1;
		}
    }

    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        Xlog.d(TAG, "readFontSizePreference index = " + index);
        pref.setValueIndex(index);

        // report the current size in the summary text
        Resources res = null;
        try {
        	res = getResources();
		} catch (Exception e) {
			//do nothing
			Xlog.d(TAG, "readFontSizePreference getResources failed");
		}
        
        if (res != null) {
        	String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
            /*HQ_xupeixin at 2015-11-11 modified about special deal the index >= length exception begin*/
            if (fontSizeNames != null) {
                int len = fontSizeNames.length;
                Xlog.d(TAG, "readFontSizePreference fontSizeNames len: " + len);
                if (index >= len) {
                    index = len -1;
                }
            }
            /*HQ_xupeixin at 2015-11-11 modified end*/
            pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                    fontSizeNames[index]));
		}
    }

    @Override
    public void onResume() {
        super.onResume();
        //add HQ_xuqian4 HQ01378873 start
        RotationPolicy.registerRotationPolicyListener(getActivity(), mRotationPolicyListener);
        //add HQ_xuqian4 HQ01378873 end
        getContentResolver().registerContentObserver(Settings.System.getUriFor(SCREEN_OFF_TIMEOUT),
                false, mScreenTimeoutObserver);
        //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness start
        getContentResolver().registerContentObserver(Settings.System.getUriFor(SCREEN_BRIGHTNESS_MODE),
                false, mAutoBrightnessObserver);
        //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness end
        if(isAdded()){
        	updateState();
        }
        ///M: MTK feature
        mDisplaySettingsExt.onResume();
        
    }

    @Override
    public void onPause() {
        super.onPause();
        ///M: MTK feature
        //add HQ_xuqian4 HQ01378873 start
        RotationPolicy.unregisterRotationPolicyListener(getActivity(),mRotationPolicyListener);
        //add HQ_xuqian4 HQ01378873 end
        mDisplaySettingsExt.onPause();
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }
    //add HQ_xuqian4 HQ01378873 start
    private RotationPolicyListener mRotationPolicyListener = new RotationPolicyListener() {
     @Override
     public void onChange() {
         if (mRotatePreference != null) {
         mRotatePreference.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
         }
     }
    };
    //add HQ_xuqian4 HQ01378873 end

    private void updateState() {
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();

	//add by HQ_caoxuhao at 20150909 HQ01356778 begin
	if(mRotatePreference != null){
/*            int speed = Settings.System.getInt(getContentResolver(),
            		Settings.System.AUTO_ROTATE_SCREEN, 0);
            mRotatePreference.setChecked(!(speed == 1));*/
      //add HQ_xuqian4 HQ01378873 start
		mRotatePreference.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
      //add HQ_xuqian4 HQ01378873 end
	}
        //add by HQ_caoxuhao at 20150909 HQ01356778 end

        //add xuqian4 HQ01216879 Display operator name
        int displayoperatorname = Settings.System.getInt(getContentResolver(),KEY_HW_STATUS_BAR_OPERATORS, 1);
        mDisplayOperatorNamePreference.setChecked(displayoperatorname == 1);

        // Update auto brightness if it is available.
        if (mAutoBrightnessPreference != null) {
            int brightnessMode = Settings.System.getInt(getContentResolver(),
                    SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
            mAutoBrightnessPreference.setChecked(brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        // Update lift-to-wake if it is available.
        if (mLiftToWakePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), WAKE_GESTURE_ENABLED, 0);
            mLiftToWakePreference.setChecked(value != 0);
        }

        // Update doze if it is available.
        if (mDozePreference != null) {
            int value = Settings.Secure.getInt(getContentResolver(), DOZE_ENABLED, 1);
            mDozePreference.setChecked(value != 0);
        }
	//add by HQ_zhouguo at 20150720 start
	if(ledLightPreference != null){
	    int value = Settings.System.getInt(getContentResolver(), LED_LIGHT_MODE, LED_LIGHT_MODE_AUTOMATIC);
            ledLightPreference.setChecked(value != LED_LIGHT_MODE_MANUAL);
        }
    }
	//add by HQ_zhouguo at 20150720 end

    //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness start
    private void updateAutoBrightnessState() {
        if (mAutoBrightnessPreference != null) {
            int brightnessMode = Settings.System.getInt(getContentResolver(),
                    SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
            mAutoBrightnessPreference.setChecked(brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
    }
    //add HQ_xuqian4 HQ01344317  Synchronization automatically adjust the brightness end

    /**M: for fix bug not sync status bar when lock screen @{*/
    private void updateTimeoutPreference(int currentTimeout) {
        Xlog.d(TAG, "currentTimeout=" + currentTimeout);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        updateTimeoutPreferenceDescription(currentTimeout);
        AlertDialog dlg = (AlertDialog) mScreenTimeoutPreference.getDialog();
        if (dlg == null || !dlg.isShowing()) {
            return;
        }
        ListView listview = dlg.getListView();
        int checkedItem = mScreenTimeoutPreference.findIndexOfValue(
        mScreenTimeoutPreference.getValue());
        if (checkedItem > -1) {
            listview.setItemChecked(checkedItem, true);
            listview.setSelection(checkedItem);
        }
    }
    /**@}*/

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            Xlog.d(TAG, "writeFontSizePreference font size =  " + Float.parseFloat(objValue.toString()));
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        ///M: add MTK feature @{
        mDisplaySettingsExt.onPreferenceClick(preference);
        /// @}
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        //add xuqian4 HQ01216879 Display operator name start
        if(KEY_DISPLAY_OPERATORNAME.equals(key)){
           boolean displayon = (Boolean) objValue;
           Settings.System.putInt(getContentResolver(), KEY_HW_STATUS_BAR_OPERATORS,displayon ? 1 : 0);
        }
        //add xuqian4 HQ01216879 Display operator name end
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        if (preference == mAutoBrightnessPreference) {
            boolean auto = (Boolean) objValue;
            Settings.System.putInt(getContentResolver(), SCREEN_BRIGHTNESS_MODE,
                    auto ? SCREEN_BRIGHTNESS_MODE_AUTOMATIC : SCREEN_BRIGHTNESS_MODE_MANUAL);
        }
        if (preference == mLiftToWakePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), WAKE_GESTURE_ENABLED, value ? 1 : 0);
        }
        if (preference == mDozePreference) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putInt(getContentResolver(), DOZE_ENABLED, value ? 1 : 0);
        }
		//add by HQ_zhouguo at 20150720 start
		if(preference == ledLightPreference){
		    
		    boolean auto = (Boolean) objValue;
	            Settings.System.putInt(getContentResolver(), LED_LIGHT_MODE,
	                    auto ? LED_LIGHT_MODE_AUTOMATIC : LED_LIGHT_MODE_MANUAL);
		}
		//add by HQ_zhouguo at 20150720 end
		
		//add by HQ_caoxuhao at 20150828 HQ01350351 begin
		if (preference == mRotatePreference) {
       //add HQ_xuqian4 HQ01378873 start
			boolean value = (Boolean) objValue;
//            Settings.System.putInt(getContentResolver(),  Settings.System.AUTO_ROTATE_SCREEN, value ? 0 : 1);
            RotationPolicy.setRotationLock(getActivity(), !value);
		}
       //add HQ_xuqian4 HQ01378873 end
		//add by HQ_caoxuhao at 20150828 HQ01350351 end
		if (preference == mBootTipPreference) {//HQ_hushunli 2015-11-30 add for HQ01454910
		    boolean value = (Boolean) objValue;
		    Settings.System.putInt(getContentResolver(), Settings.System.DATA_COST_TIP_SWITCH, value ? 1 : 0);
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

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.display_settings;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    ArrayList<String> result = new ArrayList<String>();
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_dreamsSupported)
                            || FeatureOption.MTK_GMO_RAM_OPTIMIZE) {
                        result.add(KEY_SCREEN_SAVER);
                    }
                    if (!isAutomaticBrightnessAvailable(context.getResources())) {
                        result.add(KEY_AUTO_BRIGHTNESS);
                    }
                    if (!isLiftToWakeAvailable(context)) {
                        result.add(KEY_LIFT_TO_WAKE);
                    }
                    if (!isDozeAvailable(context)) {
                        result.add(KEY_DOZE);
                    }
                    if (!RotationPolicy.isRotationLockToggleVisible(context)) {
                        result.add(KEY_AUTO_ROTATE);
                    }
                    return result;
                }
            };
}
