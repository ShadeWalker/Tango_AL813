package com.android.phone.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.util.AttributeSet;

import com.android.internal.telephony.Phone;
import com.android.phone.common.util.SettingsUtil;

/**
 * Looks up the voicemail ringtone's name asynchronously and updates the preference's summary when
 * it is created or updated.
 */
public class VoicemailRingtonePreference extends RingtonePreference {
    private static final int MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY = 1;

    private static final String IS_FOLLOW_NOTIFICATION = "is_follow_notification";
    
    private Runnable mVoicemailRingtoneLookupRunnable;
    private Handler mVoicemailRingtoneLookupComplete;

    private Phone mPhone;

    public VoicemailRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mVoicemailRingtoneLookupComplete = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY:
                        setSummary((CharSequence) msg.obj);
                        break;
                }
            }
        };
    }

    public void init(Phone phone) {
        mPhone = phone;

        // Requesting the ringtone will trigger migration if necessary.
        VoicemailNotificationSettingsUtil.getRingtoneUri(phone);

        final Preference preference = this;
        final String preferenceKey =
                VoicemailNotificationSettingsUtil.getVoicemailRingtoneSharedPrefsKey(mPhone);
        mVoicemailRingtoneLookupRunnable = new Runnable() {
            @Override
            public void run() {
                SettingsUtil.updateRingtoneName(
                        preference.getContext(),
                        mVoicemailRingtoneLookupComplete,
                        RingtoneManager.TYPE_NOTIFICATION,
                        preferenceKey,
                        MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY);
            }
        };

        updateRingtoneName();
    }

    @Override
    protected Uri onRestoreRingtone() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mPhone.getContext());
		boolean is_follow_notification = prefs.getBoolean(
				VoicemailNotificationSettingsUtil
						.getVoicemailRingtoneFollowSharedPrefsKey(mPhone),
				false);
		if (is_follow_notification) {
			return Settings.System.DEFAULT_NOTIFICATION_URI;
		} else {
			return VoicemailNotificationSettingsUtil.getRingtoneUri(mPhone);
		}
    }

    @Override
    protected void onSaveRingtone(Uri ringtoneUri) {
        // Don't call superclass method because it uses the pref key as the SharedPreferences key.
        // Delegate to the voicemail notification utility to save the ringtone instead.
        VoicemailNotificationSettingsUtil.setRingtoneUri(mPhone, ringtoneUri);

        updateRingtoneName();
    }

    private void updateRingtoneName() {
        new Thread(mVoicemailRingtoneLookupRunnable).start();
    }
    
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
    	super.onPrepareRingtonePickerIntent(ringtonePickerIntent);
        Uri uri = onRestoreRingtone();
        //android.util.Log.d(TAG, "uri=" + uri + ",Settings.System.DEFAULT_NOTIFICATION_URI=" + Settings.System.DEFAULT_NOTIFICATION_URI);
        if (uri != null && uri.toString() == Settings.System.DEFAULT_NOTIFICATION_URI.toString()) {
        	android.util.Log.d("VoicemailRingtonePreference", "set is_follow_notification true");
			ringtonePickerIntent.putExtra(IS_FOLLOW_NOTIFICATION, true);
        } else {
        	android.util.Log.d("VoicemailRingtonePreference", "set is_follow_notification false");
			ringtonePickerIntent.putExtra(IS_FOLLOW_NOTIFICATION, false);
        }
    }
    
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (data != null) {
    		boolean is_follow_notification = data.getBooleanExtra("is_follow_notification", false);
    		android.util.Log.d("VoicemailRingtonePreference", "is_follow_notification=" + is_follow_notification);
    		String key = VoicemailNotificationSettingsUtil.getVoicemailRingtoneFollowSharedPrefsKey(mPhone);
    		SharedPreferences.Editor editor = this.getEditor();
    		editor.putBoolean(key, is_follow_notification);
    		editor.commit();
    	}
        return super.onActivityResult(requestCode, resultCode, data);
    }
}
