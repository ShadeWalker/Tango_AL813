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
import android.net.Uri;

import android.util.Log;

import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

public class DefaultMmsCancelDownloadExt extends ContextWrapper implements IMmsCancelDownloadExt {
    private static final String TAG = "Mms/MmsCancelDownloadImpl";
    private IMmsCancelDownloadHost mHost = null;

    public DefaultMmsCancelDownloadExt(Context context) {
        super(context);
    }

    public void init(IMmsCancelDownloadHost host) {
        mHost = host;
        return;
    }

    protected IMmsCancelDownloadHost getHost() {
        return mHost;
    }

    @Override
    public void addHttpClient(String url, Uri uri) {
        Log.d(TAG, "MmsCancelDownloadImpl: setHttpClient()");
    }

    public void cancelDownload(final Uri uri) {
        Log.d(TAG, "MmsCancelDownloadImpl: cancelDownload()");
    }

    public void removeHttpClient(String url) {
        Log.d(TAG, "MmsCancelDownloadImpl: removeHttpClient()");
    }

    public void setCancelToastEnabled(boolean isEnable) {
        Log.d(TAG, "MmsCancelDownloadImpl: setCancelEnabled()");
    }

    public boolean getCancelToastEnabled() {
        Log.d(TAG, "MmsCancelDownloadImpl: getCancelEnabled()");
        return false;
    }

    public void markStateExt(Uri uri, int state) {
        Log.d(TAG, "MmsCancelDownloadImpl:markStateExt()");
    }

    public int getStateExt(Uri uri) {
        Log.d(TAG, "MmsCancelDownloadImpl:getStateExt(Uri)");
        return STATE_UNKNOWN;
    }

    public int getStateExt(String url) {
        Log.d(TAG, "MmsCancelDownloadImpl:getStateExt(String)");
        return STATE_UNKNOWN;
    }

    public void setWaitingDataCnxn(boolean isWaiting) {
        Log.d(TAG, "setWaitingDataCnxn() in default");
    }

    public boolean getWaitingDataCnxn() {
        Log.d(TAG, "getWaitingDataCnxn() in default");
        return false;
    }

    public void saveDefaultHttpRetryHandler(DefaultHttpRequestRetryHandler retryHandler) {
        Log.d(TAG, "MmsCancelDownloadImpl: saveDefaultHttpRetryHandler()");
    }

}
