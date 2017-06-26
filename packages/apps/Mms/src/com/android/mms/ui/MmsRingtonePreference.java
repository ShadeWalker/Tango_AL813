/*
 * Add by MTK
 *
 * for optimize chat setting ring tone.
 */
package com.android.mms.ui;

import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.Preference;
import android.preference.RingtonePreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.os.SystemProperties;

import android.util.Log;


public class MmsRingtonePreference extends RingtonePreference {

    private final String TAG = "MmsRingtonePreference";
    private static final String IS_FOLLOW_NOTIFICATION = "is_follow_notification";

    public MmsRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        String mmsRingtone = NotificationPreferenceActivity.getMmsRingtone(getContext());
        Uri mmsRingtoneUri = Uri.parse(mmsRingtone);

        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                onRestoreRingtone());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                android.media.RingtoneManager.TYPE_NOTIFICATION);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getTitle());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, mmsRingtoneUri);
  
     if(SystemProperties.get("ro.hq.sms.default.ringtone").equals("1")){
        ringtonePickerIntent.putExtra("android.intent.extra.ringtone.DEFAULT_STRING", mmsRingtone);
     }else{
      if(mmsRingtoneUri != null && Settings.System.DEFAULT_NOTIFICATION_URI.equals(mmsRingtoneUri.toString())){
            ringtonePickerIntent.putExtra(IS_FOLLOW_NOTIFICATION, true);
        } else {
            ringtonePickerIntent.putExtra(IS_FOLLOW_NOTIFICATION, false);
        }
      }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
       if((data != null)&& !SystemProperties.get("ro.hq.sms.default.ringtone").equals("1")) {
        boolean is_follow_notification = data.getBooleanExtra(IS_FOLLOW_NOTIFICATION, false);
        SharedPreferences.Editor editor = this.getEditor();
        editor.putBoolean(IS_FOLLOW_NOTIFICATION, is_follow_notification);
        editor.commit();
       }
        return super.onActivityResult(requestCode, resultCode, data);
    }
}
