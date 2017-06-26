package com.mediatek.audioprofile;

import java.util.List;

import com.android.internal.telephony.PhoneConstants;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.Indexable;
import com.mediatek.settings.FeatureOption;
import com.mediatek.xlog.Xlog;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.Locale;
import android.os.SystemProperties;

public class SoundSettingsBase extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "SoundSettingsBase";

    public static final String KEY_RING_VOLUME = "ring_volume";
    public static final String KEY_RING_VOLUME_SILENT = "ring_volume_silent";
    public static final String KEY_VIBIRATE_WHEN_SILENT = "vibirate_when_silent";

    public static final String KEY_RINGTONE1 = "ringtone1";
    public static final String KEY_RINGTONE2 = "ringtone2";
    public static final String KEY_VIBRATE_WHEN_RINGING = "vibrate_when_ringing";
    public static final String KEY_VIBRATE_WHEN_RINGING_SIM2 = "vibrate_when_ringing_sim2";

    public static final String KEY_SINGSING_EFFECTS = "singsing_effects";

    public static final String KEY_NOTIFY = "notifications_ringtone";
    public static final String KEY_DTMF_TONE = "dtmf_tone";
    public static final String KEY_SOUND_EFFECTS = "sound_effects";
    public static final String KEY_LOCK_SOUNDS = "lock_sounds";
    public static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";
    
    public static final String KEY_MUSIC_PLUS = "music_plus";
    public static final String KEY_BES_LOUDNESS = "bes_loudness";
    public static final String KEY_BES_SURROUND = "bes_surround";
    public static final String KEY_BES_LOSSLESS = "bes_lossless";
    
    protected static final int MESSAGE_NOTIFICATION = 2;
    protected static final int MESSAGE_RINGTONE1 = 3;
    protected static final int MESSAGE_RINGTONE2 = 4;
    
    protected Preference mVolumePreference;
    protected SwitchPreference mVolumeSilent;
    protected SwitchPreference mVibirateWhenSilent;

    protected DefaultRingtonePreferenceHq mRingtone1;
    protected DefaultRingtonePreferenceHq mRingtone2;
    protected SwitchPreference mVibrateWhenRinging;
//    protected SwitchPreference mVibrateWhenRinging2;

    protected SwitchPreference mSingingEffects;

    protected DefaultRingtonePreferenceHq mNotificationPreference;
    protected SwitchPreference mDtmfTone;
    protected SwitchPreference mSoundEffects;
    protected SwitchPreference mHapticFeedback;
    protected SwitchPreference mLockSounds;

    protected AudioManager mAudioManager;
    private int mCurOrientation;
//    protected int mRingtoneType = -1;

    private final BroadcastReceiver mSilentModeReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if (intent.getAction().equals(
                    AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateSilentMode();
            }
        }
    };

    public boolean isMultiSimEnabled() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    protected void changeVibrateMode(boolean isVibrate) {
        if (isVibrate)
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        else
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
    }

    protected void enableRingVolumeSilent(boolean isEnable) {
        if (isEnable) {
            changeVibrateMode(isStoredVibirateWhenSilent());
        } else {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    protected void enableVibirateWhenSilent(boolean isEnable) {
        ContentResolver cr = getContentResolver();
        Settings.Global.putInt(cr, Settings.System.VIBIRATE_WHEN_SILENT, isEnable ? 1 : 0);
        if (isRingVolumeSilent())
            changeVibrateMode(isEnable);
    }

    protected SwitchPreference findPreferenceAndSetListener(String preference) {
        SwitchPreference pref = (SwitchPreference) findPreference(preference);
        if (pref != null)
            pref.setOnPreferenceChangeListener(this);
        return pref;
    }

    protected void init(Bundle bundle) {
        initRingVolume();
        initRingtone();
        initSingingEffects();
        initFeedback();
    }

    private void initFeedback() {
        mNotificationPreference = ((DefaultRingtonePreferenceHq) findPreference("notification_sound"));
//        mNotificationPreference.setFragment(this);
        mNotificationPreference
                .setRingtoneType(RingtoneManager.TYPE_NOTIFICATION);
        mDtmfTone = findPreferenceAndSetListener(KEY_DTMF_TONE);
        mSoundEffects = findPreferenceAndSetListener(KEY_SOUND_EFFECTS);
        mHapticFeedback = findPreferenceAndSetListener(KEY_HAPTIC_FEEDBACK);
        mLockSounds = findPreferenceAndSetListener(KEY_LOCK_SOUNDS);
    }

    protected void initRingVolume() {
        mVolumePreference = findPreference(KEY_RING_VOLUME);
        mVolumeSilent = ((SwitchPreference) findPreferenceAndSetListener(KEY_RING_VOLUME_SILENT));
        mVibirateWhenSilent = ((SwitchPreference) findPreferenceAndSetListener(KEY_VIBIRATE_WHEN_SILENT));
    }

    private void initSingingEffects() {

    }

    private void initRingtone() {
        mRingtone1 = (DefaultRingtonePreferenceHq) findPreference(KEY_RINGTONE1);
//        mRingtone1.setFragment(this);
        mRingtone1.setRingtoneType(RingtoneManager.TYPE_RINGTONE);
        mRingtone2 = (DefaultRingtonePreferenceHq) findPreference(KEY_RINGTONE2);
        /* HQ_zhangpeng5 2015-12-03 modified for change sim2 ringtone title string for latin_open_market HQ01501477 begin */
        String locale = Locale.getDefault().getLanguage();
        if(locale.equals("es") && SystemProperties.get("ro.hq.ringtone2.title").equals("1")){
            mRingtone2.setTitle(R.string.ringtone_sim2_title);
        }
        /* HQ_zhangpeng5 2015-12-03 modified for change sim2 ringtone title string for latin_open_market HQ01501477 end*/
//        mRingtone2.setFragment(this);
        mRingtone2.setRingtoneType(RingtoneManager.TYPE_RINGTONE_SIM2);

        mVibrateWhenRinging = findPreferenceAndSetListener(KEY_VIBRATE_WHEN_RINGING);
//        mVibrateWhenRinging2 = findPreferenceAndSetListener(KEY_VIBRATE_WHEN_RINGING_SIM2);
    }

    protected boolean isRingVolumeSilent() {
        return mAudioManager.getRingerMode() != 2;
    }

    protected boolean isStoredVibirateWhenSilent() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.System.VIBIRATE_WHEN_SILENT, 0) == 1;
    }

    protected boolean isVibirateWhenSilent() {
        boolean result = isStoredVibirateWhenSilent();
        ContentResolver cr = getContentResolver();
        if (isRingVolumeSilent()) {
            if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                result = false;
                Settings.Global.putInt(cr, Settings.System.VIBIRATE_WHEN_SILENT, result ? 1
                        : 0);
            /* HQ_xuqian4 2015-9-30 modified for Vibration switch on begin */
            }else{
                result = true;
            }
            /* HQ_xuqian4 2015-9-30 modified end */
        }
        return result;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        /*
         * Uri uri = null; if ((resultCode == Activity.RESULT_OK) && (data !=
         * null) && ((requestCode == 12) || (requestCode == 13) || (requestCode
         * == 20))) { uri = (Uri)
         * data.getParcelableExtra("android.intent.extra.ringtone.PICKED_URI");
         * 
         * if (uri == null) uri = data.getData();
         */
		/*if (RingtoneManager.TYPE_RINGTONE == mRingtoneType) {
			mRingtone1.changeRingtone(uri);
		} else if (RingtoneManager.TYPE_RINGTONE_SIM2 == mRingtoneType) {
			mRingtone2.changeRingtone(uri);
		} else if (RingtoneManager.TYPE_NOTIFICATION == mRingtoneType) {
			mNotificationPreference.changeRingtone(uri);
		}*/
         
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        addPreferencesFromResource(R.xml.sound_settings);
        mAudioManager = (AudioManager) getSystemService("audio");
        init(bundle);
    }

    public void onDestroy() {
        super.onDestroy();
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mSilentModeReceiver);
    }

    public boolean onPreferenceChange(Preference pref, Object newValue) {
        String key = pref.getKey();

        if (KEY_RING_VOLUME_SILENT.equals(key)) {
            enableRingVolumeSilent(((Boolean) newValue).booleanValue());
            return true;
        } else if (KEY_VIBRATE_WHEN_RINGING.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING,
                    (boolean) newValue ? 1 : 0);
            return true;
        } /*else if (KEY_VIBRATE_WHEN_RINGING_SIM2.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING_SIM2,
                    (boolean) newValue ? 1 : 0);
            return true;
        }*/ else if (KEY_VIBIRATE_WHEN_SILENT.equals(key)) {
            enableVibirateWhenSilent(((Boolean) newValue).booleanValue());
            return true;
        } else if (KEY_DTMF_TONE.equals(key)) {
            Settings.System.putInt(getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING,
                    (boolean) newValue ? 1 : 0);
            return true;
        } else if (KEY_SOUND_EFFECTS.equals(key)) {
            if ((boolean) newValue)
                mAudioManager.loadSoundEffects();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SOUND_EFFECTS_ENABLED, (boolean) newValue ? 1 : 0);
            mAudioManager.unloadSoundEffects();
            return true;
        } else if (KEY_LOCK_SOUNDS.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_SOUNDS_ENABLED,
                    ((Boolean) newValue).booleanValue() ? 1 : 0);
            return true;
        } else if (KEY_HAPTIC_FEEDBACK.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, (boolean) newValue ? 1 : 0);
            return true;
        }
        return false;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
       /* if (preference == mRingtone1)
            mRingtoneType = mRingtone1.getRingtoneType();
        else if (preference == mRingtone2)
            mRingtoneType = mRingtone2.getRingtoneType();
        else if (preference == mNotificationPreference)
            mRingtoneType = mNotificationPreference.getRingtoneType();
        else if (mVolumePreference == preference) {
            mVolumePreference.setEnabled(false);
            startActivity(new Intent(getActivity(),
                    RingerVolumeDialogActivity.class));
        } else*/
            return super.onPreferenceTreeClick(preferenceScreen, preference);

//        return false;
    }

    public void onResume() {
        super.onResume();
        updateSilentMode();
        updateRingtoneSummary();
        updateVibrateWhenRinging();
        updateFeedbackPreference();
        IntentFilter intentFilter = new IntentFilter(
                AudioManager.RINGER_MODE_CHANGED_ACTION);
        getActivity().registerReceiver(mSilentModeReceiver, intentFilter);
        mVolumePreference.setEnabled(true);
    }

    private void updateVibrateWhenRinging() {
        mVibrateWhenRinging
                .setChecked(Settings.System.getInt(getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING, 1) == 1);
        /*mVibrateWhenRinging2.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING_SIM2, 1) == 1);*/
    }

    protected void updateFeedbackPreference() {
        ContentResolver cr = getContentResolver();

        mDtmfTone.setChecked(Settings.System.getInt(cr, 
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1);
        mSoundEffects.setChecked(Settings.System.getInt(cr,
                Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1);
        mLockSounds.setChecked(Settings.System.getInt(cr,
                Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) == 1);
        mHapticFeedback.setChecked(Settings.System.getInt(cr,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1);
    }

    protected void updateSilentMode() {
        mVolumeSilent.setChecked(isRingVolumeSilent());
        mVibirateWhenSilent.setChecked(isVibirateWhenSilent());
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_NOTIFICATION:
                mNotificationPreference.setSummary((CharSequence) msg.obj);
                break;
            case MESSAGE_RINGTONE1:
                mRingtone1.setSummary((CharSequence) msg.obj);
                break;
            case MESSAGE_RINGTONE2:
                mRingtone2.setSummary((CharSequence) msg.obj);
                break;
            default:
                break;
            }
        }
    };

    protected void queryRingtoneSummary(int type, Preference pref, int what) {
        if (pref == null)
            return;
        //modified by jiangchao for HQ02055768 at 2016-9-21 start
        if (getActivity() == null)
            return;
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(getActivity(), type);
        if (!isAdded())
            return;
        String summery = getString(R.string.not_set_ringtone);

        if (uri == null) {
            if (isAdded()) {
                summery = getString(R.string.no_default_ringtone);
                mHandler.sendMessage(mHandler.obtainMessage(what, summery));
                return;
            }//modified by jiangchao for HQ02055768 end
        }

        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[] { "title" }, null,
                    null, null);
            if (c != null && c.moveToFirst()) {
                summery = c.getString(0);
            }
            mHandler.sendMessage(mHandler.obtainMessage(what, summery));
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (c != null)
                c.close();
        }
    }

    private void updateRingtoneSummary() {
        new Thread(new Runnable() {
            public void run() {
                if (mNotificationPreference != null)
                    queryRingtoneSummary(
                            mNotificationPreference.getRingtoneType(),
                            mNotificationPreference, MESSAGE_NOTIFICATION);
                if (mRingtone1 != null)
                    queryRingtoneSummary(mRingtone1.getRingtoneType(),
                            mRingtone1, MESSAGE_RINGTONE1);
                if (mRingtone2 != null)
                    queryRingtoneSummary(mRingtone2.getRingtoneType(),
                            mRingtone1, MESSAGE_RINGTONE2);
            }
        }).start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Xlog.d(TAG, "onConfigurationChanged: newConfig = " + newConfig
                + ",mCurOrientation = " + mCurOrientation + ",this = " + this);
        super.onConfigurationChanged(newConfig);
        if (newConfig != null && newConfig.orientation != mCurOrientation) {
            mCurOrientation = newConfig.orientation;
        }
        getListView().clearScrapViewsIfNeeded();
    }
}
