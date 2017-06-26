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

package com.mediatek.incallui.ext;

import android.content.Context;

import com.android.incallui.Log;

import com.mediatek.common.MPlugin;

public final class ExtensionManager {

    private static final String TAG = ExtensionManager.class.getSimpleName();
    private static Context sApplicationContext;

    private ExtensionManager() {
    }

    public static void registerApplicationContext(Context context) {
        if (sApplicationContext == null) {
            sApplicationContext = context.getApplicationContext();
        }
    }

    private static IRCSeCallButtonExt sRCSeCallButtonExt;
    private static IRCSeCallCardExt sRCSeCallCardExt;
    private static IRCSeInCallExt sRCSeInCallExt;
    private static IInCallExt sInCallExt;
    private static ICallCardExt sCallCardExt;
    private static IEmergencyCallCardExt sEmergencyCallCardExt;

    public static IRCSeCallButtonExt getRCSeCallButtonExt() {
        if (sRCSeCallButtonExt == null) {
            synchronized (IRCSeCallButtonExt.class) {
                if (sRCSeCallButtonExt == null) {
                    sRCSeCallButtonExt = (IRCSeCallButtonExt) MPlugin.createInstance(
                            IRCSeCallButtonExt.class.getName(), sApplicationContext);
                    if (sRCSeCallButtonExt == null) {
                        sRCSeCallButtonExt = new DefaultRCSeCallButtonExt();
                    }
                    Log.i(TAG, "[getRCSeCallButtonExt]create ext instance: " + sRCSeCallButtonExt);
                }
            }
        }
        return sRCSeCallButtonExt;
    }

    public static IRCSeCallCardExt getRCSeCallCardExt() {
        if (sRCSeCallCardExt == null) {
            synchronized (IRCSeCallCardExt.class) {
                if (sRCSeCallCardExt == null) {
                    sRCSeCallCardExt = (IRCSeCallCardExt) MPlugin.createInstance(IRCSeCallCardExt.class.getName(),
                            sApplicationContext);
                    if (sRCSeCallCardExt == null) {
                        sRCSeCallCardExt = new DefaultRCSeCallCardExt();
                    }
                    Log.i(TAG, "[getRCSeCallCardExt]create ext instance: " + sRCSeCallCardExt);
                }
            }
        }
        return sRCSeCallCardExt;
    }

    public static IRCSeInCallExt getRCSeInCallExt() {
        if (sRCSeInCallExt == null) {
            synchronized (IRCSeInCallExt.class) {
                if (sRCSeInCallExt == null) {
                    sRCSeInCallExt = (IRCSeInCallExt) MPlugin.createInstance(IRCSeInCallExt.class.getName(),
                            sApplicationContext);
                    if (sRCSeInCallExt == null) {
                        sRCSeInCallExt = new DefaultRCSeInCallExt();
                    }
                    Log.i(TAG, "[getRCSeInCallExt]create ext instance: " + sRCSeInCallExt);
                }
            }
        }
        return sRCSeInCallExt;
    }
    
    public static IInCallExt getInCallExt() {
        if (sInCallExt == null) {
            synchronized (IInCallExt.class) {
                if (sInCallExt == null) {
                    sInCallExt = (IInCallExt) MPlugin.createInstance(IInCallExt.class.getName(),
                            sApplicationContext);
                    if (sInCallExt == null) {
                        sInCallExt = new DefaultInCallExt();
                    }
                    Log.i(TAG, "[getInCallExt]create ext instance: " + sInCallExt);
                }
            }
        }
        return sInCallExt;
    }

    public static ICallCardExt getCallCardExt() {
        if (sCallCardExt == null) {
            synchronized (ICallCardExt.class) {
                if (sCallCardExt == null) {
                    sCallCardExt = (ICallCardExt) MPlugin.createInstance(ICallCardExt.class.getName(),
                            sApplicationContext);
                    if (sCallCardExt == null) {
                        sCallCardExt = new DefaultCallCardExt();
                    }
                    Log.i(TAG, "[getCallCardExt]create ext instance: " + sCallCardExt);
                }
            }
        }
        return sCallCardExt;
    }

    public static IEmergencyCallCardExt getEmergencyCallCardExt() {
        if (sEmergencyCallCardExt == null) {
            synchronized (IEmergencyCallCardExt.class) {
                if (sEmergencyCallCardExt == null) {
                    sEmergencyCallCardExt = (IEmergencyCallCardExt) MPlugin.createInstance(
                            IEmergencyCallCardExt.class.getName(), sApplicationContext);
                    if (sEmergencyCallCardExt == null) {
                        sEmergencyCallCardExt = new DefaultEmergencyCallCardExt();
                    }
                    Log.i(TAG, "[getRCSeCallButtonExt]create ext instance: " + sEmergencyCallCardExt);
                }
            }
        }
        return sEmergencyCallCardExt;
    }

}
