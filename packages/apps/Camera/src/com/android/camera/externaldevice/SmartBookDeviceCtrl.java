package com.android.camera.externaldevice;

import com.android.camera.CameraActivity;
import com.android.camera.FeatureSwitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.view.Window;
import android.view.WindowManager;

import com.mediatek.camera.util.Log;
import com.mediatek.hdmi.HdmiDef;
import com.mediatek.hdmi.IMtkHdmiManager;

public class SmartBookDeviceCtrl implements IExternalDeviceCtrl{

    private static final String TAG = "SmartBookDeviceCtrl";
    private static final String HDMI_SERVICE = "mtkhdmi";
    private static final int UNKNOWN = -1;
    private boolean mIsSmartBookPlugged = false;

    private int lastTimeOrientation = -1;

    private final BroadcastReceiver mSmartBookReceiver = new SmartBookBroadcastReceiver();
    
    private CameraActivity mCameraActivity;
    private IMtkHdmiManager mHdmiManager;
    private PowerManager mPowerManager;

    public SmartBookDeviceCtrl(CameraActivity cameraActivity) {
        Log.i(TAG, "[SmartBook] constractor" );
        mCameraActivity = cameraActivity;
    }

    
    @Override
    public boolean onCreate() {
        mPowerManager = (PowerManager) mCameraActivity.getSystemService(Context.POWER_SERVICE);
        //will use Context.HDMI_SERVICE replace
        //current codebase can not build pass framework
        mHdmiManager = IMtkHdmiManager.Stub.asInterface(ServiceManager.getService(HDMI_SERVICE));
        return false;
    }


    @Override
    public boolean onResume() {
        Log.i(TAG, "[onResume]");
        openSmartBook();
        return true;
    }


    @Override
    public boolean onPause() {
        Log.i(TAG, "[onPause]");
        closeSmartBook();
        return false;
    }


    @Override
    public boolean onDestory() {
        return false;
    }


    @Override
    public boolean onOrientationChanged(int orientation) {
        setCameraRequestOrientaion(orientation);
        return false;
    }


    @Override
    public void addListener(Object listenr) {
        
    }

    @Override
    public void removeListener(Object listenr) {
        
    }

    private void openSmartBook() {
        Log.d(TAG, "[openSmartBook]");
        IntentFilter mSmartBookIntentFilter = new IntentFilter();
        mSmartBookIntentFilter.addAction(Intent.ACTION_SMARTBOOK_PLUG);
        mCameraActivity.registerReceiver(mSmartBookReceiver,mSmartBookIntentFilter);
        screenOnSmartBook();
    }
    
    private void closeSmartBook() {
        Log.d(TAG, "[closeSmartBook]");
        mCameraActivity.unregisterReceiver(mSmartBookReceiver);
        screenOffForSmartBook();
    }

    private class SmartBookBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "SmartBookBR,action = " + action);

            if (Intent.ACTION_SMARTBOOK_PLUG.equals(action)) {
                mIsSmartBookPlugged = intent.getBooleanExtra(Intent.EXTRA_SMARTBOOK_PLUG_STATE,
                        false);
                if (mIsSmartBookPlugged) {
                    mCameraActivity.changeOrientationTag(false, 0);
                }
                Log.d(TAG, "SmartBookBR,state = " + mIsSmartBookPlugged);
            }
        }
    }

    private void screenOnSmartBook() {
        DisplayManager mDisplayManager = (DisplayManager) mCameraActivity
                .getSystemService(Context.DISPLAY_SERVICE);
        boolean isSmartBookPluggedIn = mDisplayManager.isSmartBookPluggedIn();
        Log.i(TAG, "screenOnSmartBook,FO is :" + FeatureSwitcher.isSmartBookEnabled()
                + ",isSmartBookPluggedIn = " + isSmartBookPluggedIn);
        
        try {
            if (isSmartBookPluggedIn && !mHdmiManager.hasCapability(HdmiDef.CAPABILITY_RDMA_LIMIT) && mPowerManager != null) {
                Log.i(TAG, "will set screen on SMB:wakeUpByReason");
                mPowerManager.wakeUpByReason(SystemClock.uptimeMillis(), PowerManager.WAKE_UP_REASON_SMARTBOOK);
            }
        } catch (Exception e) {
            Log.i(TAG, "screenOnSmartBook() error");
        }
    }

    private void screenOffForSmartBook() {
        Log.d(TAG, "[screenOffForSmartBook], mIsSmartBookPlugged = " + mIsSmartBookPlugged
                + ",mPowerManager = " + mPowerManager);
        if (mIsSmartBookPlugged && mPowerManager != null) {
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),PowerManager.GO_TO_SLEEP_REASON_SMARTBOOK,0);
        }
    }
    
    private void setCameraRequestOrientaion(int orientationRequest) {
      //  Log.d(TAG, "[setCameraRequestOrientaion]lastTimeOrientation = " + lastTimeOrientation
       //         + ",orientationRequest = " + orientationRequest + ",mIsSmartBookPlugged = "
      //          + mIsSmartBookPlugged);
        // current framework have set the default orientation is:landscape
        // so first time we need set the orientation not until the app guide
        // finished
        if (!mIsSmartBookPlugged) {
           // Log.d(TAG, "don't set the requestorientation");
            return;
        }
        if (lastTimeOrientation != orientationRequest) {
            lastTimeOrientation = orientationRequest;
        } else {
            return;
        }

        if (orientationRequest == UNKNOWN) {
            return;
        }
        switch (lastTimeOrientation) {
        case 0:
            Log.d(TAG, "set to SCREEN_ORIENTATION_PORTRAIT");
            mCameraActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            break;
        case 90:
            Log.d(TAG, "set to SCREEN_ORIENTATION_REVERSE_LANDSCAPE");
            mCameraActivity
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            break;
        case 180:
            Log.d(TAG, "set to SCREEN_ORIENTATION_REVERSE_PORTRAIT");
            mCameraActivity
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
            break;
        case 270:
            Log.d(TAG, "set to SCREEN_ORIENTATION_LANDSCAPE");
            mCameraActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            break;
        }
    }

}
