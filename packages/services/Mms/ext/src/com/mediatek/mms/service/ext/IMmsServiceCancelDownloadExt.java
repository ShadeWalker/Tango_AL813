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

package com.mediatek.mms.service.ext;

import org.apache.http.client.HttpClient;

/**
 * M: Add for op09.
 */
public interface IMmsServiceCancelDownloadExt {

    /**
     * M: Save the AndroidHttpClient at plug-in side.
     * OP09Feature: MMS-01016.
     *
     * @param url an url for http client of mms.
     * @param client an httplicent of mms.
     */
    void addHttpClient(String url, HttpClient client);

    /**
     * M: Cancel downloading mms.
     * OP09Feature: MMS-01016 b).
     *
     * @param uri the url of the mms.
     */
    void cancelDownload(String uri);

    /**
     * M: remove the http client.
     * OP09Feature: MMS-01016.
     * @param uri the uri of mms.
     */
    void removeHttpClient(String uri);

    /**
     * M: Switcher for cancel download mms.
     * OP09Feature:MMS-01016.
     * @return true: enable; false: enable.
     */
    boolean isCancelDownloadEnable();

    /**
     * M: save the uri for acquire network.
     * OP09Feature: MMS-01016
     * @param uri the Mms Uri.
     */
    void addAcquireNetworkUri(String uri);

    /**
     * M: remove the uri from cache.
     * OP09Feature: MMS-01016.
     * @param uri the Mms Uri.
     */
    void removeAcquireNetWorkUri(String uri);

    /**
     * M: check the uri whether be save in the cancel cache.
     * OP09Feature: MMS-01016.
     * @param uri the Mms Uri.
     * @return ture: can be canceled; false: can not be canceled.
     */
    boolean canBeCanceled(String uri);

    /**
     * M: check the concurrent uri whether can be canceled or not.
     * OP09Feature: MMS-01016.
     * @param uri the mms uri.
     * @return ture: need be canceled; false: need not be canceled.
     */
    boolean needBeCanceled(String uri);

    /**
     * M: remove the uri from the canceled uri cache.
     * OP09Feature: MMS-01016.
     * @param uri
     *            the Mms Uri.
     */
    void removeCanceledUri(String uri);

    /**
     * M: cache the location uri.
     * OP09Feature: MMS-01016.
     * @param key use the class hashCode as key.
     * @param locationUri the mms locationUri.
     */
    void cacheLocationUri(Integer key, String locationUri);

    /**
     * M: Get the location Uri from cache.
     * OP09Feature: MMS-01016.
     * @param key use the class hashCode as key.
     * @return the location uri.
     */
    String getCachedLocationUri(Integer key);

    /**
     * M: remove the location Uri from cache.
     * @param key the key.
     */
    void removeCachedLocationUri(Integer key);

}
