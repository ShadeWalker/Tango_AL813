package com.mediatek.camera.v2.addition.asd;

import android.util.Log;

public class AsdAddition implements IAsdModeControl {
    

    private static final String TAG = "CAM_APP/AsdAddition";
    private static final boolean DEBUG = true;
    private boolean mIsAsdStarted = false;
    
    private IAsdDevice mIAsdDevice;
    
    public interface AsdDetectCallback {
        // Notice:when is not hdrMode,means open ASD use simple mode, the
        // hdrMode will be null
        public void onAsdDetected(int normalSceneMode, int hdrMode);
    }
    
    public AsdAddition(IAsdDevice asdDevice) {
        //do-noting
        mIAsdDevice = asdDevice;
    }
    
    @Override
    public void startAsd(boolean isHdrMode) {
        Log.i(TAG, "[startAsd] isHdrMode = " + isHdrMode + ",mIsAsdStarted = " + mIsAsdStarted);
        if (mIsAsdStarted) {
            Log.i(TAG, "is AsdStarted, call twice,so return");
            return;
        }
        mIAsdDevice.startAsd(isHdrMode);
        mIsAsdStarted = true;
    }

    @Override
    public void stopAsd() {
        Log.i(TAG, "[stopAsd] mIsAsdStarted = " + mIsAsdStarted);
        if (!mIsAsdStarted) {
            Log.i(TAG, "Asd not Started, why call stop,so return");
            return;
        }
        mIAsdDevice.stopAsd();
        mIsAsdStarted = false;
    }
}
