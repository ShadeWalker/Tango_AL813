package com.android.settings;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;

public class ClearLockScreen extends Activity {
	private final static String TAG = "ClearLockScreen";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!"com.android.settings.action.HW_RESET_NEW_PASSWORD"
				.equalsIgnoreCase(getIntent().getAction())) {
			Log.i(TAG, "forbid to enter ClearLockScreen!!");
			finish();
			return;
		}
		Log.i(TAG, "Entry ClearLockScreen onReceive");
		LockPatternUtils mLockPatternUtils = new LockPatternUtils(ClearLockScreen.this);
		mLockPatternUtils.clearLock(false);
		mLockPatternUtils.setLockScreenDisabled(false);
		Intent newIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
		ClearLockScreen.this.startActivity(newIntent);
		finish();
	}
}

