package com.mediatek.systemui.ext;

import android.os.SystemProperties;

/**
 * M: The utilities class of feature option definitions.
 */
public class FeatureOptionUtils {

    // EVDO dual talk support system property
    public static final String EVDO_DT_SUPPORT = "ril.evdo.dtsupport";
    // SVLTE support system property
    public static final String MTK_SVLTE_SUPPORT = "ro.mtk_svlte_support";
    // IRAT support system property
    public static final String MTK_IRAT_SUPPORT = "ro.c2k.irat.support";
    // MD Based IR support
    public static final String MTK_MD_IRAT_SUPPORT = "ro.c2k.md.irat.support";
    // MTK C2K support
    public static final String MTK_C2K_SUPPORT = "ro.mtk_c2k_support";

    // OP01 RCS support
    public static final String MTK_OP01_RCS_SUPPORT = "ro.mtk_op01_rcs_support";

    /// M: Add for CT 6M. @ {
    public static final String MTK_CT6M_SUPPORT = "ro.ct6m_support";
    /// @ }

    // Feature support.
    public static final String SUPPORT_YES = "1";

    // Build Type
    public static final String BUILD_TYPE = "ro.build.type";
    public static final String BUILD_TYPE_ENG = "eng";
    public static final String BUILD_TYPE_USER = "user";

    /**
     * Check if CDMA LTE Dual connection(SVLTE) support is true.
     * @return true if SVLTE is enabled
     */
    public static final boolean isCdmaLteDcSupport() {
        return isSupport(MTK_SVLTE_SUPPORT);
    }

    /**
     * Check if CDMA AP IRAT feature is supported.
     * @return True if AP IRAT feature is supported.
     */
    public static final boolean isCdmaApIratSupport() {
        return isCdmaIratSupport() && !isCdmaMdIratSupport();
    }

    /**
     * Check if CDMA MD IRAT feature is supported.
     * @return True if MD IRAT feature is supported.
     */
    public static final boolean isCdmaMdIratSupport() {
        return isSupport(MTK_MD_IRAT_SUPPORT);
    }

    /**
     * Check if CDMA IRAT feature is supported.
     * @return True if C2K IRAT feature is supported.
     */
    public static final boolean isCdmaIratSupport() {
        return isSupport(MTK_IRAT_SUPPORT);
    }

    /**
     * Check if MTK C2K feature is supported.
     * @return True if MTK C2K feature is supported.
     */
    public static final boolean isMtkC2KSupport() {
        return isSupport(MTK_C2K_SUPPORT);
    }

    /**
     * Check if MTK_OP01_RCS_SUPPORT feature is supported.
     * @return True if MTK OP01 RCS feature is supported.
     */
    public static final boolean isOP01RcsSupport() {
        return isSupport(MTK_OP01_RCS_SUPPORT);
    }

    /**
     * Check if isMTK_CT6M_SUPPORT feature is supported.
     * @return True if isMTK_CT6M_SUPPORT feature is supported.
     */
    public static final boolean isMTK_CT6M_SUPPORT() {
        return isSupport(MTK_CT6M_SUPPORT);
    }

    /**
     * Whether is User Load.
     * @return True if is User Load.
     */
    public static final boolean isUserLoad() {
        return SystemProperties.get(BUILD_TYPE).equals(BUILD_TYPE_USER);
    }

    // Important!!! the SystemProperties key's length must less than 31 , or will have JE
    /* Whether is support the key's value */
    private static final boolean isSupport(String key) {
        return SystemProperties.get(key).equals(SUPPORT_YES);
    }
}