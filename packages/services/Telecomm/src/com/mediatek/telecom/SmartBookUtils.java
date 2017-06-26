package com.mediatek.telecom;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.SystemProperties;
import android.util.Log;

public class SmartBookUtils {
    private final static String ONE = "1";
    private static final String LOG_TAG = "SmartBookUtils";

    public static boolean isMtkSmartBookSupport() {
        boolean isSupport = ONE.equals(SystemProperties.get("ro.mtk_smartbook_support")) ? true : false;
        Log.d(LOG_TAG, "isMtkSmartBookSupport(): " + isSupport);
        return isSupport;
    }

    public static boolean isMtkHdmiSupport() {
        boolean isSupport = ONE.equals(SystemProperties.get("ro.mtk_hdmi_support")) ? true : false;
        Log.d(LOG_TAG, "isMtkHdmiSupport(): " + isSupport);
        return isSupport;
    }

    public static void setActivityOrientation(Activity activity) {
        if (shouldSetOrientation()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Log.d(LOG_TAG, "setOrientationPortait Activity:" + activity);
        }
    }

    /**
     * This function to judge whether should set Activity to portrait.
     * Note: Here do not use isSmartBookPlugged() to judge, for when HDMI plugged should also set portrait.
     * Because Phone is portrait except tablet, so its safe to do this even smart book or HDMI is not plugged.
     * @return
     */
    public static boolean shouldSetOrientation() {
        boolean shouldSetOrientation = false;
        if (isMtkSmartBookSupport() || isMtkHdmiSupport()) {
            String ProductCharacteristic = SystemProperties.get("ro.build.characteristics");
            if (!"tablet".equals(ProductCharacteristic)) {
                shouldSetOrientation = true;
            }
        }
        Log.d(LOG_TAG, "shouldSetOrientation : " + shouldSetOrientation);
        return shouldSetOrientation;
    }
}