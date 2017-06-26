
package com.mediatek.incallui.wrapper;

import android.os.SystemProperties;

import com.mediatek.incallui.SmartBookUtils;


public class FeatureOptionWrapper {

    private static final String TAG = "FeatureOptionWrapper";
    private FeatureOptionWrapper() {
    }

    /**
     * @see FeatureOption.MTK_GEMINI_SUPPORT
     * @see FeatureOption.MTK_GEMINI_3SIM_SUPPORT
     * @see FeatureOption.MTK_GEMINI_4SIM_SUPPORT
     * @return true if the device has 2 or more slots
     */
    public static boolean isSupportGemini() {
        //return PhoneConstants.GEMINI_SIM_NUM >= 2;
        return true;
    }

    /**
     * @return MTK_PHONE_VOICE_RECORDING
     */
    public static boolean isSupportPhoneVoiceRecording() {
//        return com.mediatek.featureoption.FeatureOption.MTK_PHONE_VOICE_RECORDING;
        return true;
    }


/*    public static final boolean MTK_HDMI_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_HDMI_SUPPORT;*/
    public static final boolean MTK_SMARTBOOK_SUPPORT = SmartBookUtils.isMtkSmartBookSupport();
//            com.mediatek.common.featureoption.FeatureOption.MTK_SMARTBOOK_SUPPORT;
//    public static final boolean MTK_PHONE_VT_VOICE_ANSWER =
//            com.mediatek.common.featureoption.FeatureOption.MTK_PHONE_VT_VOICE_ANSWER;

//    public static boolean isSupportVTVoiceAnswer() {
//        boolean isSupport = com.mediatek.common.featureoption.FeatureOption.MTK_PHONE_VT_VOICE_ANSWER;
//        Log.d(TAG, "isSupportVTVoiceAnswer: " + isSupport);
//        return isSupport;
//    }
//
//    public static boolean isSupportDualTalk() {
//        boolean isSupportDualTalk = com.mediatek.common.featureoption.FeatureOption.MTK_DT_SUPPORT;
//        Log.d(TAG, "isSupportDualTalk: " + isSupportDualTalk);
//        return isSupportDualTalk;
//    }
//
    public static boolean isSupportPrivacyProtect() {
    //    boolean isSupportPrivacyProtect = com.mediatek.common.featureoption.FeatureOption.MTK_PRIVACY_PROTECTION_LOCK;
    //    return isSupportPrivacyProtect;
        return true;
    }

    /// M: for VoLTE Conference Call @{
    public static final boolean MTK_IMS_SUPPORT = SystemProperties.get("ro.mtk_ims_support")
            .equals("1");
    public static final boolean MTK_VOLTE_SUPPORT = SystemProperties.get("ro.mtk_volte_support")
            .equals("1");
    // local "feature option" to control add member function of VoLTE conference call.
    public static final boolean LOCAL_OPTION_ENABLE_ADD_MEMBER = true;
    /// @}

    /**
     * isCdma6mSupport() for CDMA 6M feature.
     * @return ro.ct6m_support
     */
    public static boolean isCdma6mSupport() {
        return "1".equals(SystemProperties.get("ro.ct6m_support"));
    }
}

