
package com.mediatek.settings;

import android.os.SystemProperties;
import android.telephony.TelephonyManager;

public class FeatureOption {
    public static final boolean MTK_GEMINI_SUPPORT = getValue("ro.mtk_gemini_support");
    public static final boolean MTK_GEMINI_3SIM_SUPPORT = TelephonyManager.getDefault().getPhoneCount() == 3;
    public static final boolean MTK_GEMINI_4SIM_SUPPORT = TelephonyManager.getDefault().getPhoneCount() == 4;
    public static final boolean MTK_VOICE_UNLOCK_SUPPORT = getValue("ro.mtk_voice_unlock_support");
    public static final boolean MTK_AUDIO_PROFILES = getValue("ro.mtk_audio_profiles");
    public static final boolean MTK_GMO_ROM_OPTIMIZE = getValue("ro.mtk_gmo_rom_optimize");
    public static final boolean PURE_AP_USE_EXTERNAL_MODEM = getValue("ro.pure_ap_use_external_modem");
    public static final boolean EVDO_DT_SUPPORT = getValue("ro.evdo_dt_support");

    public static final boolean MTK_C2K_SUPPORT = getValue("ro.mtk_c2k_support");
    public static final boolean MTK_SYSTEM_UPDATE_SUPPORT = getValue("ro.mtk_system_update_support");
    public static final boolean MTK_SCOMO_ENTRY = getValue("ro.mtk_scomo_entry");
    public static final boolean MTK_MDM_SCOMO = getValue("ro.mtk_mdm_scomo");
    public static final boolean MTK_FOTA_ENTRY = getValue("ro.mtk_fota_entry");
    public static final boolean MTK_MDM_FUMO = getValue("ro.mtk_mdm_fumo");
    public static final boolean MTK_DRM_APP = getValue("ro.mtk_oma_drm_support");
    public static final boolean MTK_EMMC_SUPPORT = getValue("ro.mtk_emmc_support");
    public static final boolean MTK_CACHE_MERGE_SUPPORT = getValue("ro.mtk_cache_merge_support");
    public static final boolean MTK_TETHERING_EEM_SUPPORT = getValue("ro.mtk_tethering_eem_support");
    public static final boolean MTK_TETHERINGIPV6_SUPPORT = getValue("ro.mtk_tetheringipv6_support");
    public static final boolean MTK_NFC_ADDON_SUPPORT = getValue("ro.mtk_nfc_addon_support");
    public static final boolean MTK_IPO_SUPPORT = getValue("ro.mtk_ipo_support");
    public static final boolean MTK_ONLY_OWNER_SIM_SUPPORT = getValue("ro.mtk_owner_sim_support");
    public static final boolean MTK_2SDCARD_SWAP = getValue("ro.mtk_2sdcard_swap");
    public static final boolean MTK_SHARED_SDCARD = getValue("ro.mtk_shared_sdcard");
    public static final boolean MTK_OWNER_SDCARD_ONLY_SUPPORT = getValue("ro.mtk_owner_sdcard_support");
    public static final boolean MTK_WFD_SUPPORT = getValue("ro.mtk_wfd_support");
    public static final boolean MTK_WLAN_SUPPORT = getValue("ro.mtk_wlan_support");
    public static final boolean MTK_GPS_SUPPORT = getValue("ro.mtk_gps_support");
    public static final boolean MTK_BT_SUPPORT = getValue("ro.mtk_bt_support");
    public static final boolean MTK_PASSPOINT_R1_SUPPORT = getValue("ro.mtk_passpoint_r1_support");
    public static final boolean MTK_DHCPV6C_WIFI = getValue("ro.mtk_dhcpv6c_wifi");
    public static final boolean MTK_EAP_SIM_AKA = getValue("ro.mtk_eap_sim_aka");
    public static final boolean MTK_WAPI_SUPPORT = getValue("ro.mtk_wapi_support");
    public static final boolean WIFI_WEP_KEY_ID_SET = getValue("ro.wifi_wep_key_id_set");
    public static final boolean MTK_AUDENH_SUPPORT = getValue("ro.mtk_audenh_support");
    public static final boolean MTK_MULTISIM_RINGTONE_SUPPORT = getValue("ro.mtk_multisim_ringtone");
    public static final boolean MTK_GEMINI_3G_SWITCH = getValue("ro.mtk_gemini_3g_switch");
    public static final boolean MTK_SMARTBOOK_SUPPORT = getValue("ro.mtk_smartbook_support");
    public static final boolean MTK_AGPS_APP = getValue("ro.mtk_agps_app");
    public static final boolean MTK_OMACP_SUPPORT = getValue("ro.mtk_omacp_support");
    public static final boolean MTK_BEAM_PLUS_SUPPORT = getValue("ro.mtk_beam_plus_support");
    public static final boolean MTK_CLEARMOTION_SUPPORT = getValue("ro.mtk_clearmotion_support");
    public static final boolean MTK_THEMEMANAGER_APP = getValue("ro.mtk_thememanager_app");
    public static final boolean MTK_POWER_SAVING_SWITCH_UI_SUPPORT = getValue("ro.mtk_pwr_save_switch");
    public static final boolean MTK_BG_POWER_SAVING_SUPPORT = getValue("ro.mtk_bg_power_saving_support");
    public static final boolean MTK_BG_POWER_SAVING_UI_SUPPORT = getValue("ro.mtk_bg_power_saving_ui");
    public static final boolean MTK_VOICE_UI_SUPPORT = getValue("ro.mtk_voice_ui_support");
    public static final boolean MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT = getValue("ro.mtk_multi_patition");
    public static final boolean MTK_WIFIWPSP2P_NFC_SUPPORT = getValue("ro.mtk_wifiwpsp2p_nfc_support");
    public static final boolean MTK_GMO_RAM_OPTIMIZE = getValue("ro.mtk_gmo_ram_optimize");
    public static final boolean MTK_WFD_SINK_SUPPORT = getValue("ro.mtk_wfd_sink_support");
    public static final boolean MTK_WFD_SINK_UIBC_SUPPORT = getValue("ro.mtk_wfd_sink_uibc_support");
    public static final boolean MTK_BESLOUDNESS_SUPPORT = getValue("ro.mtk_besloudness_support");
    public static final boolean MTK_BESSURROUND_SUPPORT = getValue("ro.mtk_bessurround_support");
    public static final boolean MTK_MIRAVISION_SETTING_SUPPORT = getValue("ro.mtk_miravision_support");
    public static final boolean MTK_TC1_FEATURE = getValue("ro.mtk_tc1_feature");
    public static final boolean MTK_LOSSLESS_SUPPORT = getValue("ro.mtk_lossless_bt_audio");
    public static final boolean MTK_VOLTE_SUPPORT = getValue("ro.mtk_volte_support");
    // add for solution 2
    public static final boolean MTK_C2K_SLOT2_SUPPORT = getValue("ro.mtk.c2k.slot2.support");
    public static final boolean MTK_DUAL_INPUT_CHARGER_SUPPORT = SystemProperties.get("ro.mtk_diso_support").equals("true");
    public static final boolean MTK_PRODUCT_IS_TABLET = SystemProperties.get("ro.build.characteristics").equals("tablet");
    // Add for C2K start @ {
    public static final boolean MTK_SVLTE_SUPPORT = getValue("ro.mtk_svlte_support");
    // @}
    // M:WFC @ {
    public static final boolean MTK_WFC_SUPPORT = getValue("ro.mtk_wfc_support");
    // @}

    /// M: Add for CT 6M. @ {
    public static final boolean MTK_CT6M_SUPPORT = getValue("ro.ct6m_support");
    /// @ }

    // Important!!!  the SystemProperties key's length must less than 31 , or will have JE
    /* get the key's value*/
    private static boolean getValue(String key) {
        return SystemProperties.get(key).equals("1");
    }
}
