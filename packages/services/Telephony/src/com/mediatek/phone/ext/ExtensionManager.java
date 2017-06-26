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

package com.mediatek.phone.ext;

import com.android.phone.PhoneGlobals;
import com.android.services.telephony.Log;

import com.mediatek.common.MPlugin;

public final class ExtensionManager {

    private static final String LOG_TAG = "ExtensionManager";

    private static IPhoneMiscExt sPhoneMiscExt;
    private static IMmiCodeExt sMmiCodeExt;
    private static IMobileNetworkSettingsExt sMobileNetworkSettingsExt;
    private static ICallFeaturesSettingExt sCallFeaturesSettingExt;
    private static INetworkSettingExt sNetworkSettingExt;
    private static ICallForwardExt sCallForwardExt;
    private static ITelecomAccountRegistryExt sTelecomAccountRegistryExt;
    private static ITelephonyConnectionServiceExt sTelephonyConnectionServiceExt;
    private static IEmergencyDialerExt sEmergencyDialerExt;

    private ExtensionManager() {
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    public static IPhoneMiscExt getPhoneMiscExt() {
        if (sPhoneMiscExt == null) {
            synchronized (IPhoneMiscExt.class) {
                if (sPhoneMiscExt == null) {
                    sPhoneMiscExt = (IPhoneMiscExt) MPlugin.createInstance(
                            IPhoneMiscExt.class.getName(), PhoneGlobals.getInstance());
                    if (sPhoneMiscExt == null) {
                        sPhoneMiscExt = new DefaultPhoneMiscExt();
                    }
                    log("[getPhoneMiscExt]create ext instance: " + sPhoneMiscExt);
                }
            }
        }
        return sPhoneMiscExt;
    }

    public static IMmiCodeExt getMmiCodeExt() {
        if (sMmiCodeExt == null) {
            synchronized (IMmiCodeExt.class) {
                if (sMmiCodeExt == null) {
                    sMmiCodeExt = (IMmiCodeExt) MPlugin.createInstance(
                            IMmiCodeExt.class.getName(), PhoneGlobals.getInstance());
                    if (sMmiCodeExt == null) {
                        sMmiCodeExt = new DefaultMmiCodeExt();
                    }
                    log("[getMmiCodeExt]create ext instance: " + sMmiCodeExt);
                }
            }
        }
        return sMmiCodeExt;
    }

    public static IMobileNetworkSettingsExt getMobileNetworkSettingsExt() {
        if (sMobileNetworkSettingsExt == null) {
            synchronized (IMobileNetworkSettingsExt.class) {
                if (sMobileNetworkSettingsExt == null) {
                    sMobileNetworkSettingsExt = (IMobileNetworkSettingsExt) MPlugin.createInstance(
                            IMobileNetworkSettingsExt.class.getName(), PhoneGlobals.getInstance());
                    if (sMobileNetworkSettingsExt == null) {
                        sMobileNetworkSettingsExt = new DefaultMobileNetworkSettingsExt();
                    }
                    log("[getMobileNetworkSettingsExt]create ext instance: " + sMobileNetworkSettingsExt);
                }
            }
        }
        return sMobileNetworkSettingsExt;
    }

    public static ICallFeaturesSettingExt getCallFeaturesSettingExt() {
        if (sCallFeaturesSettingExt == null) {
            synchronized (ICallFeaturesSettingExt.class) {
                if (sCallFeaturesSettingExt == null) {
                    sCallFeaturesSettingExt = (ICallFeaturesSettingExt) MPlugin.createInstance(
                            ICallFeaturesSettingExt.class.getName(), PhoneGlobals.getInstance());
                    if (sCallFeaturesSettingExt == null) {
                        sCallFeaturesSettingExt = new DefaultCallFeaturesSettingExt();
                    }
                    log("[getCallFeaturesSettingExt]create ext instance: " + sCallFeaturesSettingExt);
                }
            }
        }
        return sCallFeaturesSettingExt;
    }

    public static INetworkSettingExt getNetworkSettingExt() {
        if (sNetworkSettingExt == null) {
            synchronized (INetworkSettingExt.class) {
                if (sNetworkSettingExt == null) {
                    sNetworkSettingExt = (INetworkSettingExt) MPlugin.createInstance(
                            INetworkSettingExt.class.getName(), PhoneGlobals.getInstance());
                    if (sNetworkSettingExt == null) {
                        sNetworkSettingExt = new DefaultNetworkSettingExt();
                    }
                    log("[getNetworkSettingExt]create ext instance: " + sNetworkSettingExt);
                }
            }
        }
        return sNetworkSettingExt;
    }

    public static ICallForwardExt getCallForwardExt() {
        if (sCallForwardExt == null) {
            synchronized (ICallForwardExt.class) {
                if (sCallForwardExt == null) {
                    sCallForwardExt = (ICallForwardExt) MPlugin.createInstance(
                            ICallForwardExt.class.getName(), PhoneGlobals.getInstance());
                    if (sCallForwardExt == null) {
                        sCallForwardExt = new DefaultCallForwardExt();
                    }
                    log("[getCallForwardExt] create ext instance: " + sCallForwardExt);
                }
            }
        }
        return sCallForwardExt;
    }

    public static ITelecomAccountRegistryExt getTelecomAccountRegistryExt() {
        if (sTelecomAccountRegistryExt == null) {
            synchronized (ITelecomAccountRegistryExt.class) {
                if (sTelecomAccountRegistryExt == null) {
                    sTelecomAccountRegistryExt =
                        (ITelecomAccountRegistryExt) MPlugin.createInstance(
                            ITelecomAccountRegistryExt.class.getName(), PhoneGlobals.getInstance());
                    if (sTelecomAccountRegistryExt == null) {
                        sTelecomAccountRegistryExt = new DefaultTelecomAccountRegistryExt();
                    }
                    log("[getTelecomAccountRegistryExt]: " + sTelecomAccountRegistryExt);
                }
            }
        }
        return sTelecomAccountRegistryExt;
    }

    public static ITelephonyConnectionServiceExt getTelephonyConnectionServiceExt() {
        if (sTelephonyConnectionServiceExt == null) {
            synchronized (ITelephonyConnectionServiceExt.class) {
                if (sTelephonyConnectionServiceExt == null) {
                    sTelephonyConnectionServiceExt =
                        (ITelephonyConnectionServiceExt) MPlugin.createInstance(
                            ITelephonyConnectionServiceExt.class.getName(),
                                PhoneGlobals.getInstance());
                    if (sTelephonyConnectionServiceExt == null) {
                        sTelephonyConnectionServiceExt = new DefaultTelephonyConnectionServiceExt();
                    }
                    log("[getTelephonyConnectionServiceExt]: " + sTelephonyConnectionServiceExt);
                }
            }
        }
        return sTelephonyConnectionServiceExt;
    }

    public static IEmergencyDialerExt getEmergencyDialerExt() {
        if (sEmergencyDialerExt == null) {
            synchronized (IEmergencyDialerExt.class) {
                if (sEmergencyDialerExt == null) {
                    sEmergencyDialerExt =
                        (IEmergencyDialerExt) MPlugin.createInstance(
                            IEmergencyDialerExt.class.getName(),
                                PhoneGlobals.getInstance());
                    if (sEmergencyDialerExt == null) {
                        sEmergencyDialerExt = new DefaultEmergencyDialerExt();
                    }
                    log("[getEmergencyDialerExt]: " + sEmergencyDialerExt);
                }
            }
        }
        return sEmergencyDialerExt;
    }
}
