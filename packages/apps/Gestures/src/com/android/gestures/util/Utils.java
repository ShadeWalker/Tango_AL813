package com.android.gestures.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.gestures.R;

public class Utils {
	
	private static final String TAG = "Utils";
	private  MediaPlayer mediaPlayer;
	private static final String GESTURE_DISABLE = "com.android.gesture.switcher;com.android.gesture.switcher.GestureDisableActivity";
	
	private  AudioManager am;
	
	private static Utils mInstance = null;
	
	private MainHandler mHandler = new MainHandler();
	private static final int MSG_DELAY_RELEASE_AUDIO = 1;

	private class MainHandler extends Handler {
		public MainHandler() {
			super();
		}

		public void handleMessage(Message msg) {
			switch (msg.what) {

			case MSG_DELAY_RELEASE_AUDIO:
				releaseMedia();
				break;
			default:
				break;
			}
		}
	}
	 
	private Utils () {
		
	}
	
	public synchronized static Utils getInstance() {
		if (mInstance == null) {
			mInstance = new Utils();
		}
		return mInstance;
	}
	
	public void initMedia(Context context) {
		mediaPlayer = new MediaPlayer();
		try {
			AssetFileDescriptor fileDescriptor = context.getAssets().openFd("whisper.ogg");
			mediaPlayer.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(), fileDescriptor.getLength());
			
			am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
			mediaPlayer.setVolume(am.getStreamVolume(AudioManager.STREAM_SYSTEM), am.getStreamVolume(AudioManager.STREAM_SYSTEM));
			mediaPlayer.prepare();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void playMedia(Context context) {
//		mediaPlayer = MediaPlayer.create(context,R.raw.whisper); 
		if(mediaPlayer != null && !mediaPlayer.isPlaying()) {
			Log.d(TAG, "playMedia!!!");
			mediaPlayer.start();
		}
	}
	
	public void stopMedia() {
		if(mediaPlayer != null) {
			mediaPlayer.stop();
		}
	}
	
	public void releaseMediaDelay() {
		mHandler.sendEmptyMessageDelayed(MSG_DELAY_RELEASE_AUDIO, 600);
	}
	
	public void releaseMedia() {
		if(mediaPlayer != null) {
			Log.d(TAG, "releaseMedia!!!");
			mediaPlayer.release();
			mediaPlayer = null;
		}
	}
	
	public String getGetureDateBaseDefineByGetureType(int gestureType) {
		switch(gestureType) {
		case 'c':
			return "keylock_gestures_c_pkg";
		case 'e':
			return "keylock_gestures_e_pkg";
		case 'm':
			return "keylock_gestures_m_pkg";
		case 'w':
			return "keylock_gestures_w_pkg";
		case 'o':
			return "gesture_type_o";
		case 'v':
			return "gesture_type_v";
		case 's':
			return "gesture_type_s";
		case 'z':
			return "gesture_type_z";
		default:
			return "";
		}
	}
	
	public boolean isGestureTypeDisable(Context context, String gestureType) {
		if (Settings.System.getString(context.getContentResolver(),
				gestureType).equals(GESTURE_DISABLE)) {
			return true;
		}
		return false;
	}
	
	public boolean isExistPackageNames(Context context, String gestureType) {
		
		String compName = Settings.System.getString(context.getContentResolver(), gestureType);
		String[] comList = compName.split(";");
	    PackageManager mPackMgr = context.getPackageManager();
	    PackageInfo pkgInfo = null;
	    try {
	    	 pkgInfo = mPackMgr.getPackageInfo(comList[0], 0);
	    } catch (NameNotFoundException e) {
	    	e.printStackTrace();
	    }
	   
	    Log.d("guo","pkginfo: "+ pkgInfo);
	    if(pkgInfo == null || pkgInfo.packageName == null) {
	    	Settings.System.putString(context.getContentResolver(), gestureType, GESTURE_DISABLE);
	    	return false;
	    }
	    return true;
	}
	
	public void writeGestureEnable(String flag) {
		File file = new File("/proc/gesture_enable");
		FileOutputStream out = null;
		try {
			Log.d("guo", "is exise : " + file.exists()+", flag: "+flag);
			byte[] buf = flag.getBytes();
			if (!file.exists()) {
				file.createNewFile();
			}
			out = new FileOutputStream(file, false);
			out.write(buf);
		} catch (IOException e) {
			Log.d("guo", "write fail!!!");
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }
}
