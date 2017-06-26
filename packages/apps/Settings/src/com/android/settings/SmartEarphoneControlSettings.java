package com.android.settings;

import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.content.Intent;
import android.util.Log;


public class SmartEarphoneControlSettings extends SettingsPreferenceFragment {

    public static final String TAG = "SmartEarphoneControlSettings";

    public static final String SMART_EARPHONE_CONTROL_SWITCH = "smart_earphone_control_switch";
    public static final String BROADCAST_INTENT_ACTION = "com.android.settings.SmartHeadsetControlPreference.SHStatus";
    private SwitchPreference smartEarphoneControlSwitch;
    private SharedPreferences mSharedPreferences;
    private int isChecked;

    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        addPreferencesFromResource(R.xml.smart_earphone_control_settings);        
        smartEarphoneControlSwitch = (SwitchPreference) findPreference(SMART_EARPHONE_CONTROL_SWITCH);
        try {
            isChecked = Settings.System.getInt(getContentResolver(), "smart_earphone_control");
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
		//mSharedPreferences = smartEarphoneControlSwitch.getSharedPreferences();
		//boolean value = mSharedPreferences.getBoolean(SMART_EARPHONE_CONTROL_SWITCH, false);
		smartEarphoneControlSwitch.setChecked(isChecked == 1);
		smartEarphoneControlSwitch.setOnPreferenceChangeListener(mOnPreferenceChangeListener);
    }

	public void onResume(){
		super.onResume();
		try {
            isChecked = Settings.System.getInt(getContentResolver(), "smart_earphone_control");
        	} catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        	}
		smartEarphoneControlSwitch.setChecked(isChecked == 1);
	}

    OnPreferenceChangeListener mOnPreferenceChangeListener = new OnPreferenceChangeListener() {
		
		@Override
		public boolean onPreferenceChange(Preference arg0, Object arg1) {
			//Disable Smart Earphone Control as default
			smartEarphoneControlSwitch.setChecked(!smartEarphoneControlSwitch.isChecked());
			//Editor editor = mSharedPreferences.edit();
			//editor.putBoolean(SMART_EARPHONE_CONTROL_SWITCH, smartEarphoneControlSwitch.isChecked());
			//editor.commit();
			Settings.System.putInt(getContentResolver(), "smart_earphone_control", smartEarphoneControlSwitch.isChecked()?1:0);
                        //add by wangwenjia start
			Intent intent = new Intent(BROADCAST_INTENT_ACTION);
		        getActivity().sendBroadcast(intent);
			//add by wangwenjia end

			return false;
		}
    };
}
