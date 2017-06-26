package com.android.gestures.service;

import com.goodix.gestures.jni.GesturesJni;
import com.android.gestures.GestureFullscreenActivity;
import com.android.gestures.GesturesBroadcastReceiver;
import com.android.gestures.util.Utils;

import android.app.KeyguardManager;
import android.app.Service;
import android.app.KeyguardManager.KeyguardLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class GestureService extends Service {
	
	private static final String TAG = "GesturesBroadReceiver";
	private static PowerManager pm;
	public static PowerManager.WakeLock screenON;
	private Vibrator vibrator;
	
	private static final String ACTION_GESTURE = "com.android.action.gesture";
	
	private GesturesBroadcastReceiver mGesturesBroadReceiver = new GesturesBroadcastReceiver();
	private GestureActionBroadcastReceiver mGestureActionBroadcastReceiver = new GestureActionBroadcastReceiver();
	
	private MainHandler mHandler = new MainHandler();
	private static final int MSG_DRAW_SURFACEVIEW = 1;
	
	public class MainHandler extends Handler {
		public MainHandler() {
			super();
		}

	        public void handleMessage(Message msg) {
	            switch (msg.what) {
				case MSG_DRAW_SURFACEVIEW:
					launcherSurfaceView();
					break;
				default:
					break;
				}
	        }
	    }
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.d(TAG, "GestureService:onBind()");
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub 
		super.onCreate();
		Log.d(TAG, "GestureService:onCreate()");
		
		pm = (PowerManager)this.getSystemService(GestureService.POWER_SERVICE);
		screenON = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK, "goodixgestrues_bright");
        screenON.setReferenceCounted(false);

		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        Utils.getInstance().initMedia(this);
        
        registerScreenActionReceiver();
		registerGestureActionReceiver();
	}
	
	private void registerScreenActionReceiver(){  
	    final IntentFilter filter = new IntentFilter();  
	    filter.addAction(Intent.ACTION_SCREEN_OFF);  
	    filter.addAction(Intent.ACTION_SCREEN_ON);
	    registerReceiver(mGesturesBroadReceiver, filter);  
	}
	
	private void registerGestureActionReceiver(){  
	    final IntentFilter filter = new IntentFilter();  
	    filter.addAction(ACTION_GESTURE);  
	    registerReceiver(mGestureActionBroadcastReceiver, filter);  
	}
	
	public void onDestroy(){
		Log.d(TAG, "gesture service Destory!");
		Utils.getInstance().releaseMediaDelay();
		screenON.release();
		if(mGestureActionBroadcastReceiver != null) {
			unregisterReceiver(mGestureActionBroadcastReceiver);
			mGestureActionBroadcastReceiver = null;
		}
		if(mGesturesBroadReceiver != null) {
			unregisterReceiver(mGesturesBroadReceiver);
			mGesturesBroadReceiver = null;
		}
		super.onDestroy();
	}

	
	@Override
	public void onLowMemory() {
		Log.d("guo","onLowMemory");
		super.onLowMemory();
	}

	@Override
	protected void finalize() throws Throwable { 
		Log.d("guo","finalize");
		super.finalize();
	}
	
	public void launcherSurfaceView() {
		Log.d(TAG,"launcherSurfaceView()");		
	    Log.d(TAG, "screen on & timer stop");
		Intent intent = new Intent(this, GestureFullscreenActivity.class);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
		return;
	}

	public static int Byte2Int(byte b) {
		int result;
		result = ((int)b)&0xFF;
		return result;
	}
	
	public class GestureActionBroadcastReceiver extends BroadcastReceiver {
		
		private static final String TAG = "GestureActionBroadcastReceiver";
		
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "on received broadcast!" + intent.getAction());
			if(intent != null && intent.getAction() != null &&  intent.getAction().equalsIgnoreCase(ACTION_GESTURE)) {
				mHandler.removeMessages(MSG_DRAW_SURFACEVIEW);
				mHandler.sendEmptyMessage(MSG_DRAW_SURFACEVIEW);
			}
		}
	}
	
}
