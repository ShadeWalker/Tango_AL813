package com.android.settings.sim;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;

import java.util.List;

public class CheckAndShowSimSettingsService extends Service {
    private static final int TIME_TRICK = 1000;
    private Handler mHandler = new Handler();
    private boolean isRunning = false;

    private Runnable mCheckSetupWizard = new Runnable() {

        @Override
        public void run() {
            boolean isInProvisioning = Settings.Global.getInt(getContentResolver(),
                        Settings.Global.DEVICE_PROVISIONED, 0) == 0;
            mHandler.removeCallbacks(mCheckSetupWizard);
            if (isInProvisioning) {
                mHandler.postDelayed(mCheckSetupWizard, TIME_TRICK);
            } else {
                /* Delay to start sim settings to avoid starting it before launcher */
                mHandler.postDelayed(mDelayStartSimSettings, TIME_TRICK);
            }
        }

    };

    private Runnable mDelayStartSimSettings = new Runnable() {

        @Override
        public void run() {
            mHandler.removeCallbacks(mDelayStartSimSettings);
            boolean isHuaweiLauncherRunning = false;
		    final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		    List<ActivityManager.RunningAppProcessInfo> processes = am
				    .getRunningAppProcesses();
		    final PackageManager pm = getPackageManager();
		    for (ActivityManager.RunningAppProcessInfo process : processes) {			
			    try {
                    android.util.Log.d("CheckAndShowSimSettingsService", "mDelayStartSimSettings process.processName = "
                            + process.processName);
				    ApplicationInfo ai = pm.getApplicationInfo(process.processName,
						    PackageManager.GET_UNINSTALLED_PACKAGES);
				    if (ai.uid == process.uid) {
					    if ("com.huawei.android.launcher".equals(ai.packageName)) {
						    isHuaweiLauncherRunning = true;
                            break;
					    }
				    }
			    } catch (PackageManager.NameNotFoundException e) {
			    }
		    }

            if (isHuaweiLauncherRunning) {
                Intent simSetting = new Intent(Intent.ACTION_MAIN);
                simSetting.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                simSetting.setAction("com.android.settings.sim.SIM_SUB_INFO_SETTINGS");
                startActivity(simSetting);
                stopSelf();
            } else {
                mHandler.postDelayed(mDelayStartSimSettings, TIME_TRICK);
            }
        }

    };


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d("CheckAndShowSimSettingsService","onStartCommand intent = " + intent
                + ", isRunning = " + isRunning);
        if (intent != null && !isRunning) {
            isRunning = true;
            mHandler.removeCallbacks(mCheckSetupWizard);
            mHandler.postDelayed(mCheckSetupWizard, TIME_TRICK);
        }
        return Service.START_STICKY;
    }
}

