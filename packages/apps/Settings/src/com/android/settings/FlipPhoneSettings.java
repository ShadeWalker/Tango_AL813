/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class FlipPhoneSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "FlipPhoneSettings";
    public static final String KEY_FLIP_PHONE_SWITCH = "flip_phone_switch";
    public static final String KEY_FLIP_PHONE_VALUE = "flip_phone_value";

    private SwitchPreference flipPhoneSwitch;
    private ListPreference flipPhoneValue;

    private static final int DEFAULT_VALUE = 0;

    private boolean switchValue = true;
    private int flipValue = 0;
    private String[] summarys;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.flip_phone_settings);
        getActivity().getActionBar().setSubtitle(R.string.accessibility_settings);
        flipPhoneSwitch = (SwitchPreference) this.findPreference(KEY_FLIP_PHONE_SWITCH);
        flipPhoneSwitch.setOnPreferenceChangeListener(this);
        flipPhoneValue = (ListPreference) this.findPreference(KEY_FLIP_PHONE_VALUE);
        flipPhoneValue.setOnPreferenceChangeListener(this);
        switchValue = Settings.System.getInt(getContentResolver(),
                Settings.System.FLIP_PHONE_SWITCH, 1) == 1;
        flipValue = Settings.System.getInt(getContentResolver(), Settings.System.FLIP_PHONE_VALUE,
                DEFAULT_VALUE);
        summarys = getResources().getStringArray(R.array.flip_phone_items);
        Log.d(TAG, "switchValue=" + switchValue + ",flipValue=" + flipValue + ",summarys.length="
                + summarys.length);
        flipPhoneSwitch.setChecked(switchValue);
        updateFlipPhoneStatus(switchValue);
        updateFlipPhoneSummary(summarys[flipValue]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "flipValue=" + flipValue);
        if (preference == flipPhoneValue) {
            flipValue = Integer.parseInt((String) newValue);
            updateFlipPhoneSummary(summarys[flipValue]);
            Settings.System.putInt(getContentResolver(), Settings.System.FLIP_PHONE_VALUE,
                    flipValue);
            return true;
        }

        if (preference == flipPhoneSwitch) {
           switchValue = flipPhoneSwitch.isChecked();
            Log.d(TAG, "switchValue=" + switchValue);
            updateFlipPhoneStatus(switchValue);
            Settings.System.putInt(getContentResolver(), Settings.System.FLIP_PHONE_SWITCH,
                    switchValue ? 1 : 0);
            return true;
        }        
        return false;
    }

    private void updateFlipPhoneStatus(boolean value) {
        flipPhoneValue.setEnabled(value);
    }

    private void updateFlipPhoneSummary(String summary) {
        flipPhoneValue.setSummary(summary);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == flipPhoneSwitch) {
            switchValue = flipPhoneSwitch.isChecked();
            Log.d(TAG, "switchValue=" + switchValue);
            updateFlipPhoneStatus(switchValue);
            Settings.System.putInt(getContentResolver(), Settings.System.FLIP_PHONE_SWITCH,
                    switchValue ? 1 : 0);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
