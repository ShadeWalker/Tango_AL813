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
import android.preference.RingtonePreference;
import android.util.AttributeSet;
import android.util.Log;


public class ChatRingtonePreference extends RingtonePreference {

    private final String TAG = "ChatRingtonePreference";

    public ChatRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        String mmsRingtone = NotificationPreferenceActivity.getMmsRingtone(getContext());
        Uri mmsRingtoneUri = Uri.parse(mmsRingtone);
        Log.d(TAG, "onPrepareRingtonePickerIntent mmsRingtoneUri:" + mmsRingtoneUri);

        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                onRestoreRingtone());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                android.media.RingtoneManager.TYPE_NOTIFICATION);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getTitle());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, mmsRingtoneUri);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null && data.getExtras() != null) {
            int position = data.getExtras().getInt(RingtoneManager.EXTRA_RINGTONE_PICKED_POSITION);
            Log.d(TAG, "onActivityResult position:" + position);
            final int DEFAULT_RINGTONE_POSITION = 0;
            if (position == DEFAULT_RINGTONE_POSITION) {
                data.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        ChatPreferenceActivity.DEFAULT_RINGTONE);
            }
        }
        return super.onActivityResult(requestCode, resultCode, data);
    }
}
