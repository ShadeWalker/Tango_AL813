/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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

package com.mediatek.mms.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * M: defualt implemention of IMmsUnsupportedFilesEx.
 */
public class DefaultMmsUnsupportedFilesExt extends ContextWrapper implements
        IMmsUnsupportedFilesExt {

    private static final String TAG = "Mms/DefaultMmsUnsupportedFilesExt";

    /**
     * The Constructor.
     * @param base the context.
     */
    public DefaultMmsUnsupportedFilesExt(Context base) {
        super(base);
    }

    @Override
    public boolean isSupportedFile(String contentType, String fileName) {
        Log.d(TAG, "[isSupportedFile], contentType:" + contentType + ", fileName:" + fileName
            + " true");
        return true;
    }

    @Override
    public Bitmap getUnsupportedAudioIcon() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bitmap getUnsupportedImageIcon() {
        return null;
    }

    @Override
    public Bitmap getUnsupportedVideoIcon() {
        return null;
    }

    @Override
    public void setAudioUnsupportedIcon(LinearLayout audioView) {
    }

    @Override
    public void setAudioUnsupportedIcon(ImageView audioView, String contentType, String fileName) {
    }

    @Override
    public void setAudioUri(Uri uri) {
    }

    @Override
    public void setImageUnsupportedIcon(ImageView imageView, String contentType, String fileName) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setVideoUnsupportedIcon(ImageView videoView, String contentType, String fileName) {
    }

    @Override
    public void initUnsupportedViewForAudio(ViewGroup viewGroup) {
        // TODO Auto-generated method stub

    }

    @Override
    public void initUnsupportedViewForImage(ViewGroup viewGroup) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setUnsupportedViewVisibilityForAudio(boolean show) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setUnsupportedViewVisibilityForImage(boolean show) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setUnsupportedMsg(LinearLayout linearLayout, View srcView, boolean show) {

    }
}
