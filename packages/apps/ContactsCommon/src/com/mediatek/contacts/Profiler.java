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

import android.util.Log;

public class Profiler {

    private static final String TAG = "PhoneProfiler";

    public static final String DialpadFragmentEnterClick = "+DialpadFragment.onClick";
    public static final String DialpadFragmentLeaveClick = "-DialpadFragment.onClick";
    public static final String CallOptionHandlerEnterStartActivity = "+CallOptionHandler.StartActivity";
    public static final String CallOptionHandlerLeaveStartActivity = "-CallOptionHandler.StartActivity";
    public static final String CallOptionHandlerEnterOnClick = "+CallOptionHandler.onClick";
    public static final String CallOptionHandlerLeaveOnClick = "-CallOptionHandler.onClick";
    public static final String CallOptionHandlerEnterRun = "+CallOptionHandler.run";
    public static final String CallOptionHandlerLeaveRun = "-CallOptionHandler.run";
    public static final String CallOptionHelperEnterMakeVoiceCall = "+CallOptionHelper.makeVoiceCall";
    public static final String CallOptionHelperLeaveMakeVoiceCall = "-CallOptionHelper.makeVoiceCall";

    public static final String DialpadFragmentEnterOnCreate = "+DialpadFragment.onCreate";
    public static final String DialpadFragmentLeaveOnCreate = "-DialpadFragment.onCreate";
    public static final String DialpadFragmentEnterOnResume = "+DialpadFragment.onResume";
    public static final String DialpadFragmentLeaveOnResume = "-DialpadFragment.onResume";
    public static final String DialpadFragmentOnPostDraw = "DialpadFragment.onPostDrawer";
    public static final String DialpadFragmentEnterOnCreateView = "+DialpadFragment.onCreateView";
    public static final String DialpadFragmentLeaveOnCreateView = "-DialpadFragment.onCreateView";

    public static final String CallLogFragmentEnterOnCreate = "+CallLogFragment.onCreate";
    public static final String CallLogFragmentLeaveOnCreate = "-CallLogFragment.onCreate";
    public static final String CallLogFragmentEnterOnResume = "+CallLogFragment.onResume";
    public static final String CallLogFragmentLeaveOnResume = "-CallLogFragment.onResume";
    public static final String CallLogEnterOnCreateView = "+CallLogFragment.onCreateView";
    public static final String CallLogLeaveOnCreateView = "-CallLogFragment.onCreateView";

    public static final String PhoneFavoriteFragmentEnterOnCreate = "+PhoneFavoriteFragment.onCreate";
    public static final String PhoneFavoriteFragmentLeaveOnCreate = "-PhoneFavoriteFragment.onCreate";
    public static final String PhoneFavoriteFragmentEnterOnStart = "+PhoneFavoriteFragment.onStart";
    public static final String PhoneFavoriteFragmentLeaveOnStart = "-PhoneFavoriteFragment.onStart";
    public static final String PhoneFavoriteFragmentEnterOnCreateView = "+PhoneFavoriteFragment.onCreateView";
    public static final String PhoneFavoriteFragmentLeaveOnCreateView = "-PhoneFavoriteFragment.onCreateView";

    public static final String ViewPagerNewDialpadFragment = "ViewPager.getItem DialpadFragment";
    public static final String ViewPagerNewCallLogFragment = "ViewPager.getItem CallLogFragment";
    public static final String ViewPagerNewPhoneFavoriteFragment = "ViewPager.getItem PhoneFavoriteFragment";

    public static final String DialtactsActivitySetCurrentTab = "ViewPager.setCurrentTab";

    public static final String DialpadFragmentViewEnterOnMeasure = "+DialpadFragmentView.OnMeasure";
    public static final String DialpadFragmentViewLeaveOnMeasure = "-DialpadFragmentView.OnMeasure";

    public static final String DialtactsActivitySetOffscreenPageLimit = "ViewPager.setOffscreenPageLimit";
    public static final String DialtactsActivityEnterOnCreate = "+DialtactsActivity.onCreate";
    public static final String DialtactsActivityLeaveOnCreate = "-DialtactsActivity.onCreate";
    public static final String DialtactsActivityEnterOnPause = "+DialtactsActivity.onPause";
    public static final String DialtactsActivityLeaveOnPause = "-DialtactsActivity.onPause";
    public static final String DialtactsActivityEnterOnStop = "+DialtactsActivity.onStop";
    public static final String DialtactsActivityLeaveOnStop = "-DialtactsActivity.onStop";
    public static final String DialtactsActivityOnBackPressed = "DialtactsActivityOnBackPressed";

    private static final boolean enablePhoneProfiler = false;

    public static void trace(String msg) {
        if (enablePhoneProfiler) {
            Log.d(TAG, msg);
        }
    }

}
