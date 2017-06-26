package com.mediatek.camera.v2.addition.asd;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;

import com.mediatek.camera.v2.addition.IAdditionCaptureObserver;
import com.mediatek.camera.v2.addition.IAdditionManager.IAdditionListener;
import com.mediatek.camera.v2.module.ModuleListener;
import com.mediatek.camera.v2.module.ModuleListener.CaptureType;
import com.mediatek.camera.v2.module.ModuleListener.RequestType;
import com.mediatek.camera.v2.setting.ISettingServant;
import com.mediatek.camera.v2.setting.SettingConvertor;
import com.mediatek.camera.v2.util.SettingKeys;
import com.mediatek.camera.v2.vendortag.TagMetadata;
import com.mediatek.camera.v2.vendortag.TagRequest;
import com.mediatek.camera.v2.vendortag.TagResult;

import java.util.List;

public class AsdDeviceImpl implements IAsdDevice {

    private static final String TAG = "CAM_APP/AsdDeviceImpl";
    private static final boolean DEBUG = true;

    private boolean mIsAsdOpened = false;
    private boolean mIsHdrMode = false;

    private IAdditionListener mAdditionListener;
    private AsdCaptureObserver mAsdCaptureObserver = new AsdCaptureObserver();
    private ISettingServant   mSettingServant;
    private IAsdAdditionCallback mAsdAddtionCallback;
    private String mDetectedScene;

    public AsdDeviceImpl(
            IAdditionListener additionListener, ISettingServant settingServant, IAsdAdditionCallback callback) {
        Log.i(TAG, "[AsdDeviceImpl]");
        mAdditionListener = additionListener;
        mSettingServant = settingServant;
        mAsdAddtionCallback = callback;
    }

    @Override
    public void startAsd(boolean isHdrMode) {
        Log.i(TAG, "[startAsd] isHdrMode = " + isHdrMode);
        mIsHdrMode = isHdrMode;
        mIsAsdOpened = true;
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
    }

    @Override
    public void stopAsd() {
        Log.i(TAG, "[stopAsd]...");
        mIsAsdOpened = false;
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
        mAsdAddtionCallback.onAsdClosed();
    }

    public IAdditionCaptureObserver getCaptureObserver() {
        return mAsdCaptureObserver;
    }

    private class AsdCaptureObserver implements IAdditionCaptureObserver {

        @Override
        public void configuringRequests(CaptureRequest.Builder requestBuilder, RequestType requestType) {
            if (DEBUG) {
                Log.i(TAG, "[configuringRequests] mIsAsdStarted = " + mIsAsdOpened
                        + ",mIsHdrMode = " + mIsHdrMode);
            }
            int value = TagMetadata.MTK_FACE_FEATURE_ASD_MODE_OFF;
            if (mIsAsdOpened) {
                if (mIsHdrMode) {
                    value = TagMetadata.MTK_FACE_FEATURE_ASD_MODE_FULL;
                } else {
                    value = TagMetadata.MTK_FACE_FEATURE_ASD_MODE_SIMPLE;
                }
            }
            
            // Current need open FD ,because native not have finished just AP
            // call start ASD TODO
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            requestBuilder.set(TagRequest.STATISTICS_ASD_MODE, value);

        }

        @Override
        public void onCaptureStarted(CaptureRequest request, long timestamp, long frameNumber) {

        }

        @Override
        public void onCaptureCompleted(CaptureRequest request, TotalCaptureResult result) {
            int[] mode = result.get(TagResult.STATISTICS_ASD_RESULT);
            Log.i(TAG, "[onCaptureCompleted] request asd = " + request.get(TagRequest.STATISTICS_ASD_MODE));
            if (mode != null) {
                for (int i = 0; i < mode.length; i++) {
                    Log.d(TAG, "[onCaptureCompleted] mode[" + i + " ]= " + mode[i]);
                }
                
                if (mSettingServant != null) {
                    String scene = null;
                    if (mode[0] == 32) {
                        // index value is 32 means asd detected the scene of 
                        // backlight-portrait(just for mtk platform).
                        scene = "backlight-portrait";
                    } else {
                        scene = SettingConvertor.convertSceneEnumToString(mode[0]);
                    }
                    
                    Log.i(TAG, "onAsdDetected, scene:" + scene);
                    if (scene != null && !scene.equals(mDetectedScene)) {
                        List<String> supportedScene = mSettingServant
                                .getSupportedValues(SettingKeys.KEY_SCENE_MODE);
                        mDetectedScene = scene;
                        mAsdAddtionCallback.onAsdDetectedScene(scene);
                        // if scenes supported by current camera is null or the detected scene is not 
                        // in supported scenes, force set the detected scene to auto.
                        if (supportedScene == null || !supportedScene.contains(scene)) {
                            scene = SettingConvertor.SceneMode.AUTO.toString().toLowerCase();
                        }
                        mSettingServant.doSettingChange(SettingKeys.KEY_SCENE_MODE, scene, false);
                    }
                    
                }
            }
        }
    }
}
