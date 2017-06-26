/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts;

import android.bluetooth.BluetoothAdapter;
import android.os.SystemProperties;

public class ContactsSystemProperties {

    public static final boolean MTK_GEMINI_SUPPORT = isPropertyEnabled("ro.mtk_gemini_support");
    public static final boolean MTK_VT3G324M_SUPPORT = isPropertyEnabled("ro.mtk_vt3g324m_support");
    public static final boolean MTK_GEMINI_3G_SWITCH = isPropertyEnabled("ro.mtk_gemini_3g_switch");
    public static final boolean MTK_DRM_SUPPORT = isPropertyEnabled("ro.mtk_oma_drm_support");
    public static final boolean MTK_OWNER_SIM_SUPPORT = isPropertyEnabled("ro.mtk_owner_sim_support");
    public static final boolean MTK_HOTKNOT_SUPPORT = isPropertyEnabled("ro.mtk_hotknot_support");
    public static final boolean MTK_VVM_SUPPORT = true; //[VVM] vvm is a Google default feature.
    // VOLTE IMS Call feature.
    public static final boolean MTK_VOLTE_SUPPORT = isPropertyEnabled("ro.mtk_volte_support");
    public static final boolean MTK_IMS_SUPPORT = isPropertyEnabled("ro.mtk_ims_support");
    // CTA support
    public static final boolean MTK_CTA_SUPPORT = isPropertyEnabled("ro.mtk_cta_support");

    // ALPS02008620, [Performance] APP back key behavior rollback to Google's design.
    public static final boolean MTK_PERF_RESPONSE_TIME = isPropertyEnabled("ro.mtk_perf_response_time");
    public static boolean DBG_DIALER_SEARCH = true;
    public static boolean DBG_CONTACTS_GROUP = true;

    public static boolean isSupportBtProfileBpp() {
        return (BluetoothAdapter.getDefaultAdapter() != null)
                && com.mediatek.bluetooth.ConfigHelper.checkSupportedProfiles(com.mediatek.bluetooth.ProfileConfig.PROFILE_ID_BPP);
    }

    private static boolean isPropertyEnabled(String propertyString) {
        return SystemProperties.get(propertyString).equals("1");
    }
}
