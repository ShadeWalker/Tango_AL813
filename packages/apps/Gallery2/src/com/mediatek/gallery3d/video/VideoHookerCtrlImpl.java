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
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.app.Log;

import com.mediatek.gallery3d.ext.DefaultMovieItem;
import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.galleryframework.base.MediaData;

import com.mediatek.galleryfeature.SlideVideo.IVideoHookerCtl;

public class VideoHookerCtrlImpl implements IVideoHookerCtl {
    private final static String TAG = "Gallery2/VideoPlayer/VideoHookerCtrlImpl";
    private static IActivityHooker mHooker;
    private static IVideoHookerCtl sVideoHookerCtrlImpl;
    
    public static IVideoHookerCtl getVideoHookerCtrlImpl(Activity context,
            IActivityHooker rewindAndForwardHooker) {
        if (sVideoHookerCtrlImpl == null) {
            sVideoHookerCtrlImpl = new VideoHookerCtrlImpl();
            createHooker(context, rewindAndForwardHooker);
        }
        return sVideoHookerCtrlImpl;
    }
    
    private static void createHooker(Activity context, IActivityHooker rewindAndForwardHooker) {
        if (mHooker == null) {
            Log.d(TAG, "createHooker()");
            mHooker = ExtensionHelper.getHooker(context);
            ((ActivityHookerGroup) mHooker).addHooker(rewindAndForwardHooker);
            mHooker.init(context, null);
            mHooker.onCreate(null);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mHooker.onCreateOptionsMenu(menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mHooker.onPrepareOptionsMenu(menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mHooker.onOptionsItemSelected(item);
    }
    
    @Override
    public void setData(MediaData data) {
        if (data != null) {
            String uri = "file://" + data.filePath;
            IMovieItem item = new DefaultMovieItem(uri, data.mimeType, data.caption);
            mHooker.setParameter(null, item);
        }
    }
    
    public static IActivityHooker getHooker() {
        return mHooker;
    }
    
    public static void destoryHooker() {
        Log.d(TAG, "destoryHooker()");
        sVideoHookerCtrlImpl = null;
        mHooker = null;
    }
}