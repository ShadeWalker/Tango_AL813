package com.mediatek.dialer.util;

import android.os.SystemProperties;

public class DialerFeatureOptions {
    // [Union Query] this feature will make a union query on Calls table and data view
    // while query the call log. So that the query result would contain contacts info.
    // and no need to query contacts info again in CallLogAdapter. It improve the call log performance.
    public static final boolean CALL_LOG_UNION_QUERY = true;

    // [Ip-Prefix] Ip call prefix. This feature depend on "Union Query"
    public static final boolean IP_PREFIX = true;

    // [Multi-Delete] Support delete the multi-selected call logs
    public static final boolean MULTI_DELETE = true;

    // [CallLog Search] Support search call log from quick search box.
    public static final boolean CALL_LOG_SEARCH = true;

    // [Call Account Notification] Show a notification to indicator the available call accounts
    // and the selected call account. And allow the user to select the default call account.
    public static final boolean CALL_ACCOUNT_NOTIFICATION = true;

    // [Call Log Account Filter] when enabled, allow user to filter out call logs from specific account
    public static final boolean CALL_LOG_ACCOUNT_FILTER = true;
    /**
     * [DialerSearch] whether DialerSearch feature enabled on this device
     * @return ture if allowed to enable
     */
    public static boolean isDialerSearchEnabled() {
        return SystemProperties.get("ro.mtk_dialer_search_support").equals("1");
    }

    /**
     * [MTK_SINGLE_IMEI] whether MTK_SINGLE_IMEI feature enabled on this device
     * @return ture if allowed to enable
     */
    public static boolean isSigleImeiEnabled() {
        return SystemProperties.get("ro.mtk_single_imei").equals("1");
    }

    /** M: [ALPS01791893] check if using mtk audio profile @{ */
    public static boolean isMTKAudioProfileEnabled() {
        return SystemProperties.get("ro.mtk_audio_profiles").equals("1");
    }
    /** @} */

    /**
     * [CallLog I/O Filter] Whether the calllog incoming and outgoing filter supported
     * @return true if support the callog incoming and outgoing filter feature
     */
    public static boolean isCallLogIOFilterEnabled() {
        String operatorSpec = SystemProperties.get("ro.operator.optr", "");
        // Return true on OP09 or OP02 mode
        if (operatorSpec.equals("OP02") || operatorSpec.equals("OP09")) {
            return true;
        }
        return false;
    }

    public static final boolean MTK_IMS_SUPPORT = SystemProperties.get(
            "ro.mtk_ims_support").equals("1");
    public static final boolean MTK_VOLTE_SUPPORT = SystemProperties.get(
            "ro.mtk_volte_support").equals("1");
    public static final boolean MTK_ENHANCE_VOLTE_CONF_CALL = true;
    public static final boolean MTK_SUGGESTED_ACCOUNT = true;

    /**
     * [Suggested Account] Whether suggested account is supported
     * @return true if the suggested account was supported
     */
    public static boolean isSuggestedAccountSupport() {
        return MTK_SUGGESTED_ACCOUNT;
    }

    /**
     * [VoLTE ConfCall] Whether the VoLTE enhanced conference call supported
     * @return true if the VoLTE enhanced conference call supported
     */
    public static boolean isVolteEnhancedConfCallSupport() {
        return MTK_ENHANCE_VOLTE_CONF_CALL && MTK_IMS_SUPPORT && MTK_VOLTE_SUPPORT;
    }

    /**
     * [VoLTE] Whether the VoLTE call supported
     * @return true if the VoLTE call supported
     */
    public static boolean isVolteCallSupport() {
        return MTK_IMS_SUPPORT && MTK_VOLTE_SUPPORT;
    }

    /**
     * @return true if mtk app response time enhancement is enabled
     */
    public static boolean isPerfResponseTimeEnabled() {
        return SystemProperties.get("ro.mtk_perf_response_time").equals("1");
    }

    /**
     * @return true if mtk cdma 6m support
     */
    public static boolean isCDMA6MSupport() {
        return SystemProperties.get("ro.ct6m_support").equals("1");
    }

    /**
     * [C2K solution2] Whether the C2K solution2 supported
     * @return true if the C2K solution2 supported
     */
    public static boolean isC2KSolution2Support() {
        return SystemProperties.get("ro.mtk.c2k.slot2.support").equals("1");
    }

    /**
     * Whether the LTE is supported
     * @return true if the LTE is supported
     */
    public static boolean isLteSupport() {
        return SystemProperties.get("ro.mtk_lte_support").equals("1");
    }

    /**
     * @return true if mtk cta feature option is enabled
     */
    public static boolean isCtaSupported() {
        return SystemProperties.get("ro.mtk_cta_support").equals("1");
    }
}