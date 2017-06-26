package com.mediatek.settings.ext;

import android.os.SystemProperties;


public class FeatureOption {
    public static final boolean MTK_GEMINI_SUPPORT = getValue("ro.mtk_gemini_support");
    
    /* get the key's value*/
    private static boolean getValue(String key) {
    	return SystemProperties.get(key).equals("1");
    }
}
