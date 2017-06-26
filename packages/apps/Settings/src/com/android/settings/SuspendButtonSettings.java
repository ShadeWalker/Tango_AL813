package com.android.settings;

import android.os.Bundle;
import android.preference.SwitchPreference;

public class SuspendButtonSettings extends SettingsPreferenceFragment {

    private SuspendButtonEnabler mEnabler;

    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        addPreferencesFromResource(R.xml.suspend_button_settings);
        SwitchPreference suspendButtonPreference = (SwitchPreference) findPreference("suspend_button_switch");
        mEnabler = new SuspendButtonEnabler(getActivity(), suspendButtonPreference);
    }

    public void onPause() {
        super.onPause();
        mEnabler.pause();
    }

    public void onResume() {
        super.onResume();
        mEnabler.resume();
    }
}