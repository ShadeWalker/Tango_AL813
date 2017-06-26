package com.mediatek.camera.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;


public class DefaultCameraFeatureExt extends ContextWrapper implements ICameraFeatureExt {
    private static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    protected Context mContext;
    public DefaultCameraFeatureExt(Context context) {
        super(context);
        mContext = context;
    }

    public int getQuickViewDisplayDuration() {
        return 0;
    }

    public void updateSettingItem(String key, ArrayList<CharSequence> entries, ArrayList<CharSequence> entryValues) {
        if (KEY_SCENE_MODE.equals(key)) {
            int index = 0;
            for (Iterator<CharSequence> iter = entryValues.iterator(); iter.hasNext();) {
                CharSequence value = String.valueOf(iter.next());
                if ("normal".equals(value)) {
                    iter.remove();
                    entries.remove(index);
                    break;
                }
                index++;
            }
        }
    }
    
    public void checkCamcorderProfile(int quality, CamcorderProfile profile) {
    }
    
    public void configRecorder(MediaRecorder recorder) {
    }
}
