package com.mediatek.gallery3d.video;

import android.content.Context;
import android.os.SystemProperties;
import com.mediatek.galleryframework.util.MtkLog;

public class MtkVideoFeature {
    private static final String TAG = "Gallery2/MtkVideoFeature";

    private static final String MTK_GMO_RAM_OPTIMIZE = "ro.mtk_gmo_ram_optimize";
    private static final String MTK_SMARTBOOK = "ro.mtk_smartbook_support";
    private static final String MTK_MULTIWINDOW = "ro.mtk_multiwindow_support";
    private static final String MTK_SLOWMOTION = "ro.mtk_slow_motion_support";
    private static final String MTK_SLOWMOTION_16X = "ro.mtk_16x_slowmotion_support";
    private static final String MTK_HOTKNOT = "ro.mtk_hotknot_support";
    private static final String SUBTITLE_PROPERTY = "ro.mtk_subtitle_support";
    private static final String AUDIO_CHANGE_PROPERTY = "ro.mtk_audio_change_support";
    private static final String SUPPER_DIMMING = "ro.mtk_ultra_dimming_support";
    private static final String MTK_SUPPORT = "1";

    private static final boolean mIsGmoRamOptimize = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_GMO_RAM_OPTIMIZE));
    private static final boolean mIsSmartBookSupport = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_SMARTBOOK));
    private static final boolean mIsMultiWindowSupport = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_MULTIWINDOW));

    //added for slow motion
    private static final boolean supportSlowMotion = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_SLOWMOTION));
    //added for hotKnot
    private static final boolean supportHotKnot = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_HOTKNOT));
  //add for slow motion 16x
    private static final boolean supportSlowMotion16x = MTK_SUPPORT.equals(SystemProperties
            .get(MTK_SLOWMOTION_16X));

    private static final boolean mIsSubTitleSupport = MTK_SUPPORT.equals(SystemProperties
            .get(SUBTITLE_PROPERTY));

    private static final boolean mIsAudioChangeSupport = MTK_SUPPORT.equals(SystemProperties
            .get(AUDIO_CHANGE_PROPERTY));
    
    private static final boolean mIsSupperDimmingSupport = MTK_SUPPORT.equals(SystemProperties
            .get(SUPPER_DIMMING));

    public static boolean isForceAllVideoAsSlowMotion() {
       return (SystemProperties.getInt("slow_motion_debug", 0) == 2);
    }

    private static boolean isSlowMotionDebug() {
        return (SystemProperties.getInt("slow_motion_debug", 0) == 1);
    }

    private static int getSlowMotionUIDebugMode() {
        return SystemProperties.getInt("slow_motion_ui_debug", 0);
    }

    public static boolean isSlowMotionSupport() {
        MtkLog.i(TAG, "isSlowMotionSupport() return " + supportSlowMotion);
        if (/*isSlowMotionDebug() ||*/ isForceAllVideoAsSlowMotion()) {
            return true;
        }
        return supportSlowMotion;
    }

    public static boolean isRewindAndForwardSupport(Context context) {
        int debugMode = getSlowMotionUIDebugMode();
        if (debugMode == 1) { //force return true.
            return true;
        } else if (debugMode == 2) { //force return false.
            return false;
        }
        boolean support = ExtensionHelper.hasRewindAndForward(context);
        MtkLog.i(TAG, "isRewindAndForwardSupport() return " + support);
        return support;
    }

    public static boolean isSimulateWfd() {
        int support = SystemProperties.getInt("wfd_debug", 0);
        MtkLog.i(TAG, "isSimulateWfd() support " + support);
        return support == 1;
    }

    ///added for HotKnot @{
    public static boolean isHotKnotSupported() {
        MtkLog.i(TAG, "isHotKnotSupported() return " + supportHotKnot);
        return supportHotKnot;
    }
    /// @}

    public static boolean isSlowMotion16xSupported() {
        MtkLog.i(TAG, "isSlowMotion16xSupported() return " + supportSlowMotion16x);
        return supportSlowMotion16x;
    }

    // M: is ram optimize Enable
    public static boolean isGmoRAM() {
        boolean enabled = mIsGmoRamOptimize;
        MtkLog.i(TAG, "isGmoRAM() return " + enabled);
        return enabled;
    }

    public static boolean isEnableMultiWindow() {
        return mIsMultiWindowSupport;
    }

    public static boolean isGmoRamOptimize() {
        MtkLog.v(TAG, "isGmoRamOptimize() " + mIsGmoRamOptimize);
        return mIsGmoRamOptimize;
    }

    public static boolean isSmartBookSupport() {
        MtkLog.v(TAG, "isSmartBookSupport() " + mIsSmartBookSupport);
        return mIsSmartBookSupport;
    }

    public static boolean isSubTitleSupport() {
        MtkLog.v(TAG, "isSubTitleSupport() " + mIsSubTitleSupport);
        return mIsSubTitleSupport;
    }

    public static boolean isAudioChangeSupport() {
        MtkLog.v(TAG, "isAudioChangeSupport() " + mIsAudioChangeSupport);
        return mIsAudioChangeSupport;
    }
    
    public static boolean isSupperDimmingSupport() {
        MtkLog.v(TAG, "isSupperDimmingSupport() " + mIsSupperDimmingSupport);
        return mIsSupperDimmingSupport;
    }
}
