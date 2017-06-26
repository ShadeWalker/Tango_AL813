/* Copyright (C) 2008 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
     
     HQ_daiwenqiang 20151224 add for HQ01588720 
*/

package com.android.settings;

import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;

import android.util.Log;


public class TouchProofSettings extends SettingsPreferenceFragment {

    public static final String TAG = "TouchProofSettings";

    public static final String TOUCH_DISABLE_MODE_SWITCH = "touch_disable_mode_switch";

    private SwitchPreference touchDisableModeSwitch;
    private int isChecked;

    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        addPreferencesFromResource(R.xml.touch_proof_settings);
        touchDisableModeSwitch = (SwitchPreference) findPreference(TOUCH_DISABLE_MODE_SWITCH);
        try {
            isChecked = Settings.System.getInt(getContentResolver(), "touch_disable_mode");
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
		touchDisableModeSwitch.setChecked(isChecked == 1);
		touchDisableModeSwitch.setOnPreferenceChangeListener(mOnPreferenceChangeListener);
    }

    OnPreferenceChangeListener mOnPreferenceChangeListener = new OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference arg0, Object arg1) {
			touchDisableModeSwitch.setChecked(!touchDisableModeSwitch.isChecked());
			Settings.System.putInt(getContentResolver(), "touch_disable_mode", touchDisableModeSwitch.isChecked()?1:0);
			return false;
		}
    };

}
