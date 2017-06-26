package com.android.emailcommon.utility;

import android.os.SystemProperties;

/**
 * M: Add FeatureOption class.
 */
public class FeatureOption {
    /** OMA drm support */
    public static final boolean MTK_DRM_APP =
            SystemProperties.get("ro.mtk_oma_drm_support").equals("1");

    /** cache merge support */
    public static final boolean MTK_CACHE_MERGE_SUPPORT =
            SystemProperties.get("ro.mtk_cache_merge_support").equals("1");

    public static final boolean MTK_EMMC_SUPPORT =
            SystemProperties.get("ro.mtk_emmc_support").equals("1");
}
