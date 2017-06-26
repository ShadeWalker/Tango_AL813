/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

/// M: Add for CT6M. @{
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
/// @}

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.MobileSignalController.MobileIconGroup;

import com.mediatek.systemui.ext.NetworkType;

/**
  * TelephonyIcons.
 */
public class TelephonyIcons {
    //***** Signal strength icons
    /// M: config for show the ! icon or not @{
    static final int[] TELEPHONY_SIGNAL_STRENGTH_EXCLAMATION  = {
          R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4,
    };
    static final int[] TELEPHONY_SIGNAL_STRENGTH_FULL = {
          R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully,
    };

    static final int[] QS_TELEPHONY_SIGNAL_STRENGTH_EXCLAMATION = {
          R.drawable.ic_qs_signal_0,
          R.drawable.ic_qs_signal_1,
          R.drawable.ic_qs_signal_2,
          R.drawable.ic_qs_signal_3,
          R.drawable.ic_qs_signal_4,
    };

    static final int[] QS_TELEPHONY_SIGNAL_STRENGTH_FULL = {
          R.drawable.ic_qs_signal_full_0,
          R.drawable.ic_qs_signal_full_1,
          R.drawable.ic_qs_signal_full_2,
          R.drawable.ic_qs_signal_full_3,
          R.drawable.ic_qs_signal_full_4,
    };

    static final int[] TELEPHONY_SIGNAL_STRENGTH_ROAMING_EXCLAMATION  = {
          R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4,
    };
    static final int[] TELEPHONY_SIGNAL_STRENGTH_ROAMING_FULL = {
          R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully,
    };

    static final int TELEPHONY_LEVEL_COUNT = TELEPHONY_SIGNAL_STRENGTH_FULL.length;
    static int[][] TELEPHONY_SIGNAL_STRENGTH = new int[2][TELEPHONY_LEVEL_COUNT];
    static int[][] QS_TELEPHONY_SIGNAL_STRENGTH = new int[2][TELEPHONY_LEVEL_COUNT];
    static int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = new int[2][TELEPHONY_LEVEL_COUNT];

    static void initTelephonyIcon() {
        if (NetworkControllerImpl.mShowNormalIcon) {
            TELEPHONY_SIGNAL_STRENGTH[0] = TELEPHONY_SIGNAL_STRENGTH_FULL;
            TELEPHONY_SIGNAL_STRENGTH[1] = TELEPHONY_SIGNAL_STRENGTH_FULL;
            QS_TELEPHONY_SIGNAL_STRENGTH[0] = QS_TELEPHONY_SIGNAL_STRENGTH_FULL;
            QS_TELEPHONY_SIGNAL_STRENGTH[1] = QS_TELEPHONY_SIGNAL_STRENGTH_FULL;
            TELEPHONY_SIGNAL_STRENGTH_ROAMING[0] = TELEPHONY_SIGNAL_STRENGTH_ROAMING_FULL;
            TELEPHONY_SIGNAL_STRENGTH_ROAMING[1] = TELEPHONY_SIGNAL_STRENGTH_ROAMING_FULL;
        } else {
            TELEPHONY_SIGNAL_STRENGTH[0] = TELEPHONY_SIGNAL_STRENGTH_EXCLAMATION;
            TELEPHONY_SIGNAL_STRENGTH[1] = TELEPHONY_SIGNAL_STRENGTH_FULL;
            QS_TELEPHONY_SIGNAL_STRENGTH[0] = QS_TELEPHONY_SIGNAL_STRENGTH_EXCLAMATION;
            QS_TELEPHONY_SIGNAL_STRENGTH[1] = QS_TELEPHONY_SIGNAL_STRENGTH_FULL;
            TELEPHONY_SIGNAL_STRENGTH_ROAMING[0] = TELEPHONY_SIGNAL_STRENGTH_ROAMING_EXCLAMATION;
            TELEPHONY_SIGNAL_STRENGTH_ROAMING[1] = TELEPHONY_SIGNAL_STRENGTH_ROAMING_FULL;
        }
    }
    /// M: config for show the ! icon or not @}

    static final int TELEPHONY_NUM_LEVELS = 5;

    //GSM/UMTS
    static final int TELEPHONY_NO_NETWORK = R.drawable.stat_sys_signal_null;

    /*static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };*/

    static final int QS_TELEPHONY_NO_NETWORK = R.drawable.ic_qs_signal_no_signal;

    /*static final int[][] QS_TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.ic_qs_signal_0,
          R.drawable.ic_qs_signal_1,
          R.drawable.ic_qs_signal_2,
          R.drawable.ic_qs_signal_3,
          R.drawable.ic_qs_signal_4 },
        { R.drawable.ic_qs_signal_full_0,
          R.drawable.ic_qs_signal_full_1,
          R.drawable.ic_qs_signal_full_2,
          R.drawable.ic_qs_signal_full_3,
          R.drawable.ic_qs_signal_full_4 }
    };

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };*/

    static final int[] QS_DATA_R = {
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_r
    };

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g }
        };

    static final int[] QS_DATA_G = {
        R.drawable.ic_qs_signal_g,
        R.drawable.ic_qs_signal_g
    };

    static final int[][] DATA_3G = {
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
        };

    static final int[] QS_DATA_3G = {
        R.drawable.ic_qs_signal_3g,
        R.drawable.ic_qs_signal_3g
    };

    static final int[][] DATA_E = {
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
        };

    static final int[] QS_DATA_E = {
        R.drawable.ic_qs_signal_e,
        R.drawable.ic_qs_signal_e
    };

    //3.5G
    static final int[][] DATA_H = {
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
    };

    static final int[] QS_DATA_H = {
                R.drawable.ic_qs_signal_h,
                R.drawable.ic_qs_signal_h
    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            };

    static final int[] QS_DATA_1X = {
        R.drawable.ic_qs_signal_1x,
        R.drawable.ic_qs_signal_1x
    };

    // LTE and eHRPD
    static final int[][] DATA_4G = {
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
        };

    static final int[] QS_DATA_4G = {
        R.drawable.ic_qs_signal_4g,
        R.drawable.ic_qs_signal_4g
    };

    // LTE branded "LTE"
    static final int[][] DATA_LTE = {
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte },
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte }
    };

    static final int[] QS_DATA_LTE = {
        R.drawable.ic_qs_signal_lte,
        R.drawable.ic_qs_signal_lte
    };

    static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_airplane_mode;
    static final int ROAMING_ICON = R.drawable.stat_sys_data_fully_connected_roam;
    static final int ICON_LTE = R.drawable.stat_sys_data_fully_connected_lte;
    static final int ICON_G = R.drawable.stat_sys_data_fully_connected_g;
    static final int ICON_E = R.drawable.stat_sys_data_fully_connected_e;
    static final int ICON_H = R.drawable.stat_sys_data_fully_connected_h;
    static final int ICON_3G = R.drawable.stat_sys_data_fully_connected_3g;
    static final int ICON_4G = R.drawable.stat_sys_data_fully_connected_4g;
    static final int ICON_1X = R.drawable.stat_sys_data_fully_connected_1x;

    static final int QS_ICON_LTE = R.drawable.ic_qs_signal_lte;
    static final int QS_ICON_3G = R.drawable.ic_qs_signal_3g;
    static final int QS_ICON_4G = R.drawable.ic_qs_signal_4g;
    static final int QS_ICON_1X = R.drawable.ic_qs_signal_1x;

    static final MobileIconGroup THREE_G = new MobileIconGroup(
            "3G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3g,
            TelephonyIcons.ICON_3G,
            true,
            TelephonyIcons.QS_DATA_3G
            );

    static final MobileIconGroup UNKNOWN = new MobileIconGroup(
            "Unknown",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            0, 0, false, new int[2]
            );

    static final MobileIconGroup E = new MobileIconGroup(
            "E",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_edge,
            TelephonyIcons.ICON_E,
            false,
            TelephonyIcons.QS_DATA_E
            );

    static final MobileIconGroup ONE_X = new MobileIconGroup(
            "1X",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_cdma,
            TelephonyIcons.ICON_1X,
            true,
            TelephonyIcons.QS_DATA_1X
            );

    static final MobileIconGroup G = new MobileIconGroup(
            "G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_gprs,
            TelephonyIcons.ICON_G,
            false,
            TelephonyIcons.QS_DATA_G
            );

    static final MobileIconGroup H = new MobileIconGroup(
            "H",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3_5g,
            TelephonyIcons.ICON_H,
            false,
            TelephonyIcons.QS_DATA_H
            );

    static final MobileIconGroup FOUR_G = new MobileIconGroup(
            "4G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_4g,
            TelephonyIcons.ICON_4G,
            true,
            TelephonyIcons.QS_DATA_4G
            );

    static final MobileIconGroup LTE = new MobileIconGroup(
            "LTE",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_lte,
            TelephonyIcons.ICON_LTE,
            true,
            TelephonyIcons.QS_DATA_LTE
            );

    static final MobileIconGroup ROAMING = new MobileIconGroup(
            "Roaming",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_roaming,
            TelephonyIcons.ROAMING_ICON,
            false,
            TelephonyIcons.QS_DATA_R
            );

    /// M: Support "Service Network Type on Statusbar". @{
    /**
     * M: getNetworkTypeIcon: Get Network icon by network type.
     * @param networkType : Network Type
     * @return  Network icon ID
     */
    static public int getNetworkTypeIcon(NetworkType networkType) {
        if (networkType == NetworkType.Type_G) {
            return R.drawable.stat_sys_network_type_g;
        } else if (networkType == NetworkType.Type_E) {
            return R.drawable.stat_sys_network_type_e;
        } else if (networkType == NetworkType.Type_3G) {
            return R.drawable.stat_sys_network_type_3g;
        } else if (networkType == NetworkType.Type_4G) {
            return R.drawable.stat_sys_network_type_4g;
        } else if (networkType == NetworkType.Type_1X) {
            return R.drawable.stat_sys_network_type_1x;
        } else if (networkType == NetworkType.Type_1X3G) {
            return R.drawable.stat_sys_network_type_3g;
        } else {
            return -1;
        }
    }
    /// M: Support "Service Network Type on Statusbar". @}

    /// M: Add roaming data nework type icon @{
    static final int ROAMING_ICON_LTE = R.drawable.stat_sys_data_fully_connected_lte_roam;
    static final int ROAMING_ICON_G = R.drawable.stat_sys_data_fully_connected_g_roam;
    static final int ROAMING_ICON_E = R.drawable.stat_sys_data_fully_connected_e_roam;
    static final int ROAMING_ICON_H = R.drawable.stat_sys_data_fully_connected_h_roam;
    static final int ROAMING_ICON_3G = R.drawable.stat_sys_data_fully_connected_3g_roam;
    static final int ROAMING_ICON_4G = R.drawable.stat_sys_data_fully_connected_4g_roam;
    static final int ROAMING_ICON_1X = R.drawable.stat_sys_data_fully_connected_1x_roam;

    /**
     *
     * Get roaming data type icon id.
     *
     * @param dataType Data type icon id
     * @return Roaming data type icon id
     */
    static public int getRoamingDataTypeIcon(int dataType) {
        int icon = ROAMING_ICON;
        switch (dataType) {
        case ICON_LTE:
            icon = ROAMING_ICON_LTE;
            break;
        case ICON_G:
            icon = ROAMING_ICON_G;
            break;
        case ICON_E:
            icon = ROAMING_ICON_E;
            break;
        case ICON_H:
            icon = ROAMING_ICON_H;
            break;
        case ICON_3G:
            icon = ROAMING_ICON_3G;
            break;
        case ICON_4G:
            icon = ROAMING_ICON_4G;
            break;
        case ICON_1X:
            icon = ROAMING_ICON_1X;
            break;
        default:
            break;
        }
        return icon;
    }
    /// M: Add roaming data nework type icon @}

    /// M: Add for CT6M. add activity icon and primary sim icon @{
    static final int DATA_ACTIVITY_NONE = R.drawable.ct_stat_sys_signal_not_inout;
    static final int DATA_ACTIVITY_IN = R.drawable.ct_stat_sys_signal_in;
    static final int DATA_ACTIVITY_OUT = R.drawable.ct_stat_sys_signal_out;
    static final int DATA_ACTIVITY_INOUT = R.drawable.ct_stat_sys_signal_inout;

    /**
     * M: getDataActivityIcon: Get DataActivity icon by dataActivity type.
     * @param dataActivity : dataActivity Type
     * @return  dataActivity icon ID
     */
    static public int getDataActivityIcon(int dataActivity) {
        int icon = DATA_ACTIVITY_NONE;

        switch(dataActivity) {
        case TelephonyManager.DATA_ACTIVITY_IN:
            icon = DATA_ACTIVITY_IN;
            break;
        case TelephonyManager.DATA_ACTIVITY_OUT:
            icon = DATA_ACTIVITY_OUT;
            break;
        case TelephonyManager.DATA_ACTIVITY_INOUT:
            icon = DATA_ACTIVITY_INOUT;
            break;
        default:
            break;
        }
        return icon;
    }


    static final int PRIMARY_SIM_CARD = R.drawable.stat_sys_data_primary_simcard;
    static final int PRIMARY_SIM_CARD_ROAM = R.drawable.stat_sys_data_primary_simcard_roam;

    /**
     * M: getPrimarySimIcon: Get PrimarySim icon by roaming type.
     * @param roaming : roaming Type
     * @param subId : current sim subId
     * @return  PrimarySim icon ID
     */
    static public int getPrimarySimIcon(boolean roaming, int subId) {
        int icon = 0; // not show
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();

        if (roaming) {
            if (subId == defaultDataSubId) {
                icon = PRIMARY_SIM_CARD_ROAM;
            } else {
                icon = TelephonyIcons.ROAMING_ICON;
            }
        } else {
            if (subId == defaultDataSubId) {
                icon = PRIMARY_SIM_CARD;
            }
        }
        return icon;
    }
    /// @}
}

