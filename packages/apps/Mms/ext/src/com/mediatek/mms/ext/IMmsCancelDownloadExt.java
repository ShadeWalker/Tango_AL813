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

import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

import android.net.Uri;

public interface IMmsCancelDownloadExt {
    int STATE_UNKNOWN           = 0x00;
    int STATE_DOWNLOADING       = 0x01;
    int STATE_CANCELLING        = 0x02;
    int STATE_COMPLETE          = 0x03;
    int STATE_ABORTED           = 0x04;

    /**
     * Init host call back;
     * @param host
     */
    void init(IMmsCancelDownloadHost host);

    /**
     * M: Cancel downloading mms;
     * OP09Feature: MMS-01016 b);
     * @param uri
     */
    void cancelDownload(final Uri uri);

    /**
     * M: Save the AndroidHttpClient at plug-in side.
     * OP09Feature: MMS-01016
     * @param url contentLocation
     * @param uri mms-uri
     */
    void addHttpClient(String url, Uri uri);

    /**
     * M: remove the androidhttpClient;
     * OP09Feature: MMS-01016
     * @param url
     */
    void removeHttpClient(String url);

    /**
     * M: Used for shieled the original toast and show the cancel download toast.
     * whether show the cancel toast or not.
     * OP09Feature: MMS-01016
     * @param isEnable
     */
    void setCancelToastEnabled(boolean isEnable);

    /**
     * M: Get the switcher for "cancelToast"
     * OP09Feature: MMS-01016
     * @return
     */
    boolean getCancelToastEnabled();

    /**
     * M: Update the database for the specific MMS downloading status.
     * OP09Feature: MMS-01016
     * @param uri
     * @param state
     */
    void markStateExt(Uri uri, int state);

    /**
     * M: Get the specific MMS downloading status.
     * OP09Feature: MMS-01016
     * @param uri
     * @return
     */
    int getStateExt(Uri uri);

    /**
     * M: Get the specific MMS downloading status.
     * OP09Feature: MMS-01016
     * @param url
     * @return
     */
    int getStateExt(String url);

    /**
     * M: Record if current transaction is waiting for data connectivity.
     * OP09Feature: MMS-01016
     * @param isWaiting
     */
    void setWaitingDataCnxn(boolean isWaiting);

    /**
     * M: get the status for the data connection.
     * OP09Feature: MMS-01016
     * @return
     */
    boolean getWaitingDataCnxn();

    /**
     * M: save the retryHandler to plugin side in order to cancel downloading mms through calling the framework's api;
     * OP09Feature: MMS-01016
     * @param retryHandler
     */
    void saveDefaultHttpRetryHandler(DefaultHttpRequestRetryHandler retryHandler);
}
