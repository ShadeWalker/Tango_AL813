package com.mediatek.settings.ext;

import android.app.Fragment;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.mediatek.audioprofile.AudioProfileManager;

 public class DefaultAudioProfileExt  extends ContextWrapper implements IAudioProfileExt {

    private Fragment mContext;
    private LayoutInflater mInflater;

    private TextView mTextView = null;
    private RadioButton mCheckboxButton = null;
    private TextView mSummary = null;
    private ImageView mImageView = null;

    private View mLayout;

    private boolean mHasMoreRingtone = false;

    public DefaultAudioProfileExt(Context context) {
        super(context);
   }

    public boolean isPrefEditable() {
        return false;
    }

    public View createView(Context context, int defaultLayoutId) {
        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mLayout = mInflater.inflate(defaultLayoutId, null);
        return mLayout;
    }

    public View getPreferenceTitle(int defaultTitleId) {
        mTextView = (TextView) mLayout.findViewById(defaultTitleId);
        return mTextView;
    }

    public View getPreferenceSummary(int defaultSummaryId) {
        mSummary = (TextView) mLayout.findViewById(defaultSummaryId);
           return mSummary;
    }

    public View getPrefRadioButton(int defaultRBId) {
        mCheckboxButton = (RadioButton) mLayout.findViewById(defaultRBId);
        return mCheckboxButton;
    }

    public View getPrefImageView(int defaultImageViewId) {
        mImageView = (ImageView) mLayout.findViewById(defaultImageViewId);
        return mImageView;
    }

    public void setRingtonePickerParams(Intent intent) {
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_MORE_RINGTONES, false);
        mHasMoreRingtone = true;
    }

    public void setRingerVolume(AudioManager audiomanager, int volume) {
        audiomanager.setStreamVolume(AudioProfileManager.STREAM_RING, volume, 0);
        audiomanager.setStreamVolume(AudioProfileManager.STREAM_NOTIFICATION, volume, 0);
    }

   public void setVolume(AudioManager audiomanager, int streamType, int volume) {
       audiomanager.setStreamVolume(streamType, volume, 0);
    }

   @Override
   public void addCustomizedPreference(PreferenceScreen preferenceScreen) {
   }

   @Override
   public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
       return false;
   }

   @Override
   public void onAudioProfileSettingResumed(PreferenceFragment fragment) {
   }

   @Override
   public void onAudioProfileSettingPaused(PreferenceFragment fragment) {
   }

    @Override
    public void removeRingVolumePreference(PreferenceScreen preferenceScreen) {
    }
}
