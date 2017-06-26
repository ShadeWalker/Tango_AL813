/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.IntentSender.SendIntentException;
import android.media.AudioManager;
import android.os.Bundle;
import android.app.ActionBar;
import android.view.Gravity;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.content.Context;
import android.os.SystemProperties;
import android.widget.Switch;
import android.provider.Settings;
import android.content.ContentResolver;
import android.content.DialogInterface.OnClickListener;
import android.widget.CompoundButton;
import android.provider.Settings.SettingNotFoundException;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.TwoStatePreference;

public class KeylockGesturesSettings extends SettingsPreferenceFragment {
    private final String TAG = "KeylockGesturesSettings";
    private TwoStatePreference mEnabledSwitch;
    private IconListPreference mGestures_c;	
    private IconListPreference mGestures_e;
    private IconListPreference mGestures_w;
    private IconListPreference mGestures_m;

    private static final String KEY_GESTURES_C = "gesture_type_c";
    private static final String KEY_GESTURES_E = "gesture_type_e";
    private static final String KEY_GESTURES_W = "gesture_type_w";
    private static final String KEY_GESTURES_M = "gesture_type_m";
	private static final String KEY_GESTURES_ENABLER = "gesture_enabler";
 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		ContentResolver resolver = getActivity().getContentResolver();
		
        addPreferencesFromResource(R.xml.keylockgestures_settings);		

		mGestures_c = (IconListPreference)findPreference(KEY_GESTURES_C);
		mGestures_e = (IconListPreference)findPreference(KEY_GESTURES_E);
		mGestures_m = (IconListPreference)findPreference(KEY_GESTURES_M);
		mGestures_w = (IconListPreference)findPreference(KEY_GESTURES_W);
		mEnabledSwitch = (TwoStatePreference) findPreference(KEY_GESTURES_ENABLER);
		setPreferenceListener(KEY_GESTURES_ENABLER, mEnabledSwitch);

		try
		{
			if(Settings.System.getInt(resolver,Settings.System.KEYLOCK_GESTURES_SWITCH) != 1)
			{			
				mGestures_c.setEnabled(false);
				mGestures_e.setEnabled(false);
				mGestures_m.setEnabled(false);
				mGestures_w.setEnabled(false);
				
				sendGestureBroadcast("/proc/gesture_enablec","0");				
				sendGestureBroadcast("/proc/gesture_enablee","0");
				sendGestureBroadcast("/proc/gesture_enablew","0");
				sendGestureBroadcast("/proc/gesture_enablem","0");
				sendGestureBroadcast("/proc/gesture_enablecc","0");

			}

		}catch(SettingNotFoundException snfe)
		{
			Log.e(TAG, Settings.System.KEYLOCK_GESTURES_SWITCH + " not found");
		}
				
    }

	private void setPreferenceListener(final String preferenceType, Preference p) {
        p.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preferenceType.equals(KEY_GESTURES_ENABLER)){//gesture switch
					setCheckedChanged((Boolean)newValue);
				}
                return true;
            }
        });
    }

	@Override
    public void onStart() {
        super.onStart();

        // On/off switch is hidden for Setup Wizard (returns null)
        try{
           ContentResolver resolver = getActivity().getContentResolver();
           mEnabledSwitch.setChecked(Settings.System.getInt(resolver,
                                     Settings.System.KEYLOCK_GESTURES_SWITCH) == 1);
        }catch(SettingNotFoundException snfe){
           Log.e(TAG, Settings.System.KEYLOCK_GESTURES_SWITCH + " not found");
        }
    }

    @Override
    public void onPause() {
      
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
     
        return true;
    }
	
	
    public void setCheckedChanged(boolean newValue) {
        if(newValue)
        {
            sendGestureBroadcast("/proc/gesture_enable","1");
			sendGestureBroadcast("/proc/gesture_enablec","1");				
            sendGestureBroadcast("/proc/gesture_enablee","1");
            sendGestureBroadcast("/proc/gesture_enablew","1");
            sendGestureBroadcast("/proc/gesture_enablem","1");
			
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_SWITCH,1);
			Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_C,1);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_E,1);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_W,1);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_M,1);

			mGestures_c.setEnabled(true);
			mGestures_e.setEnabled(true);
			mGestures_m.setEnabled(true);
			mGestures_w.setEnabled(true);
        }
        else
        {
            sendGestureBroadcast("/proc/gesture_enable","0");
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_SWITCH,0);

			mGestures_c.setEnabled(false);
			mGestures_e.setEnabled(false);
			mGestures_m.setEnabled(false);
			mGestures_w.setEnabled(false);

			

            sendGestureBroadcast("/proc/gesture_enablec","0");				
            sendGestureBroadcast("/proc/gesture_enablee","0");
            sendGestureBroadcast("/proc/gesture_enablew","0");
            sendGestureBroadcast("/proc/gesture_enablem","0");

            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_C,0);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_E,0);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_W,0);
            Settings.System.putInt(getContentResolver(), Settings.System.KEYLOCK_GESTURES_M,0);

       }
    }

	@Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
	{		
        return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	private void sendGestureBroadcast(String ProcName,String flag)
	{
	    	Intent gestureIntent = new Intent("android.intent.action.GESTURE_ENABLE");
			Bundle bundle=new Bundle();
			bundle.putString("flag", flag);
			bundle.putString("procName",ProcName);
			gestureIntent.putExtras(bundle);
			Log.d(TAG, "send Broadcast gestureIntent");
			getActivity().sendBroadcast(gestureIntent);

	}   
}
