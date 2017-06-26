/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
// add for gemini
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
//import android.provider.Telephony.SIMInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import com.mediatek.xlog.Xlog;
import java.util.List;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends PreferenceActivity {
    public static final String TAG = "[ETWS]CellBroadcastSettings";
    // Preference key for whether to enable emergency notifications (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Duration of alert sound (in seconds).
    public static final String KEY_ALERT_SOUND_DURATION = "alert_sound_duration";

    // Default alert duration (in seconds).
    public static final String ALERT_SOUND_DEFAULT_DURATION = "4";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_ALERT_SETTINGS = "category_alert_settings";

    // Preference category for ETWS related settings.
    public static final String KEY_CATEGORY_ETWS_SETTINGS = "category_etws_settings";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference category for Brazil specific settings.
    public static final String KEY_CATEGORY_BRAZIL_SETTINGS = "category_brazil_settings";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // cb message filter entry    
    public static final String CELL_BROADCAST = "pref_key_cell_broadcast";    
    public static final String SUB_TITLE_NAME = "sub_title_name";
    private Preference mCBsettingPref;

     private Preference mAlertVolumePref;
    // add for gemini
    //private static int mSimId = -1;
     private static int mSubId = -1;

    public static final String KEY_ENABLE_ETWS_ALERT = "enable_etws_alerts";
    private MediaPlayer mMediaPlayer;
    private Handler handler;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "CellBroadcastSetting::onCreate()++");

        // add for gemini
        if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
            addPreferencesFromResource(R.xml.preferences);
            initGeminiPreference(getIntent());
        } else {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(android.R.id.content,
                    new CellBroadcastSettingsFragment(CellBroadcastSettings.this)).commit();
       }
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);

        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            setContentView(R.layout.cell_broadcast_disallowed_preference_screen);
            return;
        }

        getActionBar().setDisplayHomeAsUpEnabled(true);
        handler = new Handler();
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {
        private CellBroadcastSettings mContext;
        public CellBroadcastSettingsFragment() {

        }

        public CellBroadcastSettingsFragment(CellBroadcastSettings ctx) {
            mContext = ctx;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Log.i(TAG, "CellBroadcastSettingsFragment::OnCreate()++");

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen preferenceScreen = getPreferenceScreen();

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    Log.i(TAG, "onPreferenceChange::pref.getKye() = " + pref.getKey());
                    CellBroadcastReceiver.startConfigService(pref.getContext());
                    return true;
                }
            };

            //show ETWS
            Preference enableEtwsAlerts = findPreference(KEY_ENABLE_ETWS_ALERT);
            if (enableEtwsAlerts != null) {
                enableEtwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            // alert sound duration
            ListPreference duration = (ListPreference) findPreference(KEY_ALERT_SOUND_DURATION);
            duration.setSummary(duration.getEntry());
            duration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    final ListPreference listPref = (ListPreference) pref;
                    final int idx = listPref.findIndexOfValue((String) newValue);
                    listPref.setSummary(listPref.getEntries()[idx]);
                    return true;
                }
            });
            duration.setDependency(KEY_ENABLE_ETWS_ALERT);

            //alert sound volume
            Preference volume = findPreference(KEY_ALERT_SOUND_VOLUME);
            OnPreferenceClickListener l = new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Log.i(TAG, "getAlertVolumeListener onclicked ");
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
                    LayoutInflater flater = mContext.getLayoutInflater();
                    View v = flater.inflate(R.layout.alert_dialog_view, null);
                    SeekBar sb = (SeekBar)v.findViewById(R.id.seekbar);
                    // set bar's progress
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                    float pro = 1.0f;
                    pro =prefs.getFloat(KEY_ALERT_SOUND_VOLUME, 1.0f);
                    int progress = (int)(pro*100);
                    if (progress < 0) {
                        progress = 0;
                    } else if (progress > 100) {
                        progress = 100;
                    }
                    //Xlog.d(TAG, "open volume setting,progress:"+progress+",pro:"+pro);
                    sb.setProgress(progress);
                    sb.setOnSeekBarChangeListener(mContext.getSeekBarListener());
                    dialog.setTitle(R.string.alert_sound_volume)
                    .setView(v)
                    .setPositiveButton(R.string.button_dismiss, new OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            // TODO Auto-generated method stub
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                            SharedPreferences.Editor editor = prefs.edit();

                            editor.putFloat(KEY_ALERT_SOUND_VOLUME, mAlertVolume);

                            editor.commit();
                        }
                    })
                    .setNegativeButton(R.string.button_cancel, new OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            // TODO Auto-generated method stub
                            arg0.dismiss();
                        }
                    })
                    .show();
                    return true;
                }
            };

            volume.setOnPreferenceClickListener(l);
            volume.setDependency(KEY_ENABLE_ETWS_ALERT);

            // alert reminder interval
            ListPreference interval = (ListPreference) findPreference(KEY_ALERT_REMINDER_INTERVAL);
            interval.setSummary(interval.getEntry());
            interval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    final ListPreference listPref = (ListPreference) pref;
                    final int idx = listPref.findIndexOfValue((String) newValue);
                    listPref.setSummary(listPref.getEntries()[idx]);
                    return true;
                }
            });
            interval.setDependency(KEY_ENABLE_ETWS_ALERT);

            //vibrate
            CheckBoxPreference vibrate = (CheckBoxPreference)findPreference(KEY_ENABLE_ALERT_VIBRATE);
            vibrate.setDependency(KEY_ENABLE_ETWS_ALERT);

            //speak aler message
            CheckBoxPreference speech = (CheckBoxPreference)findPreference(KEY_ENABLE_ALERT_SPEECH);
            speech.setDependency(KEY_ENABLE_ETWS_ALERT);

            Preference enableEtwsTestAlerts = findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            if (enableEtwsTestAlerts != null) {
                enableEtwsTestAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            enableEtwsTestAlerts.setDependency(KEY_ENABLE_ETWS_ALERT);

        }
    }

    // add for gemini functions
    // the default value here is the same as preferences.xml
    public static final boolean ENABLE_EMERGENCY_ALERTS_DEFAULT = true;
    // alert sound duration default value is exist
    // alert sound volume
    public static final String KEY_ALERT_SOUND_VOLUME = "alert_sound_volume";
    private static float mAlertVolume = 1.0f;
    public static final boolean ENABLE_ETWS_TEST_ALERTS_DEFAULT = true;
    public static final boolean ENABLE_ALERT_SPEECH_DEFAULT = true;
    public static final boolean ENABLE_CMAS_EXTREME_THREAT_ALERTS_DEFAULT = true;
    public static final boolean ENABLE_CMAS_SEVERE_THREAT_ALERTS_DEFAULT = true;
    public static final boolean ENABLE_CMAS_AMBER_ALERTS_DEFAULT = false;
    public static final boolean ENABLE_CMAS_TEST_ALERTS_DEFAULT = false;
    public static final boolean ENABLE_CHANNEL_50_ALERTS_DEFAULT = true;

    private void initGeminiPreference(Intent it) {
        Log.i(TAG, "initGeminiPreference ++");
        //mSimId = getIntent().getIntExtra("sim_id", -1);
        //Xlog.d(TAG, "mSimId:" + mSimId);
        mSubId = it.getIntExtra("subscription",-1);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Preference.OnPreferenceChangeListener startConfigServiceListener =
        new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference pref, Object newValue) {
                CellBroadcastReceiver.startConfigServiceGemini(pref.getContext(), mSubId);
                return true;
            }
        };

        // show ETWS
        CheckBoxPreference enableEtwsAlerts = (CheckBoxPreference)findPreference(KEY_ENABLE_ETWS_ALERT);
        if (enableEtwsAlerts != null) {
            String newKey = KEY_ENABLE_ETWS_ALERT + "_"+ mSubId;
            enableEtwsAlerts.setKey(newKey);
            enableEtwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);

            enableEtwsAlerts.setChecked(prefs.getBoolean(newKey, true));
        }

        ListPreference alertSoundDuration = (ListPreference) findPreference(KEY_ALERT_SOUND_DURATION);
        if (alertSoundDuration != null) {
            String newKey = KEY_ALERT_SOUND_DURATION + "_"+ mSubId;
            alertSoundDuration.setKey(newKey);
            alertSoundDuration.setSummary(prefs.getString(newKey, ALERT_SOUND_DEFAULT_DURATION));
            alertSoundDuration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    final ListPreference listPref = (ListPreference) pref;
                    final int idx = listPref.findIndexOfValue((String) newValue);
                    listPref.setSummary(listPref.getEntries()[idx]);
                    return true;
                }
            });
            alertSoundDuration.setValue(prefs.getString(newKey, ALERT_SOUND_DEFAULT_DURATION));
        }
        alertSoundDuration.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);

        // alert reminder interval
        ListPreference reminder = (ListPreference) findPreference(KEY_ALERT_REMINDER_INTERVAL);
        reminder.setKey(KEY_ALERT_REMINDER_INTERVAL + "_" + mSubId);
        reminder.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference pref, Object newValue) {
                final ListPreference listPref = (ListPreference) pref;
                final int idx = listPref.findIndexOfValue((String) newValue);
                listPref.setSummary(listPref.getEntries()[idx]);
                return true;
            }
        });
        String deafult = ((CharSequence[])reminder.getEntries())[0].toString();
        reminder.setValue(prefs.getString(KEY_ALERT_REMINDER_INTERVAL + "_" + mSubId, deafult));
        reminder.setSummary(reminder.getEntry());
        reminder.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);

        CheckBoxPreference alertVibrate = (CheckBoxPreference)findPreference(KEY_ENABLE_ALERT_VIBRATE);
        alertVibrate.setKey(KEY_ENABLE_ALERT_VIBRATE + "_" + mSubId);
        alertVibrate.setChecked(prefs.getBoolean(KEY_ENABLE_ALERT_VIBRATE + "_" + mSubId, true));
        alertVibrate.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);

        CheckBoxPreference enableAlertSpeech = (CheckBoxPreference) findPreference(KEY_ENABLE_ALERT_SPEECH);
        if (enableAlertSpeech != null) {
            String newKey = KEY_ENABLE_ALERT_SPEECH + "_"+ mSubId;
            enableAlertSpeech.setKey(newKey);
            enableAlertSpeech.setChecked(prefs.getBoolean(newKey, ENABLE_ALERT_SPEECH_DEFAULT));
            enableAlertSpeech.setOnPreferenceChangeListener(startConfigServiceListener);
        }
        enableAlertSpeech.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);

        CheckBoxPreference enableEtwsTestAlerts = (CheckBoxPreference) findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
        if (enableEtwsTestAlerts != null) {
            String newKey = KEY_ENABLE_ETWS_TEST_ALERTS + "_"+ mSubId;
            enableEtwsTestAlerts.setKey(newKey);
            enableEtwsTestAlerts.setChecked(prefs.getBoolean(newKey, ENABLE_ETWS_TEST_ALERTS_DEFAULT));
        }
        enableEtwsTestAlerts.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);

        // update title as sim card name
        //SIMInfo simInfo = SIMInfo.getSIMInfoById(this, mSimId);
        //TODO: done
        SubscriptionInfo si = SubscriptionManager.from(this).getActiveSubscriptionInfo(mSubId);
        if (si != null) {
            setTitle(si.getDisplayName().toString());
        }

        Preference alertVolume = (Preference)findPreference(KEY_ALERT_SOUND_VOLUME);
        alertVolume.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);
        if(alertVolume != null) {
            alertVolume.setOnPreferenceClickListener(getAlertVolumeListener());
            alertVolume.setDependency(KEY_ENABLE_ETWS_ALERT + "_"+ mSubId);
        }
    }

    private OnPreferenceClickListener getAlertVolumeListener() {
        Log.i(TAG, "getAlertVolumeListener ++ ");
        OnPreferenceClickListener l = new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Log.i(TAG, "getAlertVolumeListener onclicked ");
                final AlertDialog.Builder dialog = new AlertDialog.Builder(CellBroadcastSettings.this);
                LayoutInflater flater = getLayoutInflater();
                View v = flater.inflate(R.layout.alert_dialog_view, null);
                SeekBar sb = (SeekBar)v.findViewById(R.id.seekbar);
                // set bar's progress                
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CellBroadcastSettings.this);
                float pro = 1.0f;
                if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
                    pro = prefs.getFloat(KEY_ALERT_SOUND_VOLUME+"_"+ mSubId, 1.0f);
                } else {
                    pro =prefs.getFloat(KEY_ALERT_SOUND_VOLUME, 1.0f);
                }
                int progress = (int)(pro*100);
                if (progress < 0) {
                    progress = 0;
                } else if (progress > 100) {
                    progress = 100;
                }
                //Xlog.d(TAG, "open volume setting,progress:"+progress+",pro:"+pro);
                sb.setProgress(progress);
                sb.setOnSeekBarChangeListener(getSeekBarListener());
                dialog.setTitle(R.string.alert_sound_volume)
                .setView(v)
                .setPositiveButton(R.string.button_dismiss, new PositiveButtonListener())
                .setNegativeButton(R.string.button_cancel, new NegativeButtonListener())
                .show();
                return true;
            }
        };
        return l;
    }

    private SeekBar.OnSeekBarChangeListener getSeekBarListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mAlertVolume = progress/100.0f;
                    Xlog.d(TAG, "volume:"+mAlertVolume);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mMediaPlayer != null) {
                    mMediaPlayer.stop();
                } else {
                    mMediaPlayer = new MediaPlayer();
                    mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    try {
                        AssetFileDescriptor afd = getResources().openRawResourceFd(
                                R.raw.attention_signal);
                        if (afd != null) {
                            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd
                                    .getStartOffset(), afd.getLength());
                            afd.close();
                        }
                    } catch (Exception e) {
                        Xlog.e(TAG, "exception onStartTrackingTouch: " + e);
                    }
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // make some sample sound
                try {
                    mMediaPlayer.setVolume(mAlertVolume, mAlertVolume);
                    mMediaPlayer.prepare();
                    mMediaPlayer.seekTo(0);
                    mMediaPlayer.start();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                                mMediaPlayer.stop();
                                Xlog.d(TAG, "handler post stop at 100 millis");
                            }
                        }
                    }, 100);
                } catch (Exception e) {
                    Xlog.e(TAG, "exception onStopTrackingTouch: " + e);
                }
            }
        };
    }

    private class PositiveButtonListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            //save alert sound volume
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CellBroadcastSettings.this);
            SharedPreferences.Editor editor = prefs.edit();
            if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
                editor.putFloat(KEY_ALERT_SOUND_VOLUME+"_"+mSubId, mAlertVolume);
            	
            } else {
                editor.putFloat(KEY_ALERT_SOUND_VOLUME, mAlertVolume);
            }
            editor.commit();
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    }

    private class NegativeButtonListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // cancel
            dialog.dismiss();
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    }

//TODO: not used anywhere
/*
    private static SubscriptionInfo getSubInfoGemini(Context context) {
    	SubscriptionInfo info = null;
		int[] subId;
        if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
			subId = SubscriptionManager.getSubIdUsingSlotId(0);
			if (subId != null && subId.length > 0) {			
				info = SubscriptionManager.getSubscriptionInfo(subId[0]);
			}            
            if (info == null) {
				subId = SubscriptionManager.getSubIdUsingSlotId(1);
                if (subId != null && subId.length > 0) {			
					info = SubscriptionManager.getSubscriptionInfo(subId[0]);
				} 
            }
        } else {
            subId = SubscriptionManager.getSubIdUsingSlotId(0);
			if (subId != null && subId.length > 0) {			
				info = SubscriptionManager.getSubscriptionInfo(subId[0]);
			}     
        }	
        return info;
    }
    */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }

        return true;
    }
}
