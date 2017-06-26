package com.mediatek.camera.ext;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;

import java.util.ArrayList;

public interface ICameraFeatureExt {
    /**
     * get the duration of capture quick view.
     *
     * Returns the duration of capture quick view, unit is ms, 
     *         negtive or zero means on need to delay;
     */
    int getQuickViewDisplayDuration();

    /**
     * update the setting item, such as WB, Scene.
     *
     * @param key indicate the setting item which needs to be updated,
     * @param entries the display values of current setting item,
     * @param entryValues the logic values of current setting item.
     */
    void updateSettingItem(String key, ArrayList<CharSequence> entries, ArrayList<CharSequence> entryValues);

    /**
     * check the CamcorderProfile for video recording.
     *
     * @param quality index of CamcorderProfile for video recording,
     * @param profile  which need to check and change,
     */
    void checkCamcorderProfile(int quality, CamcorderProfile profile);
    /**
     * config video recorder.
     *
     * @param recorder the recorder need to config,
     */     
    void configRecorder(MediaRecorder recorder);
}
