package com.mediatek.deskclock.utility;

import android.os.SystemProperties;

/**
 * M: Add FeatureOption class.
 */
public class FeatureOption {

    public static final boolean MTK_GEMINI_SUPPORT =
            SystemProperties.get("ro.mtk_gemini_support").equals("1");
}
