/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
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
package com.mediatek.gallery3d.video;

import android.app.Activity;
import android.content.Intent;

import com.mediatek.drm.OmaDrmClient;
import com.mediatek.galleryframework.util.MtkLog;

public class CTAExtension {
    private static final String TAG = "Gallery2/CTAExtension";
    private static final String CTA_ACTION = "com.mediatek.dataprotection.ACTION_VIEW_LOCKED_FILE";
    private OmaDrmClient mDrmClient;
    private Activity mActivity;
    private String mToken;
    private String mTokenKey;
    private boolean mIsCtaPlayback;

    public CTAExtension(Activity activity) {
        mActivity = activity;
    }

    /**
     * Check videoplayer is launched by DataProtection app or not.
     * if launched by DataProtection, should check the token value is valid or not.
     */
    public void checkIntentAndToken() {
        mDrmClient = new OmaDrmClient(mActivity.getApplicationContext());
        Intent intent = mActivity.getIntent();
        String action = intent.getAction();
        MtkLog.i(TAG, "checkIntentAndToken action = " + action);
        if (CTA_ACTION.equals(action)) {
            mToken = intent.getStringExtra("TOKEN");
            mTokenKey = intent.getData().toString();
            if (mToken == null || !mDrmClient.isTokenValid(mTokenKey, mToken)) {
                mDrmClient.release();
                mDrmClient = null;
                mActivity.finish();
                return;
            }
            mIsCtaPlayback = true;
        }
    }

    /**
     * If videoplayer back to background when playing a cta file, it should finish and return to
     * the DataProtection app.
     */
    public void finishPlayIfNeed() {
        MtkLog.i(TAG, "finishPlayIfNeed mIsCtaPlayback = " + mIsCtaPlayback);
        if (mIsCtaPlayback) {
            mDrmClient.clearToken(mTokenKey, mToken);
            mTokenKey = null;
            mToken = null;
            mIsCtaPlayback = false;
            mDrmClient.release();
            mDrmClient = null;
            mActivity.finish();
        } else if (mDrmClient != null) {
            mDrmClient.release();
            mDrmClient = null;
        }
    }
}