package com.android.gestures;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.android.gestures.service.GestureService;
import com.android.gestures.service.GestureService.MainHandler;
import com.android.gestures.util.Utils;

public class GesturesBroadcastReceiver extends BroadcastReceiver{
	
	private static final String TAG = "GesturesBroadReceiver";
	private static final String ACTION_GESTURE = "com.android.action.gesture";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "on received broadcast!" + intent.getAction());
		if(intent != null && intent.getAction() != null && intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
			context.startService(new Intent(context,GestureService.class));
			Log.d(TAG, "start unlock service");
		}

		if(intent != null && intent.getAction() != null &&  intent.getAction().equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {
		}
	}
}
