package com.mediatek.galleryfeature.config;

import java.io.File;

import android.content.Context;
import android.os.Environment;
import android.os.SystemProperties;

import com.mediatek.bluetooth.ConfigHelper;
import com.mediatek.bluetooth.ProfileConfig;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.Utils;

public class FeatureConfig {
    private static final String TAG = "MtkGallery2/FeatureConfig";

    public static final String DRM_PROPERTY = "ro.mtk_oma_drm_support";
    public static final String LIVEPHOTO_PROPERTY = "ro.mtk_live_photo_support";
    public static final String HOTKNOT_PROPERTY = "ro.mtk_hotknot_support";
    public static final String SUBTITLE_PROPERTY = "ro.mtk_subtitle_support";
    public static final String AUDIO_CHANGE_PROPERTY = "ro.mtk_audio_change_support";
    public static final String MAV_SUPPORT_PROPERTY = "ro.mtk_cam_mav_support";
    public static final String MTK_GMO_RAM_OPTIMIZE = "ro.mtk_gmo_ram_optimize";
    public static final String SLOWMOTION_SUPPORT_PROPERTY = "ro.mtk_slow_motion_support";
    public static final String THUMBNAIL_PLAY_PROPERTY = "ro.mtk_thumbnail_play_support";
    public static final String VIDEO_HEVC_SUPPORT_PROPERTY = "";
    public static final String MOTION_TRACK_SUPPORT_PROPERTY = "ro.mtk_motion_track_support";
    public static final String VIDEO_2K_SUPPORT_PROPERTY = "ro.mtk.video.4kh264_support";
    public static final String IMAGE_REFOCUS_SUPPORT_PROPERTY = "ro.mtk_cam_img_refocus_support";

    public static final int CPU_CORES_NUM = Runtime.getRuntime()
            .availableProcessors();

    // Whether resource consuming MTK new features should be enabled
    public static final boolean supportHeavyFeature = (CPU_CORES_NUM >= 4);

    public static final boolean supportPanorama3D = true && supportHeavyFeature;

    public static final boolean supportConShotsImages = true && supportHeavyFeature;

    public static final boolean supportMotionTrack = SystemProperties.get(
            MOTION_TRACK_SUPPORT_PROPERTY).equals("1")
            && supportHeavyFeature;

    public static final boolean supportLivePhoto = SystemProperties.get(
            LIVEPHOTO_PROPERTY).equals("1")
            && supportHeavyFeature;

    public static final boolean supportVideoThumbnailPlay = SystemProperties
            .get(THUMBNAIL_PLAY_PROPERTY).equals("1") && supportHeavyFeature;

    public static final boolean supportThumbnailMAV = SystemProperties.get(
            MAV_SUPPORT_PROPERTY).equals("1")
            && supportHeavyFeature;

    public static final boolean supportFancyHomePage = true;

    public static final boolean supportBluetoothPrint = ConfigHelper.checkSupportedProfiles(ProfileConfig.PROFILE_ID_BPP);

    public static final boolean supportMtkBeamPlus = SystemProperties.get(
            "ro.mtk_beam_plus_support").equals("1");

    // DRM (Digital Rights management) is developed by MediaTek.
    // Gallery3d avails MtkPlugin via android DRM framework to manage
    // digital rights of videos and images
    public static final boolean supportDrm = SystemProperties.get(DRM_PROPERTY)
            .equals("1");

    // MPO (Multi-Picture Object) is series of 3D features developed by
    // MediaTek. Camera can shot MAV file or stereo image. Gallery is
    // responsible to list all mpo files add call corresponding module
    // to playback them.
    public static final boolean supportMpo = SystemProperties.get(
            MAV_SUPPORT_PROPERTY).equals("1");

    // HEVC(Multi-Picture Object) is new video codec supported by
    // MediaTek. hevc can encode and decode. Gallery is
    // responsible to support hevc Trim/Mute
    // L default enabled this feature
    public static final boolean supportHevc = true; //SystemProperties.get(
            //VIDEO_HEVC_SUPPORT_PROPERTY).equals("1");

    public static final boolean supportSlowMotion = SystemProperties.get(
            SLOWMOTION_SUPPORT_PROPERTY).equals("1");

    public static final boolean support2kVideo = SystemProperties.get(
            VIDEO_2K_SUPPORT_PROPERTY).equals("1");

    public static final boolean supportHotKnot = SystemProperties.get(
            HOTKNOT_PROPERTY).equals("1");

    public static final boolean supportEmulator = SystemProperties.get(
            "ro.kernel.qemu").equals("1");

    public static final boolean isTablet = SystemProperties.get(
            "ro.build.characteristics").equals("tablet");

    public static boolean supportSlideVideoPlay = SystemProperties.get(
            "ro.mtk_slidevideo_support").equals("1")
            && (new File(Environment.getExternalStorageDirectory(),
                    "SUPPORTSVP")).exists() && !isTablet;

    public static final boolean supportMAV = SystemProperties.get(
            MAV_SUPPORT_PROPERTY).equals("1");

    public static final boolean supportAudioChange = SystemProperties.get(
            AUDIO_CHANGE_PROPERTY).equals("1");

    public static final boolean supportSubtitle = SystemProperties.get(
            SUBTITLE_PROPERTY).equals("1");

    public static final boolean supportPQ = (new File(Environment
            .getExternalStorageDirectory(), "SUPPORT_PQ")).exists();

    public static final boolean isGmoRamOptimize = SystemProperties.get(
            MTK_GMO_RAM_OPTIMIZE).equals("1");
    public static volatile boolean isLowRamDevice;

    //Picture quality enhancement feature avails Camera ISP hardware
    //to improve image quality displayed on the screen.
    public static final boolean supportPictureQualityEnhance = true;

    public static boolean supportImageDCEnhance = (new File(Environment
            .getExternalStorageDirectory(), "SUPPORT_DC")).exists() && SystemProperties.get(
            "ro.mtk_miravision_image_dc").equals("1");

    public static boolean isSupportClearMotion(Context context) {
        return Utils.isFileExist(context, "SUPPORT_CLEARMOTION");
    }

    public static boolean supportRefocus = false;

    private static void updateRefocusFeatureOption() {
        if (SystemProperties.get(IMAGE_REFOCUS_SUPPORT_PROPERTY).equals("1")) {
            supportRefocus = true;
        } else {
            File file = new File(Environment.getExternalStorageDirectory(),
                    "SUPPORTREFOCUS");
            if (file.exists()) supportRefocus = true;
        }
    }

    static {
        updateRefocusFeatureOption();
        MtkLog.i(TAG, "CPU_CORES_NUM = " + CPU_CORES_NUM);
        MtkLog.i(TAG, "supportRefocus = " + supportRefocus);
        MtkLog.i(TAG, "supportHeavyFeature = " + supportHeavyFeature);
        MtkLog.i(TAG, "supportPanorama3D = " + supportPanorama3D);
        MtkLog.i(TAG, "supportConShotsImages = " + supportConShotsImages);
        MtkLog.i(TAG, "supportMotionTrack = " + supportMotionTrack);
        MtkLog.i(TAG, "supportLivePhoto = " + supportLivePhoto);
        MtkLog.i(TAG, "supportThumbnailMAV = " + supportThumbnailMAV);
        MtkLog.i(TAG, "supportFancyHomePage = " + supportFancyHomePage);
        MtkLog.i(TAG, "supportBluetoothPrint = " + supportBluetoothPrint);
        MtkLog.i(TAG, "supportMtkBeamPlus = " + supportMtkBeamPlus);
        MtkLog.i(TAG, "supportDrm = " + supportDrm);
        MtkLog.i(TAG, "supportMpo = " + supportMpo);
        MtkLog.i(TAG, "supportHevc = " + supportHevc);
        MtkLog.i(TAG, "supportSlowMotion = " + supportSlowMotion);
        MtkLog.i(TAG, "support2kVideo = " + support2kVideo);
        MtkLog.i(TAG, "supportHotKnot = " + supportHotKnot);
        MtkLog.i(TAG, "supportEmulator = " + supportEmulator);
        MtkLog.i(TAG, "supportSlideVideoPlay = " + supportSlideVideoPlay);
        MtkLog.i(TAG, "supportMAV = " + supportMAV);
        MtkLog.i(TAG, "supportAudioChange = " + supportAudioChange);
        MtkLog.i(TAG, "supportSubtitle = " + supportSubtitle);
        MtkLog.i(TAG, "supportPQ = " + supportPQ);
        MtkLog.i(TAG, "isGmoRamOptimize = " + isGmoRamOptimize);
        MtkLog.i(TAG, "isLowRamDevice = " + isLowRamDevice);
        MtkLog.i(TAG, "supportImageDCEnhance = " + supportImageDCEnhance);
    }
}
