package com.mediatek.audioprofile;

import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.xlog.Xlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.Preference;
import android.preference.VolumePreference;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.util.Log;
import android.widget.TextView;
import android.preference.SeekBarVolumizer;

public class RingerVolumeDialogActivity extends Activity {
     private static final String TAG = "RingerVolumeDialogActivity";

    private static final int[] SEEKBAR_ID = { R.id.ringer_volume_seekbar,
            R.id.media_volume_seekbar, R.id.alarm_volume_seekbar,
            R.id.voice_volume_seekbar };

    private static final int[] SEEKBAR_TYPE = { AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL };

	protected static final int UPDATE_UI = 101;

    private AudioManager mAudioManager;

    private AlertDialog mDialog;
    private final H mHandler = new H();
    private BroadcastReceiver mRingModeChangedReceiver;
    private SeekBarVolumizer[] mSeekBarVolumizer;
    private SeekBar[] mSeekBars = new SeekBar[SEEKBAR_ID.length];
    private final VolumePreferenceCallback mVolumeCallback = new VolumePreferenceCallback();

    private boolean originRingtoneIsMute = false;
    private boolean positiveResult = false;
    private boolean ringtoneHasBeenChanged = false;
    
    

    private SeekBarVolumizer createSeekBarVolumizer(SeekBar seekBar,
            final int streamType, final Callback callback) {

        SeekBarVolumizer.Callback cb = new SeekBarVolumizer.Callback() {
            @Override
            public void onSampleStarting(SeekBarVolumizer sbv) {
                if (callback != null)
                    callback.onSampleStarting(sbv);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromTouch) {
            }

            @Override
            public void onMuted(boolean muted) {
            }
        };

        final Uri sampleUri = (streamType == AudioManager.STREAM_MUSIC) ? getMediaVolumeUri(this)
                : null;

        SeekBarVolumizer seekBarVolumizer = new SeekBarVolumizer(
                getApplicationContext(), streamType, sampleUri, cb);

        seekBarVolumizer.start();
        seekBarVolumizer.setSeekBar(seekBar);

        return seekBarVolumizer;
    }

    private Uri getMediaVolumeUri(Context ctx) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + ctx.getPackageName() + "/" + R.raw.media_volume);
    }

    private void updateSlidersAndMutedStates() {
        Log.e(TAG, "updateSlidersAndMutedStates");
        for (int i = 0; i < SEEKBAR_TYPE.length; i++) {
            if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT)
                    && ((SEEKBAR_TYPE[i] == AudioManager.STREAM_RING) || (SEEKBAR_TYPE[i] == AudioManager.STREAM_NOTIFICATION))) {
                originRingtoneIsMute = true;
            }

            if (mSeekBars[i] != null) {
                int streamVolume = mAudioManager
                        .getStreamVolume(SEEKBAR_TYPE[i]);
                Log.e(TAG, "streamVolume=" + streamVolume + ", i=" + i);
                if (i == 3) {
                    mSeekBars[i].setProgress(streamVolume + 1);
                } else {
                    mSeekBars[i].setProgress(streamVolume);
                }

            }
        }
        boolean isStreamMute = mAudioManager.isStreamMute(SEEKBAR_TYPE[0]);
        if (isStreamMute) {
        	ImageView voiceMute = (ImageView) mScrollView.findViewById(R.id.ringer_mute_button);
        	voiceMute.setBackground(getResources().getDrawable(R.drawable.ic_audio_ring_notif_mute));
		}else{
			ImageView voiceMute = (ImageView) mScrollView.findViewById(R.id.ringer_mute_button);
			voiceMute.setBackground(getResources().getDrawable(R.drawable.ic_audio_ring_notif));
		}
    }

    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        showDialog(1);
    }

    protected Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
        case 1:
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            mScrollView = (ScrollView) ((LayoutInflater) getSystemService("layout_inflater"))
                    .inflate(R.layout.preference_dialog_ringervolume_hq, null);
            builder.setView(mScrollView);
            builder.setTitle(R.string.all_volume_title);

            DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int which) {
                }
            };
            builder.setPositiveButton(R.string.volumn_dlg_dismiss, onClickListener);

            mSeekBarVolumizer = new SeekBarVolumizer[SEEKBAR_ID.length];
            mAudioManager = ((AudioManager) getApplicationContext()
                    .getSystemService("audio"));

            for (int j = 0; j < SEEKBAR_ID.length; j++) {
                mSeekBars[j] = (SeekBar) mScrollView.findViewById(SEEKBAR_ID[j]);
                mSeekBarVolumizer[j] = createSeekBarVolumizer(mSeekBars[j],
                        SEEKBAR_TYPE[j], mVolumeCallback);
            }

            updateSlidersAndMutedStates();

            if (mRingModeChangedReceiver == null) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
                intentFilter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
                mRingModeChangedReceiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (("android.media.RINGER_MODE_CHANGED".equals(action))
                                || ("android.media.VOLUME_CHANGED_ACTION".equals(action)))
                            mHandler.sendMessage(mHandler.obtainMessage(UPDATE_UI,
                                intent.getIntExtra("android.media.EXTRA_RINGER_MODE", -1), 0));
                    }
                };

                getApplicationContext().registerReceiver(
                        mRingModeChangedReceiver, intentFilter);
            }

            mDialog = builder.create();

            DialogInterface.OnDismissListener onDismissListener = new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dlg) {
                    if (!positiveResult && originRingtoneIsMute
                            && !ringtoneHasBeenChanged) {
                        mAudioManager.setRingerMode(0);
                    }

                    if (mRingModeChangedReceiver != null) {
                        getApplicationContext().unregisterReceiver(
                                mRingModeChangedReceiver);

                    }

                    finish();
                }
            };

            mDialog.setOnDismissListener(onDismissListener);
            mDialog.show();
            break;

        default:
            break;
        }
        return mDialog;
    }

    public void onPause() {
        super.onPause();
        finish();
        if ((mDialog != null) && (mDialog.isShowing()))
            mDialog.dismiss();
    }

    public void onResume() {
        super.onResume();
    }

    protected void onStop() {
        super.onStop();
        for (SeekBarVolumizer sbv : mSeekBarVolumizer){
			if (sbv != null){
				sbv.stop();
			}
        }
    }

    public static abstract interface Callback {
        public abstract void onSampleStarting(SeekBarVolumizer sbv);
    }

    private final class VolumePreferenceCallback implements Callback {
        private SeekBarVolumizer mCurrent;

        @Override
        public void onSampleStarting(SeekBarVolumizer sbv) {
            if (mCurrent != null && mCurrent != sbv) {
                mCurrent.stopSample();
            }
            mCurrent = sbv;
            if (mCurrent != null) {
                mHandler.removeMessages(H.STOP_SAMPLE);
                mHandler.sendEmptyMessageDelayed(H.STOP_SAMPLE, SAMPLE_CUTOFF);
            }
        }

        public void onStreamValueChanged(int stream, int progress) {
            if (stream == AudioManager.STREAM_RING) {
                mHandler.removeMessages(H.UPDATE_RINGER_ICON);
                mHandler.obtainMessage(H.UPDATE_RINGER_ICON, progress, 0)
                        .sendToTarget();
            }
        }

        public void stopSample() {
            if (mCurrent != null) {
                mCurrent.stopSample();
            }
        }

    }

    private static final int SAMPLE_CUTOFF = 20000;

	private ScrollView mScrollView;

    private final class H extends Handler {
        private static final int UPDATE_PHONE_RINGTONE = 1;
        private static final int UPDATE_NOTIFICATION_RINGTONE = 2;
        private static final int STOP_SAMPLE = 3;
        private static final int UPDATE_RINGER_ICON = 4;
        private static final int RINGTONE_CHANGE = 5;

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case STOP_SAMPLE:
                mVolumeCallback.stopSample();
                break;
            case RINGTONE_CHANGE:
                // Xlog.d(TAG, "Ringtone changed.");
                // mVolumeCallback.ringtoneChanged();
            }
            updateSlidersAndMutedStates();
        }
    }
}
