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
package com.mediatek.contacts.util;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;


public class DisableViewsUtil extends Handler {
    private static final String TAG = "DisableViewsUtil";

    private static final int ACTION_DISABLE = 0;
    private static final int ACTION_ENABLE = 1;
    private static final long DISABLE_DURATION = 500;
    private List<View> mViews;

    private DisableViewsUtil(List<View> views) {
        super(Looper.getMainLooper());
        mViews = views;
    }

    public void disableTemporarily() {
        disableAll();
        sendEmptyMessageDelayed(ACTION_ENABLE, DISABLE_DURATION);
    }

    @Override
    public void handleMessage(Message msg) {
        LogUtils.d(TAG, "[handleMessage]msg = " + msg.what);
        switch (msg.what) {
        case ACTION_ENABLE:
            enableAll();
            break;
        case ACTION_DISABLE:
            disableAll();
            break;
        default:
            LogUtils.w(TAG, "[handleMessage]not supported message: " + msg.what);
            break;
        }
    }

    private void enableAll() {
        for (View v : mViews) {
            if (v != null) {
                v.setEnabled(true);
            }
        }
    }

    private void disableAll() {
        for (View v : mViews) {
            if (v != null) {
                v.setEnabled(false);
            }
        }
    }

    public static class Builder {
        List<View> mViews = new ArrayList<View>();

        public Builder addView(View view) {
            if (view != null) {
                mViews.add(view);
            } else {
                LogUtils.w(TAG, "[addView]view is null, abort adding.");
            }
            return this;
        }

        public Builder addViews(List<View> views) {
            mViews.addAll(views);
            return this;
        }

        public DisableViewsUtil build() {
            return new DisableViewsUtil(mViews);
        }
    }
}
