package com.mediatek.phone.ext;

import android.app.AlertDialog;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class DefaultMobileNetworkSettingsExt implements IMobileNetworkSettingsExt {

    @Override
    public void initOtherMobileNetworkSettings(PreferenceActivity activity) {
    }

    @Override
    public void initOtherMobileNetworkSettings(PreferenceActivity activity, int subId) {
    }

    @Override
    public void initMobileNetworkSettings(PreferenceActivity activity, int currentTab) {
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return false;
    }

    @Override
    public void updateLTEModeStatus(ListPreference preference) {
    }

    @Override
    public void updateNetworkTypeSummary(ListPreference preference) {
    }

    @Override
    public void customizeAlertDialog(Preference preference, AlertDialog.Builder builder) {
    }

    @Override
    public void customizePreferredNetworkMode(ListPreference listPreference, int subId) {
    }

    @Override
    public void onPreferenceChange(Preference preference, Object objValue) {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void unRegister() {
    }

    @Override
    public boolean isCtPlugin() {
        return false;
    }

    @Override
    public boolean useCTTestcard() {
        return false;
    }	
    
    @Override
    public boolean volteEnabledForOperator() {
        return true;
    }
}
