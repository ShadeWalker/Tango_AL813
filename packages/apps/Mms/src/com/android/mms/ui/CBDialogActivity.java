package com.android.mms.ui;

import java.util.List;

import com.android.mms.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
//import com.mediatek.telephony.SimInfoManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.content.Context;
import android.net.Uri;
import android.content.res.Resources;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.view.Window;
import android.view.WindowManager;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import android.content.DialogInterface;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.app.StatusBarManager;

public class CBDialogActivity extends AlertActivity implements DialogInterface.OnClickListener {
	public static final String CB_MESSAGE_ACTION = "android.intent.action.SHOW";
    public static final String CB_MESSAGE_VALUE = "cb_message_txt";
    public static final String CB_MESSAGE_SIM = "cb_message_sim";
    public static final String TAG = "CBDialogActivity";
    private static final float IN_CALL_VOLUME = 0.125f;
    private static final float IN_CALL_VOLUME_MAX = 1f;
    
    private Button okbtn;
	private Intent mIntent;
	private String mCBMessage;
	//private TextView mTextView;
	private int mCallStateLast = 0;
	private int mCallStateCur = 0;
	private int simId;
    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;
    private Vibrator vibrator; 
    private Context mContext;
    private boolean mPlaying = false;
    private WakeLock mWakelock = null;	
    private TelephonyManager mTelephonyManager;
    /** The status bar where back/home/recent buttons are shown. */
    private StatusBarManager mStatusBar;

    /** All the widgets to disable in the status bar */
    final private static int sWidgetsToDisable = StatusBarManager.DISABLE_EXPAND
			| StatusBarManager.DISABLE_SYSTEM_INFO
            | StatusBarManager.DISABLE_HOME
            | StatusBarManager.DISABLE_BACK
            | StatusBarManager.DISABLE_SEARCH
            | StatusBarManager.DISABLE_RECENT;
    
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
        	mCallStateLast = mCallStateCur;
			mCallStateCur = state;
            // The user might already be in a call when the alarm fires. When
            // we register onCallStateChanged, we get the initial in-call state
            // which kills the alarm. Check against the initial call state so
            // we don't kill the alarm during a call.
            if (mCallStateLast != TelephonyManager.CALL_STATE_IDLE 
				&& state == TelephonyManager.CALL_STATE_IDLE) {
				if( mPlaying && mMediaPlayer != null && mMediaPlayer.isPlaying()){
					mMediaPlayer.setVolume(IN_CALL_VOLUME_MAX);
				}
				if( mPlaying && vibrator != null){  
          			 vibrator.cancel();  
				}
                
            }
        }
    };
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.e(TAG, "onCreate");
		setFinishOnTouchOutside(false);
		mTelephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakelock = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.SCREEN_DIM_WAKE_LOCK, "SimpleTimer");
        	mWakelock.acquire();

		final Window win = getWindow();
  		win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
  				| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
  		win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
  			| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		mIntent=getIntent();
		if(mIntent!=null && mIntent.getAction()!=null && mIntent.getAction().equals(CB_MESSAGE_ACTION)){
			mCBMessage=mIntent.getStringExtra(CB_MESSAGE_VALUE);
			simId=mIntent.getIntExtra(CB_MESSAGE_SIM, -1);
		}else{
			finish();
		}
		if(mCBMessage==null || TextUtils.isEmpty(mCBMessage)){
			finish();
		}
		
		//setContentView(R.layout.cbinfo);
		//mTextView=(TextView)this.findViewById(R.id.text);
		
		//Log.e(TAG, "onCreate "+simId+": "+mCBMessage);
		
		//mTextView.setText(mCBMessage);
		//okbtn=(Button)this.findViewById(R.id.button_ok);
		//okbtn.setOnClickListener(this);
		Uri ringtone = Uri.parse("android.resource://" + this.getPackageName() + "/" + R.raw.attention_signal);
		mMediaPlayer = new MediaPlayer();
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		Log.e(TAG,"mMediaPlayer " + mMediaPlayer);
		Log.e(TAG, "ringtone: "+ringtone.toString());
        	mMediaPlayer.setOnErrorListener(new OnErrorListener() {
		    	public boolean onError(MediaPlayer mp, int what, int extra) {
				Log.e(TAG,"Error occurred while playing audio.");
				mp.stop();
				mp.release();
				mMediaPlayer = null;
				if (mAudioManager != null) {
				    mAudioManager.abandonAudioFocus(audioListener);
				}
				return true;
		    	}
        	});
		Log.e(TAG,"mMediaPlayer " + mMediaPlayer);
		try{ 
			//setDataSourceFromResource(getResources(), mMediaPlayer,R.raw.attention_signal);
			if (mMediaPlayer != null) {
			    Log.e(TAG,"start Notificaiton");
				if( mTelephonyManager.getCallState()
		                 == TelephonyManager.CALL_STATE_RINGING ){//ring,need vibrate and ringtone as normal
				}else if( mTelephonyManager.getCallState()
		                 != TelephonyManager.CALL_STATE_IDLE ){//druing a call need vibrate and ringtone but volume is low
					mMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
				}
			    mMediaPlayer.setDataSource(this, ringtone);
		        startNotificaiton(mMediaPlayer,!mMediaPlayer.isPlaying());
		        }
		} catch (IOException ex) {
			Log.e(TAG,"ringtone Resource get fail.");
		}
        mStatusBar = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        mStatusBar.disable(sWidgetsToDisable);
		mWakelock.release();
	}
    public void onClick(DialogInterface dialogInterface, int button) {
        Log.d(TAG, "onClick");
        if (button == DialogInterface.BUTTON_POSITIVE) {
			if (mPlaying) {
			    mPlaying = false;

			    // Stop audio playing
			    if (mMediaPlayer != null) {
				mMediaPlayer.stop();
				mMediaPlayer.release();
				mMediaPlayer = null;
			    }
				if(vibrator != null){  
          			 vibrator.cancel();  
				}
			} 
			this.finish();
            return;
        }
    }
 
	private void showMessageDialog( ){
        final AlertController.AlertParams p = mAlertParams;
		p.mCancelable = false;
        p.mTitle = getString(R.string.cell_broadcast_dialog_title);
        p.mView = createView();
        p.mPositiveButtonText = getString(R.string.OK);
        p.mPositiveButtonListener = this;
        setupAlert();		
	}

	private View createView(){
        View view = getLayoutInflater().inflate(R.layout.cbinfo, null);
        TextView mMessageView = (TextView) view.findViewById(R.id.message_text);
		
		/*if(simId != -1){
			mMessageView.setText(getSimInfoNameBySimId(this,simId)+": "+mCBMessage);
		}else{
			mMessageView.setText(mCBMessage);
		}*/
		
		mMessageView.setText(mCBMessage);
        //TextView mDateTimeView = (TextView) view.findViewById(R.id.date_view);
        //mDateTimeView.setText( MessageUtils.formatTimeStampStringWithYMD( this,mDateTime ) );//change the time to string
		
        return view;
		
	}
	
	private AudioManager.OnAudioFocusChangeListener audioListener = new AudioManager.OnAudioFocusChangeListener() {
		public void onAudioFocusChange(int focusChange) {
		    Log.e(TAG,"OnAudioFocusChangeListener: focusChange= " + focusChange);
		    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
		        Log.e(TAG,"audio focus gain ...");
		        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
		            mMediaPlayer.start();
		        }
		    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
		            || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
		        Log.e(TAG,"audio focus loss ...");
		        if (mAudioManager != null) {
		            mAudioManager.abandonAudioFocus(audioListener);
		        }
			if(mMediaPlayer != null){
				mMediaPlayer.stop();
			}
		    }
		}
    	};
	private void startNotificaiton(MediaPlayer player, boolean silent)
            throws java.io.IOException, IllegalArgumentException,
                   IllegalStateException {
		Log.e(TAG,"Notificaiton: silent= " + silent);
		showMessageDialog();
		/// M: modified for controlling music play when alarm coming @{
		int result = mAudioManager.requestAudioFocus(audioListener, AudioManager.STREAM_MUSIC,
		        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
		// do not play alarms if stream volume is 0
		// (typically because ringer mode is silent).
		boolean isVolumeOk = mAudioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) != 0;
		/*if (silent) {
		    mAudioManager.abandonAudioFocus(audioListener);
		    return;
		}*/
		Log.e(TAG,"volume is not silent : " + isVolumeOk);
		/// @}
		Log.e(TAG,"startNotificaiton: result " + result);
		//if (isVolumeOk && result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
		if (true) {
		    player.setAudioStreamType(AudioManager.STREAM_ALARM);
		    player.setLooping(true);
		    player.prepare();
		    player.start();
		}
		vibrator = (Vibrator)this.getSystemService(this.VIBRATOR_SERVICE);   
		vibrator.vibrate(new long[]{1000, 3000}, 0);
		mPlaying = true;
    	}
    	private void setDataSourceFromResource(Resources resources,	
            MediaPlayer player, int res) throws java.io.IOException {
		AssetFileDescriptor afd = resources.openRawResourceFd(res);
		if (afd != null) {
		    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
		            afd.getLength());
		    afd.close();
		}
	}

	public void onPause() {
		super.onPause();
        mStatusBar.disable(StatusBarManager.DISABLE_NONE);
	}

    @Override
    protected void onStop() {
	Log.e(TAG,"onStop(): mPlaying " + mPlaying);
        super.onStop();
	if (mPlaying) {
            //mPlaying = false;

            // Stop audio playing
            /*if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }*/
	    if(vibrator != null){  
           	vibrator.cancel();  
            }
        }
	//this.finish();
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }else if(keyCode == KeyEvent.KEYCODE_HOME){
			return true;
		}
        return super.onKeyDown(keyCode, event);
    }
 	/*@Override  
   	public void onAttachedToWindow() {  
        this.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD);  
        super.onAttachedToWindow();  
   	} */
    @Override
    public void onDestroy() {
		super.onDestroy();
        // Stop listening for incoming calls.
        mTelephonyManager.listen(mPhoneStateListener, 0);
    }
}

