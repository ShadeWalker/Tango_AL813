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

import java.io.IOException;

import android.content.Context;
import android.content.ContextWrapper;

import android.util.Log;

/// M: ALPS00440523, set service to foreground @ {
import android.app.Service;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
/// @}
/// M: ALPS00545779, for FT, restart pending receiving mms @ {
import android.net.Uri;
import android.provider.Telephony.MmsSms;
/// @}

/// M: ALPS00452618, set special HTTP retry handler for CMCC FT @
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
/// @}
import org.apache.http.protocol.HttpContext;

public class DefaultMmsTransactionExt extends ContextWrapper implements IMmsTransactionExt {
    private static final String TAG = "Mms/MmsTransactionImpl";
    private Context mContext;

    public DefaultMmsTransactionExt(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * Set status code from server this time, if it is server fail need handled, will make
     * server fail count inscrease
     * @param value         Status code from server
     *
     */
    public void setMmsServerStatusCode(int code) {
        Log.d(TAG, "setMmsServerStatusCode");
    }

    /**
     * Update connection if we meet same server error many times.
     *
     * @return              If it really update connection returns true, otherwise false.
     */
    public boolean updateConnection() {
        Log.d(TAG, "updateConnection");
        return false;
    }

    /// M: ALPS00452618, set special HTTP retry handler for CMCC FT @
    /**
     * Get HTTP request retry handler
     *
     * @return              Return DefaultHttpRequestRetryHandler instance
     */
    public DefaultHttpRequestRetryHandler getHttpRequestRetryHandler() {
        Log.d(TAG, "getHttpRequestRetryHandler");
        return new MmsHttpRequestRetryHandler(mContext, 1, true);
    }
    /// @}

    /// M: ALPS00440523, set service to foreground @
    /**
     * Set service to foreground
     *
     * @param service         Service that need to be foreground
     */
    public void startServiceForeground(Service service) {
        Log.d(TAG, "startServiceForeground");
    }

    /**
     * Set service to foreground
     *
     * @param service         Service that need stop to be foreground
     */
    public void stopServiceForeground(Service service) {
        Log.d(TAG, "stopServiceForeground");
    }

    /// M: ALPS00440523, set property @ {
    public void setSoSendTimeoutProperty() {
        Log.d(TAG, "setSoSendTimeoutProperty");
        System.setProperty("SO_SND_TIMEOUT", "1");
    }
    /// @}

    /// M: ALPS00545779, for FT, restart pending receiving mms @ {
    /* On default,  only check failureType. If it is transient failed message then need restart*/
    public boolean isPendingMmsNeedRestart(Uri pduUri, int failureType) {
        return isTransientFailure(failureType);
    }

    private static boolean isTransientFailure(int type) {
        return (type < MmsSms.ERR_TYPE_GENERIC_PERMANENT) && (type > MmsSms.NO_ERROR);
    }
    /// @}

    /// M: This class is for function DefaultHttpRequestRetryHandler
    public class MmsHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {

        private static final String TAG = "MmsHttpRequestRetryHandler";
        private Context mHandlerContext = null;

        public MmsHttpRequestRetryHandler(Context context, int retryCount, boolean requestSentRetryEnabled) {
            super(retryCount, requestSentRetryEnabled);
            mHandlerContext = context;
        }

        public boolean retryRequest(
                final IOException exception,
                int executionCount,
                final HttpContext context) {

            Log.d(TAG, "retryRequest");

            if (exception == null) {
                throw new IllegalArgumentException("Exception parameter may not be null");
            }
            if (context == null) {
                throw new IllegalArgumentException("HTTP context may not be null");
            }

            if (!isPdpConnected()) {
                Log.d(TAG, "pdp disconnected");
                return false;
            }

            return super.retryRequest(exception, executionCount, context);
        }

        private boolean isPdpConnected() {
            Log.d(TAG, "isPdpConnected");

            ConnectivityManager connMgr = (ConnectivityManager) mHandlerContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connMgr != null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d(TAG, "InterruptedException for sleep 1s");
                }

                NetworkInfo ni = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
                if (ni != null && ni.isConnected()) {
                    Log.d(TAG, "connected");
                    return true;
                } else {
                    if (ni == null) {
                        Log.d(TAG, "getNetworkInfo fail");
                    } else {
                        Log.d(TAG, "ni is not connected");
                    }
                    return false;
                }
            } else {
                Log.d(TAG, "get ConnectivityManager failed!");
                return false;
            }
        }
    }
}

