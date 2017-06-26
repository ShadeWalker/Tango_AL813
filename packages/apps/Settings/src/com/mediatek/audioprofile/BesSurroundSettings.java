package com.mediatek.audioprofile;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;

import com.android.settings.R;
import com.mediatek.audioprofile.AudioProfileManager;
import com.mediatek.xlog.Xlog;

public class BesSurroundSettings extends SettingsPreferenceFragment implements
        CompoundButton.OnCheckedChangeListener, BesSurroundItem.OnClickListener {

    private static final String XLOGTAG = "Settings/AudioP";
    private static final String TAG = "BesSurroundSettings:";
    private Switch mSwitch;

    private static final String KEY_MOVIE_MODE = "movie_mode";
    private static final String KEY_MUSIC_MODE = "music_mode";

    private BesSurroundItem mMovieMode;
    private BesSurroundItem mMusicMode;
    private BesSurroundItem[] mBesSurroundItems;

    private AudioProfileManager mProfileManager;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.audioprofile_bessurrond_settings);
        mMovieMode = (BesSurroundItem) findPreference(KEY_MOVIE_MODE);
        mMusicMode = (BesSurroundItem) findPreference(KEY_MUSIC_MODE);
        mMovieMode.setOnClickListener(this);
        mMusicMode.setOnClickListener(this);
        mProfileManager = (AudioProfileManager) getSystemService(Context.AUDIO_PROFILE_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        createSwitch();
        initBesSurroundStatus();
    }

    public void createSwitch() {
        mSwitch = (Switch) getActivity().getLayoutInflater().inflate(
                com.mediatek.internal.R.layout.imageswitch_layout, null);
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final int padding = activity.getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        mSwitch.setPaddingRelative(0, 0, padding, 0);
        activity.getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM);
        activity.getActionBar().setCustomView(
                mSwitch,
                new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.END));
        mSwitch.setOnCheckedChangeListener(this);
        activity.setTitle(R.string.audio_profile_bes_surround_title);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Xlog.d(XLOGTAG, TAG + "onCheckedChanged: " + isChecked);
        mProfileManager.setBesSurroundState(isChecked);
        getPreferenceScreen().setEnabled(isChecked);
    }

    private void initBesSurroundStatus() {
        mSwitch.setChecked(mProfileManager.getBesSurroundState());
        getPreferenceScreen().setEnabled(mSwitch.isChecked());
        if (mProfileManager.getBesSurroundMode() == mProfileManager.KEY_MOVIE_MODE_CODE) {
            mMovieMode.setChecked(true);
            mMusicMode.setChecked(false);
        } else {
            mMovieMode.setChecked(false);
            mMusicMode.setChecked(true);
        }
    }

    public void onRadioButtonClicked(BesSurroundItem emiter) {
        if (emiter == mMovieMode) {
            mProfileManager.setBesSurroundMode(mProfileManager.KEY_MOVIE_MODE_CODE);
            mMusicMode.setChecked(false);
        } else if (emiter == mMusicMode) {
            mProfileManager.setBesSurroundMode(mProfileManager.KEY_MUSIC_MODE_CODE);
            mMovieMode.setChecked(false);
        }
        emiter.setChecked(true);
    }
}
